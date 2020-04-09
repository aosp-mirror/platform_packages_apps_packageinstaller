/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("AutoRevokePermissions")

package com.android.permissioncontroller.permission.service

import android.Manifest
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.os.UserManager
import android.permission.PermissionManager
import android.provider.DeviceConfig
import android.provider.Settings
import android.util.Log
import androidx.annotation.MainThread
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.Constants.ACTION_MANAGE_AUTO_REVOKE
import com.android.permissioncontroller.Constants.AUTO_REVOKE_NOTIFICATION_ID
import com.android.permissioncontroller.Constants.PERMISSION_REMINDER_CHANNEL_ID
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.AppOpLiveData
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.UserPackageInfosLiveData
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS
import com.android.permissioncontroller.permission.utils.application
import com.android.permissioncontroller.permission.utils.forEachInParallel
import com.android.permissioncontroller.permission.utils.updatePermissionFlags
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

private const val LOG_TAG = "AutoRevokePermissions"
private const val DEBUG = false

private val DEFAULT_UNUSED_THRESHOLD_MS = DAYS.toMillis(90)
private fun getUnusedThresholdMs(context: Context) = when {
    DEBUG -> SECONDS.toMillis(1)
    TeamfoodSettings.get(context) != null -> TeamfoodSettings.get(context)!!.unusedThresholdMs
    else -> DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
            PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS,
            DEFAULT_UNUSED_THRESHOLD_MS)
}

private val DEFAULT_CHECK_FREQUENCY_MS = DAYS.toMillis(15)
private fun getCheckFrequencyMs(context: Context) = when {
    TeamfoodSettings.get(context) != null -> TeamfoodSettings.get(context)!!.checkFrequencyMs
    else -> DeviceConfig.getLong(
            DeviceConfig.NAMESPACE_PERMISSIONS,
            PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS,
            DEFAULT_CHECK_FREQUENCY_MS)
}

private val SERVER_LOG_ID =
    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED

private fun isAutoRevokeEnabled(context: Context): Boolean {
    return getCheckFrequencyMs(context) > 0 && getUnusedThresholdMs(context) > 0
}

/**
 * Receiver of the onBoot event.
 */
class AutoRevokeOnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (DEBUG) {
            Log.i(LOG_TAG, "scheduleAutoRevokePermissions " +
                "with frequency ${getCheckFrequencyMs(context)}ms" +
                "and threshold ${getUnusedThresholdMs(context)}ms")
        }
        val jobInfo = JobInfo.Builder(
            Constants.AUTO_REVOKE_JOB_ID,
            ComponentName(context, AutoRevokeService::class.java))
            .setPeriodic(getCheckFrequencyMs(context))
            .build()
        val status = context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
        if (status != JobScheduler.RESULT_SUCCESS) {
            Log.e(LOG_TAG,
                "Could not schedule ${AutoRevokeService::class.java.simpleName}: $status")
        }
    }
}

