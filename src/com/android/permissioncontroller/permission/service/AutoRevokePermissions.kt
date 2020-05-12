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
import android.accessibilityservice.AccessibilityService
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
import android.app.admin.DevicePolicyManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.content.pm.PackageManager.GET_META_DATA
import android.content.pm.PackageManager.GET_SERVICES
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.NetworkScoreManager
import android.os.Bundle
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.os.UserManager
import android.permission.PermissionManager
import android.printservice.PrintService
import android.provider.DeviceConfig
import android.provider.Settings
import android.service.attention.AttentionService
import android.service.autofill.AutofillService
import android.service.autofill.augmented.AugmentedAutofillService
import android.service.dreams.DreamService
import android.service.notification.NotificationListenerService
import android.service.textclassifier.TextClassifierService
import android.service.voice.VoiceInteractionService
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.inputmethod.InputMethod
import androidx.annotation.MainThread
import androidx.preference.PreferenceManager
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.Constants.ACTION_MANAGE_AUTO_REVOKE
import com.android.permissioncontroller.Constants.AUTO_REVOKE_NOTIFICATION_ID
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.Constants.PERMISSION_REMINDER_CHANNEL_ID
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.AppOpLiveData
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UnusedAutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.AutoRevokePermissionsDumpProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.PackageProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.PerUserProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.PermissionGroupProto
import com.android.permissioncontroller.permission.service.AutoRevokePermissionsProto.TeamFoodSettingsProto
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS
import com.android.permissioncontroller.permission.utils.application
import com.android.permissioncontroller.permission.utils.forEachInParallel
import com.android.permissioncontroller.permission.utils.updatePermissionFlags
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Random

private const val LOG_TAG = "AutoRevokePermissions"
private const val DEBUG_OVERRIDE_THRESHOLDS = false
// TODO eugenesusla: temporarily enabled for extra logs during dogfooding
private const val DEBUG = true || DEBUG_OVERRIDE_THRESHOLDS

private const val AUTO_REVOKE_ENABLED = true

private var SKIP_NEXT_RUN = false

private val EXEMPT_PERMISSIONS = listOf(
        android.Manifest.permission.ACTIVITY_RECOGNITION)

private val DEFAULT_UNUSED_THRESHOLD_MS =
        if (AUTO_REVOKE_ENABLED) DAYS.toMillis(90) else Long.MAX_VALUE
fun getUnusedThresholdMs(context: Context) = when {
    DEBUG_OVERRIDE_THRESHOLDS -> SECONDS.toMillis(1)
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

private val PREF_KEY_FIRST_BOOT_TIME = "first_boot_time"

fun isAutoRevokeEnabled(context: Context): Boolean {
    return getCheckFrequencyMs(context) > 0 &&
            getUnusedThresholdMs(context) > 0 &&
            getUnusedThresholdMs(context) != Long.MAX_VALUE
}

/**
 * @return dump of auto revoke service as a proto
 */
suspend fun dumpAutoRevokePermissions(context: Context): AutoRevokePermissionsDumpProto {
    val teamFoodSettings = GlobalScope.async(IPC) {
        TeamfoodSettings.get(context)?.dump()
                ?: TeamFoodSettingsProto.newBuilder().build()
    }

    val dumpData = GlobalScope.async(IPC) {
        AutoRevokeDumpLiveData(context).getInitializedValue(staleOk = true)
    }

    return AutoRevokePermissionsDumpProto.newBuilder()
            .setTeamfoodSettings(teamFoodSettings.await())
            .addAllUsers(dumpData.await().dumpUsers())
            .build()
}

/**
 * Receiver of the onBoot event.
 */
class AutoRevokeOnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Init firstBootTime
        val firstBootTime = context.firstBootTime

        if (DEBUG) {
            DumpableLog.i(LOG_TAG, "scheduleAutoRevokePermissions " +
                "with frequency ${getCheckFrequencyMs(context)}ms " +
                "and threshold ${getUnusedThresholdMs(context)}ms")
        }

        val userManager = context.getSystemService(UserManager::class.java)!!
        // If this user is a profile, then its auto revoke will be handled by the primary user
        if (userManager.isProfile) {
            if (DEBUG) {
                DumpableLog.i(LOG_TAG, "user ${myUserHandle().identifier} is a profile. Not " +
                    "running Auto Revoke.")
            }
            return
        } else if (DEBUG) {
            DumpableLog.i(LOG_TAG, "user ${myUserHandle().identifier} is a profile owner. " +
                "Running Auto Revoke.")
        }

        SKIP_NEXT_RUN = true

        val jobInfo = JobInfo.Builder(
            Constants.AUTO_REVOKE_JOB_ID,
            ComponentName(context, AutoRevokeService::class.java))
            .setPeriodic(getCheckFrequencyMs(context))
            .build()
        val status = context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
        if (status != JobScheduler.RESULT_SUCCESS) {
            DumpableLog.e(LOG_TAG,
                "Could not schedule ${AutoRevokeService::class.java.simpleName}: $status")
        }
    }
}