@MainThread
private suspend fun revokePermissionsOnUnusedApps(context: Context):
    List<Pair<String, UserHandle>> {
    if (!isAutoRevokeEnabled(context)) {
        return emptyList()
    }

    val now = System.currentTimeMillis()

    val unusedApps: MutableList<LightPackageInfo> = UserPackageInfosLiveData[myUserHandle()]
        .getInitializedValue(staleOk = true)
        .toMutableList()

    // TODO eugenesusla: adapt UsageStats into a LiveData
    val stats = withContext(IPC) {
        context.getSystemService<UsageStatsManager>()
            .queryUsageStats(
                if (DEBUG) INTERVAL_DAILY else INTERVAL_MONTHLY,
                now - getUnusedThresholdMs(context),
                now)
    }
    val profileUsersStats: Deferred<List<List<UsageStats>>> =
        GlobalScope.async(IPC, start = CoroutineStart.LAZY) {
            context
                .getSystemService<UserManager>()
                .enabledProfiles
                .map { user ->
                    context.forUser(user)
                        .getSystemService<UsageStatsManager>()
                        .queryUsageStats(
                            if (DEBUG) INTERVAL_DAILY else INTERVAL_MONTHLY,
                            now - getUnusedThresholdMs(context),
                            now)
                }
        }

    for (stat in stats) {
        var lastTimeVisible: Long = stat.lastTimeVisible
        val pkg = stat.packageName

        // Limit by install time
        unusedApps.find {
            it.packageName == pkg
        }?.let {
            lastTimeVisible = Math.max(lastTimeVisible, it.firstInstallTime)
        }

        // Handle cross-profile apps
        if (context.isPackageCrossProfile(pkg)) {
            profileUsersStats
                .await()
                .fold(lastTimeVisible) { result, profileStats ->
                    val time: Long = profileStats
                        .find { it.packageName == pkg }
                        ?.lastTimeVisible
                        ?: result
                    Math.max(result, time)
                }
        }

        // Threshold check
        if (now - lastTimeVisible <= getUnusedThresholdMs(context)) {
            unusedApps.removeAll { it.packageName == pkg }
        }
    }

    if (DEBUG) {
        Log.i(LOG_TAG, "Unused apps: ${unusedApps.map { it.packageName }}")
    }

    val manifestExemptPackages: Set<String> = withContext(IPC) {
        context.getSystemService<PermissionManager>()
            .getAutoRevokeExemptionGrantedPackages()
    }

    val revokedApps = mutableListOf<Pair<String, UserHandle>>()
    unusedApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
        if (pkg.grantedPermissions.isEmpty()) {
            return@forEachInParallel
        }

        val packageName = pkg.packageName
        if (isPackageAutoRevokeExempt(pkg, manifestExemptPackages)) {
            return@forEachInParallel
        }

        val anyPermsRevoked = AtomicBoolean(false)
        val pkgPermGroups: Map<String, List<String>> =
            PackagePermissionsLiveData[packageName, myUserHandle()]
                .getInitializedValue(staleOk = true)

        pkgPermGroups.entries.forEachInParallel(Main) { (groupName, _) ->
            if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                return@forEachInParallel
            }

            val group: LightAppPermGroup =
                LightAppPermGroupLiveData[packageName, groupName, myUserHandle()]
                    .getInitializedValue(staleOk = true)
                    ?: return@forEachInParallel

            val fixed = group.isBackgroundFixed || group.isForegroundFixed
            if (!fixed &&
                group.permissions.any { (_, perm) -> perm.isGrantedIncludingAppOp } &&
                !group.isGrantedByDefault &&
                !group.isGrantedByRole &&
                group.isUserSensitive) {

                val revocablePermissions = group.permissions.keys.toList()

                if (revocablePermissions.isEmpty()) {
                    return@forEachInParallel
                }

                if (DEBUG) {
                    Log.i(LOG_TAG, "revokeUnused $packageName - $revocablePermissions")
                }

                val uid = group.packageInfo.uid
                for (permName in revocablePermissions) {
                    PermissionControllerStatsLog.write(
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
                        Random.nextLong(), uid, packageName, permName, false, SERVER_LOG_ID)
                }

                val packageImportance = context
                    .getSystemService(ActivityManager::class.java)!!
                    .getPackageImportance(packageName)
                if (packageImportance > IMPORTANCE_TOP_SLEEPING) {
                    anyPermsRevoked.compareAndSet(false, true)

                    KotlinUtils.revokeBackgroundRuntimePermissions(
                        context.application, group,
                        userFixed = false, oneTime = false,
                        filterPermissions = revocablePermissions)
                    KotlinUtils.revokeForegroundRuntimePermissions(
                        context.application, group,
                        userFixed = false, oneTime = false,
                        filterPermissions = revocablePermissions)

                    for (permission in revocablePermissions) {
                        context.packageManager.updatePermissionFlags(
                            permission, packageName, myUserHandle(),
                            FLAG_PERMISSION_AUTO_REVOKED to true,
                            FLAG_PERMISSION_USER_SET to false)
                    }
                } else {
                    Log.i(LOG_TAG,
                        "Skipping auto-revoke - app running with importance $packageImportance")
                }
            }
        }

        if (anyPermsRevoked.get()) {
            synchronized(revokedApps) {
                revokedApps.add(pkg.packageName to UserHandle.getUserHandleForUid(pkg.uid))
            }
        }
    }
    return revokedApps
}

suspend fun isPackageAutoRevokeExempt(
    context: Context,
    pkg: LightPackageInfo
) = isPackageAutoRevokeExempt(pkg, withContext(IPC) {
    context.getSystemService<PermissionManager>()
            .getAutoRevokeExemptionGrantedPackages()
})

private suspend fun isPackageAutoRevokeExempt(
    pkg: LightPackageInfo,
    manifestExemptPackages: Set<String>
): Boolean {
    val packageName = pkg.packageName
    val packageUid = pkg.uid

    val whitelistAppOpMode =
        AppOpLiveData[packageName,
            AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, packageUid]
            .getInitializedValue()
    if (whitelistAppOpMode == MODE_DEFAULT) {
        // Initial state - whitelist not explicitly overridden by either user or installer

        if (DEBUG) {
            // Suppress exemptions to allow debugging
            return false
        }

        if (pkg.targetSdkVersion <= android.os.Build.VERSION_CODES.Q) {
            // Q- packages exempt by default
            return true
        } else {
            // R+ packages only exempt with manifest attribute
            return packageName in manifestExemptPackages
        }
    }
    return whitelistAppOpMode != MODE_ALLOWED
}