@MainThread
private suspend fun revokePermissionsOnUnusedApps(
    context: Context,
    sessionId: Long = INVALID_SESSION_ID
):
    List<Pair<String, UserHandle>> {
    if (!isAutoRevokeEnabled(context)) {
        return emptyList()
    }

    val now = System.currentTimeMillis()
    val firstBootTime = context.firstBootTime

    val unusedApps = AllPackageInfosLiveData.getInitializedValue(staleOk = true).toMutableMap()

    val userStats = UsageStatsLiveData[getUnusedThresholdMs(context),
        if (DEBUG_OVERRIDE_THRESHOLDS) INTERVAL_DAILY else INTERVAL_MONTHLY].getInitializedValue()
    for (user in unusedApps.keys) {
        if (user !in userStats.keys) {
            unusedApps.remove(user)
        }
    }

    for ((user, stats) in userStats) {
        var unusedUserApps = unusedApps[user] ?: continue

        unusedUserApps = unusedUserApps.filter { packageInfo ->
            val pkgName = packageInfo.packageName

            var lastTimeVisible: Long = stats.lastTimeVisible(pkgName)

            // Limit by install time
            lastTimeVisible = Math.max(lastTimeVisible, packageInfo.firstInstallTime)

            // Limit by first boot time
            lastTimeVisible = Math.max(lastTimeVisible, firstBootTime)

            // Handle cross-profile apps
            if (context.isPackageCrossProfile(pkgName)) {
                for ((otherUser, otherStats) in userStats) {
                    if (otherUser == user) {
                        continue
                    }
                    lastTimeVisible = Math.max(lastTimeVisible, otherStats.lastTimeVisible(pkgName))
                }
            }

            // Threshold check - whether app is unused
            now - lastTimeVisible > getUnusedThresholdMs(context)
        }

        unusedApps[user] = unusedUserApps
        if (DEBUG) {
            DumpableLog.i(LOG_TAG, "Unused apps for user ${user.identifier}: " +
                "${unusedUserApps.map { it.packageName }}")
        }
    }

    val manifestExemptPackages: Set<String> = withContext(IPC) {
        context.getSystemService<PermissionManager>()
            .getAutoRevokeExemptionGrantedPackages()
    }

    // Exempt important system-bound services
    val keyboardPackages = packagesWithService(context,
            InputMethod.SERVICE_INTERFACE,
            Manifest.permission.BIND_INPUT_METHOD)
    val notificationListenerPackages = packagesWithService(context,
            NotificationListenerService.SERVICE_INTERFACE,
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
    val accessibilityPackages = packagesWithService(context,
            AccessibilityService.SERVICE_INTERFACE,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
    val liveWallpaperPackages = packagesWithService(context,
            WallpaperService.SERVICE_INTERFACE,
            Manifest.permission.BIND_WALLPAPER)
    val voiceInteractionServicePackages = packagesWithService(context,
            VoiceInteractionService.SERVICE_INTERFACE,
            Manifest.permission.BIND_VOICE_INTERACTION)
    val attentionServicePackages = packagesWithService(context,
            AttentionService.SERVICE_INTERFACE,
            Manifest.permission.BIND_ATTENTION_SERVICE)
    val textClassifierPackages = packagesWithService(context,
            TextClassifierService.SERVICE_INTERFACE,
            Manifest.permission.BIND_TEXTCLASSIFIER_SERVICE)
    val printServicePackages = packagesWithService(context,
            PrintService.SERVICE_INTERFACE,
            Manifest.permission.BIND_PRINT_SERVICE)
    val dreamServicePackages = packagesWithService(context,
            DreamService.SERVICE_INTERFACE,
            Manifest.permission.BIND_DREAM_SERVICE)
    val networkScorerPackages = packagesWithService(context,
            NetworkScoreManager.ACTION_RECOMMEND_NETWORKS,
            Manifest.permission.BIND_NETWORK_RECOMMENDATION_SERVICE)
    val autofillPackages = packagesWithService(context,
            AutofillService.SERVICE_INTERFACE,
            Manifest.permission.BIND_AUTOFILL_SERVICE)
    val augmentedAutofillPackages = packagesWithService(context,
            AugmentedAutofillService.SERVICE_INTERFACE,
            Manifest.permission.BIND_AUGMENTED_AUTOFILL_SERVICE)
    val deviceAdminPackages = packagesWithService(context,
            DevicePolicyManager.ACTION_DEVICE_ADMIN_SERVICE,
            Manifest.permission.BIND_DEVICE_ADMIN)

    val exemptServicePackages = mutableSetOf<String>().apply {
        addAll(keyboardPackages)
        addAll(notificationListenerPackages)
        addAll(accessibilityPackages)
        addAll(liveWallpaperPackages)
        addAll(voiceInteractionServicePackages)
        addAll(attentionServicePackages)
        addAll(textClassifierPackages)
        addAll(printServicePackages)
        addAll(dreamServicePackages)
        addAll(networkScorerPackages)
        addAll(autofillPackages)
        addAll(augmentedAutofillPackages)
        addAll(deviceAdminPackages)
    }

    val revokedApps = mutableListOf<Pair<String, UserHandle>>()
    for ((user, userApps) in unusedApps) {
        userApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
            if (pkg.grantedPermissions.isEmpty()) {
                return@forEachInParallel
            }

            if (pkg.packageName in exemptServicePackages) {
                return@forEachInParallel
            }

            val packageName = pkg.packageName
            if (isPackageAutoRevokeExempt(context, pkg, manifestExemptPackages)) {
                return@forEachInParallel
            }

            val anyPermsRevoked = AtomicBoolean(false)
            val pkgPermGroups: Map<String, List<String>>? =
                PackagePermissionsLiveData[packageName, user]
                    .getInitializedValue(staleOk = true)

            pkgPermGroups?.entries?.forEachInParallel(Main) { (groupName, _) ->
                if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                    return@forEachInParallel
                }

                val group: LightAppPermGroup =
                    LightAppPermGroupLiveData[packageName, groupName, user]
                        .getInitializedValue(staleOk = true)
                        ?: return@forEachInParallel

                val fixed = group.isBackgroundFixed || group.isForegroundFixed
                val granted = group.permissions.any { (_, perm) ->
                    perm.isGrantedIncludingAppOp && perm.name !in EXEMPT_PERMISSIONS
                }
                if (!fixed &&
                    granted &&
                    !group.isGrantedByDefault &&
                    !group.isGrantedByRole &&
                    group.isUserSensitive) {

                    val revocablePermissions = group.permissions.keys.toList()

                    if (revocablePermissions.isEmpty()) {
                        return@forEachInParallel
                    }

                    if (DEBUG) {
                        DumpableLog.i(LOG_TAG, "revokeUnused $packageName - $revocablePermissions")
                    }

                    val uid = group.packageInfo.uid
                    for (permName in revocablePermissions) {
                        PermissionControllerStatsLog.write(
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
                            sessionId, uid, packageName, permName, false, SERVER_LOG_ID)
                    }

                    val packageImportance = context
                        .getSystemService(ActivityManager::class.java)!!
                        .getPackageImportance(packageName)
                    if (packageImportance > IMPORTANCE_TOP_SLEEPING) {
                        if (DEBUG) {
                            DumpableLog.i(LOG_TAG, "revoking $packageName - $revocablePermissions")
                        }
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
                                permission, packageName, user,
                                FLAG_PERMISSION_AUTO_REVOKED to true,
                                FLAG_PERMISSION_USER_SET to false)
                        }
                    } else {
                        DumpableLog.i(LOG_TAG,
                            "Skipping auto-revoke - $packageName running with importance " +
                                "$packageImportance")
                    }
                }
            }

            if (anyPermsRevoked.get()) {
                synchronized(revokedApps) {
                    revokedApps.add(pkg.packageName to user)
                }
            }
        }
    }
    return revokedApps
}

private fun packagesWithService(
    context: Context,
    serviceInterface: String,
    permission: String
): List<String> {
    val packageNames = context.packageManager
            .queryIntentServices(
                    Intent(serviceInterface),
                    GET_SERVICES or GET_META_DATA)
            .mapNotNull { resolveInfo ->
                if (resolveInfo?.serviceInfo?.permission != permission) {
                    return@mapNotNull null
                }
                resolveInfo?.serviceInfo?.packageName
            }
    if (DEBUG) {
        DumpableLog.i(LOG_TAG,
                "Detected ${serviceInterface.substringAfterLast(".")}s: $packageNames")
    }
    return packageNames
}

private fun List<UsageStats>.lastTimeVisible(pkgName: String) =
        find { it.packageName == pkgName }?.lastTimeVisible ?: 0L

suspend fun isPackageAutoRevokeExempt(
    context: Context,
    pkg: LightPackageInfo
) = isPackageAutoRevokeExempt(context, pkg, withContext(IPC) {
    context.getSystemService<PermissionManager>()
            .getAutoRevokeExemptionGrantedPackages()
})

private suspend fun isPackageAutoRevokeExempt(
    context: Context,
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

        if (DEBUG_OVERRIDE_THRESHOLDS) {
            // Suppress exemptions to allow debugging
            return false
        }

        if (pkg.targetSdkVersion <= android.os.Build.VERSION_CODES.Q &&
                TeamfoodSettings.get(context)?.enabledForPreRApps != true) {
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

val Context.sharedPreferences: SharedPreferences get() {
    return PreferenceManager.getDefaultSharedPreferences(this)
}

private val Context.firstBootTime: Long get() {
    var time = sharedPreferences.getLong(PREF_KEY_FIRST_BOOT_TIME, -1L)
    if (time > 0) {
        return time
    }
    // This is the first boot
    time = System.currentTimeMillis()
    sharedPreferences.edit().putLong(PREF_KEY_FIRST_BOOT_TIME, time).apply()
    return time
}

/**
 * A job to check for apps unused in the last [getUnusedThresholdMs]ms every
 * [getCheckFrequencyMs]ms and [revokePermissionsOnUnusedApps] for them
 */
class AutoRevokeService : JobService() {
    var job: Job? = null
    var jobStartTime: Long = -1L

    override fun onStartJob(params: JobParameters?): Boolean {
        if (DEBUG) {
            DumpableLog.i(LOG_TAG, "onStartJob")
        }

        if (SKIP_NEXT_RUN) {
            SKIP_NEXT_RUN = false
            if (DEBUG) {
                Log.i(LOG_TAG, "Skipping auto revoke first run when scheduled by system")
            }
            jobFinished(params, false)
            return true
        }

        jobStartTime = System.currentTimeMillis()
        job = GlobalScope.launch(Main) {
            try {
                var sessionId = INVALID_SESSION_ID
                while (sessionId == INVALID_SESSION_ID) {
                    sessionId = Random().nextLong()
                }

                val revokedApps = revokePermissionsOnUnusedApps(this@AutoRevokeService, sessionId)
                if (revokedApps.isNotEmpty()) {
                    showAutoRevokeNotification(sessionId)
                }
            } catch (e: Exception) {
                DumpableLog.e(LOG_TAG, "Failed to auto-revoke permissions", e)
            }
            jobFinished(params, false)
        }
        return true
    }

    private suspend fun showAutoRevokeNotification(sessionId: Long) {
        val notificationManager = getSystemService(NotificationManager::class.java)!!

        val permissionReminderChannel = NotificationChannel(
            PERMISSION_REMINDER_CHANNEL_ID, getString(R.string.permission_reminders),
            IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(permissionReminderChannel)

        val clickIntent = Intent(this, ManagePermissionsActivity::class.java).apply {
            action = ACTION_MANAGE_AUTO_REVOKE
            putExtra(EXTRA_SESSION_ID, sessionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, clickIntent,
            FLAG_ONE_SHOT or FLAG_UPDATE_CURRENT)

        val numRevoked = UnusedAutoRevokedPackagesLiveData.getInitializedValue().size
        val titleString = if (numRevoked == 1) {
            getString(R.string.auto_revoke_permission_reminder_notification_title_one)
        } else {
            getString(R.string.auto_revoke_permission_reminder_notification_title_many, numRevoked)
        }
        val b = Notification.Builder(this, PERMISSION_REMINDER_CHANNEL_ID)
            .setContentTitle(titleString)
            .setContentText(getString(
                R.string.auto_revoke_permission_reminder_notification_content))
            .setStyle(Notification.BigTextStyle().bigText(getString(
                R.string.auto_revoke_permission_reminder_notification_content)))
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(getColor(android.R.color.system_notification_accent_color))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        Utils.getSettingsLabelForNotifications(applicationContext.packageManager)?.let {
            settingsLabel ->
            val extras = Bundle()
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, settingsLabel.toString())
            b.addExtras(extras)
        }

        notificationManager.notify(AutoRevokeService::class.java.simpleName,
            AUTO_REVOKE_NOTIFICATION_ID, b.build())
    }

    companion object {
        const val SHOW_AUTO_REVOKE = "showAutoRevoke"
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        DumpableLog.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
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
                    DumpableLog.i(LOG_TAG, "Parsing teamfood setting value: $str")
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

    /**
     * @return team food settings for dumping as as a proto
     */
    suspend fun dump(): TeamFoodSettingsProto {
        return TeamFoodSettingsProto.newBuilder()
                .setEnabledForPreRApps(enabledForPreRApps)
                .setUnusedThresholdMillis(unusedThresholdMs)
                .setCheckFrequencyMillis(checkFrequencyMs)
                .build()
    }
}

/** Data interesting to auto-revoke */
private class AutoRevokeDumpLiveData(context: Context) :
        SmartUpdateMediatorLiveData<AutoRevokeDumpLiveData.AutoRevokeDumpData>() {
    /** All data */
    data class AutoRevokeDumpData(
        val users: List<AutoRevokeDumpUserData>
    ) {
        fun dumpUsers(): List<PerUserProto> {
            return users.map { it.dump() }
        }
    }

    /** Per user data */
    data class AutoRevokeDumpUserData(
        val user: UserHandle,
        val pkgs: List<AutoRevokeDumpPackageData>
    ) {
        fun dump(): PerUserProto {
            val dump = PerUserProto.newBuilder()
                    .setUserId(user.identifier)

            pkgs.forEach { dump.addPackages(it.dump()) }

            return dump.build()
        }
    }

    /** Per package data */
    data class AutoRevokeDumpPackageData(
        val uid: Int,
        val packageName: String,
        val firstInstallTime: Long,
        val lastTimeVisible: Long?,
        val groups: List<AutoRevokeDumpGroupData>
    ) {
        fun dump(): PackageProto {
            val dump = PackageProto.newBuilder()
                    .setUid(uid)
                    .setPackageName(packageName)
                    .setFirstInstallTime(firstInstallTime)

            lastTimeVisible?.let { dump.lastTimeVisible = lastTimeVisible }

            groups.forEach { dump.addGroups(it.dump()) }

            return dump.build()
        }
    }

    /** Per permission group data */
    data class AutoRevokeDumpGroupData(
        val groupName: String,
        val isFixed: Boolean,
        val isAnyGrantedIncludingAppOp: Boolean,
        val isGrantedByDefault: Boolean,
        val isGrantedByRole: Boolean,
        val isUserSensitive: Boolean,
        val isAutoRevoked: Boolean
    ) {
        fun dump(): PermissionGroupProto {
            return PermissionGroupProto.newBuilder()
                    .setGroupName(groupName)
                    .setIsFixed(isFixed)
                    .setIsAnyGrantedIncludingAppop(isAnyGrantedIncludingAppOp)
                    .setIsGrantedByDefault(isGrantedByDefault)
                    .setIsGrantedByRole(isGrantedByRole)
                    .setIsUserSensitive(isUserSensitive)
                    .setIsAutoRevoked(isAutoRevoked)
                    .build()
        }
    }

    /** Usage stats: user -> list<usages> */
    private val usages = UsageStatsLiveData[
        getUnusedThresholdMs(context),
        if (DEBUG_OVERRIDE_THRESHOLDS) INTERVAL_DAILY else INTERVAL_MONTHLY
    ]

    /** All package infos: user -> pkg **/
    private val packages = AllPackageInfosLiveData

    /** Group names of revoked permission groups: (user, pkg-name) -> set<group-name> **/
    private val revokedPermGroupNames = UnusedAutoRevokedPackagesLiveData

    /**
     * Group names for packages
     * map<user, pkg-name> -> list<perm-group-name>. {@code null} before step 1
     */
    private var pkgPermGroupNames:
            MutableMap<Pair<UserHandle, String>, PackagePermissionsLiveData>? = null

    /**
     * Group state for packages
     * map<(user, pkg-name) -> map<perm-group-name -> group>>, value {@code null} before step 2
     */
    private val pkgPermGroups =
            mutableMapOf<Pair<UserHandle, String>,
                    MutableMap<String, LightAppPermGroupLiveData>?>()

    /** If this live-data currently inside onUpdate */
    private var isUpdating = false

    init {
        addSource(revokedPermGroupNames) {
            updateIfActive()
        }

        addSource(usages) {
            updateIfActive()
        }

        addSource(packages) {
            pkgPermGroupNames?.values?.forEach { removeSource(it) }
            pkgPermGroupNames = null
            pkgPermGroups.values.forEach { it?.values?.forEach { removeSource(it) } }

            updateIfActive()
        }
    }

    override fun onUpdate() {
        // If a source is already ready, the call onUpdate when added. Suppress this
        if (isUpdating) {
            return
        }
        isUpdating = true

        // Step 1, packages is loaded, nothing else
        if (packages.isInitialized && pkgPermGroupNames == null) {
            pkgPermGroupNames = mutableMapOf()

            for ((user, userPkgs) in packages.value!!) {
                for (pkg in userPkgs) {
                    val newPermGroupNames = PackagePermissionsLiveData[pkg.packageName, user]
                    pkgPermGroupNames!![user to pkg.packageName] = newPermGroupNames

                    addSource(newPermGroupNames) {
                        pkgPermGroups[user to pkg.packageName]?.forEach { removeSource(it.value) }
                        pkgPermGroups.remove(user to pkg.packageName)

                        updateIfActive()
                    }
                }
            }
        }

        // Step 2, packages and pkgPermGroupNames are loaded, but pkgPermGroups are not loaded yet
        if (packages.isInitialized && pkgPermGroupNames != null) {
            for ((user, userPkgs) in packages.value!!) {
                for (pkg in userPkgs) {
                    if (pkgPermGroupNames!![user to pkg.packageName]?.isInitialized == true &&
                            pkgPermGroups[user to pkg.packageName] == null) {
                        pkgPermGroups[user to pkg.packageName] = mutableMapOf()

                        for (groupName in
                                pkgPermGroupNames!![user to pkg.packageName]!!.value!!.keys) {
                            if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                                continue
                            }

                            val newPkgPermGroup = LightAppPermGroupLiveData[pkg.packageName,
                                    groupName, user]

                            pkgPermGroups[user to pkg.packageName]!![groupName] = newPkgPermGroup

                            addSource(newPkgPermGroup) { updateIfActive() }
                        }
                    }
                }
            }
        }

        // Step 3, everything is loaded, generate data
        if (packages.isInitialized && usages.isInitialized && revokedPermGroupNames.isInitialized &&
                pkgPermGroupNames?.values?.all { it.isInitialized } == true &&
                pkgPermGroupNames?.size == pkgPermGroups.size &&
                pkgPermGroups.values.all { it?.values?.all { it.isInitialized } == true }) {
            val users = mutableListOf<AutoRevokeDumpUserData>()

            for ((user, userPkgs) in packages.value!!) {
                val pkgs = mutableListOf<AutoRevokeDumpPackageData>()

                for (pkg in userPkgs) {
                    val groups = mutableListOf<AutoRevokeDumpGroupData>()

                    for (groupName in pkgPermGroupNames!![user to pkg.packageName]!!.value!!.keys) {
                        if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                            continue
                        }

                        pkgPermGroups[user to pkg.packageName]!![groupName]!!.value!!.apply {
                            groups.add(AutoRevokeDumpGroupData(groupName,
                                    isBackgroundFixed || isForegroundFixed,
                                    permissions.any { (_, p) -> p.isGrantedIncludingAppOp },
                                    isGrantedByDefault,
                                    isGrantedByRole,
                                    isUserSensitive,
                                    revokedPermGroupNames.value!![pkg.packageName to user]
                                            ?.contains(groupName) ?: false
                            ))
                        }
                    }

                    pkgs.add(AutoRevokeDumpPackageData(pkg.uid, pkg.packageName,
                            pkg.firstInstallTime,
                            usages.value!![user]
                                    ?.find { it.packageName == pkg.packageName }?.lastTimeVisible,
                            groups))
                }

                users.add(AutoRevokeDumpUserData(user, pkgs))
            }

            value = AutoRevokeDumpData(users)
        }

        isUpdating = false
    }
}