private fun Context.isPackageCrossProfile(pkg: String): Boolean {
    return packageManager.checkPermission(
        Manifest.permission.INTERACT_ACROSS_PROFILES, pkg) == PERMISSION_GRANTED ||
        packageManager.checkPermission(
            Manifest.permission.INTERACT_ACROSS_USERS, pkg) == PERMISSION_GRANTED ||
        packageManager.checkPermission(
            Manifest.permission.INTERACT_ACROSS_USERS_FULL, pkg) == PERMISSION_GRANTED
}

private fun Context.forUser(user: UserHandle): Context {
    return Utils.getUserContext(application, user)
}

private fun Context.forParentUser(): Context {
    return Utils.getParentUserContext(this)
}

private inline fun <reified T> Context.getSystemService() = getSystemService(T::class.java)!!

/**
 * A job to check for apps unused in the last [getUnusedThresholdMs]ms every
 * [getCheckFrequencyMs]ms and [revokePermissionsOnUnusedApps] for them
 */
class AutoRevokeService : JobService() {
    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        if (DEBUG) {
            Log.i(LOG_TAG, "onStartJob")
        }

        jobStartTime = System.currentTimeMillis()
        job = GlobalScope.launch(Main) {
            try {
                val revokedApps = revokePermissionsOnUnusedApps(this@AutoRevokeService)
                if (revokedApps.isNotEmpty()) {
                    showAutoRevokeNotification()
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to auto-revoke permissions", e)
            }
            jobFinished(params, false)
        }
        return true
    }

    private fun showAutoRevokeNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)!!

        val permissionReminderChannel = NotificationChannel(
            PERMISSION_REMINDER_CHANNEL_ID, getString(R.string.permission_reminders),
            IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(permissionReminderChannel)

        val clickIntent = Intent(this, ManagePermissionsActivity::class.java).apply {
            action = ACTION_MANAGE_AUTO_REVOKE
            putExtra(SHOW_AUTO_REVOKE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, clickIntent,
            FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT)

        val b = Notification.Builder(this, PERMISSION_REMINDER_CHANNEL_ID)
            .setContentTitle(getString(R.string.auto_revoke_permission_reminder_notification_title))
            .setContentText(getString(
                R.string.auto_revoke_permission_reminder_notification_content))
            .setStyle(Notification.BigTextStyle().bigText(getString(
                R.string.auto_revoke_permission_reminder_notification_content)))
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(getColor(android.R.color.system_notification_accent_color))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(AutoRevokeService::class.java.simpleName,
            AUTO_REVOKE_NOTIFICATION_ID, b.build())

        GlobalScope.launch(Main) {
            // Initialize the "all auto revoked" liveData, to speed loading if the user clicks on
            // the notification
            AutoRevokedPackagesLiveData.getInitializedValue()
        }
    }

    companion object {
        const val SHOW_AUTO_REVOKE = "showAutoRevoke"
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
        job?.cancel()
        return true
    }
}

private data class TeamfoodSettings(
    val enabledForPreRApps: Boolean,
    val unusedThresholdMs: Long,
    val checkFrequencyMs: Long
) {
    companion object {
        private var cached: TeamfoodSettings? = null

        fun get(context: Context): TeamfoodSettings? {
            if (cached != null) return cached

            return Settings.Global.getString(context.contentResolver,
                "auto_revoke_parameters" /* Settings.Global.AUTO_REVOKE_PARAMETERS */)?.let { str ->

                if (DEBUG) {
                    Log.i(LOG_TAG, "Parsing teamfood setting value: $str")
                }
                str.split(",")
                    .mapNotNull {
                        val keyValue = it.split("=")
                        keyValue.getOrNull(0)?.let { key ->
                            key to keyValue.getOrNull(1)
                        }
                    }
                    .toMap()
                    .let { pairs ->
                        TeamfoodSettings(
                            enabledForPreRApps = pairs["enabledForPreRApps"] == "true",
                            unusedThresholdMs =
                                pairs["unusedThresholdMs"]?.toLongOrNull()
                                        ?: DEFAULT_UNUSED_THRESHOLD_MS,
                            checkFrequencyMs = pairs["checkFrequencyMs"]?.toLongOrNull()
                                    ?: DEFAULT_CHECK_FREQUENCY_MS)
                    }
            }.also {
                cached = it
                if (DEBUG) {
                    Log.i(LOG_TAG, "Parsed teamfood setting value: $it")
                }
            }
        }
    }
}
