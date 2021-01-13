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
import android.app.admin.DeviceAdminReceiver
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
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.os.UserManager
import android.printservice.PrintService
import android.provider.DeviceConfig
import android.provider.Settings
import android.service.autofill.AutofillService
import android.service.dreams.DreamService
import android.service.notification.NotificationListenerService
import android.service.voice.VoiceInteractionService
import android.service.wallpaper.WallpaperService
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS
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
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.AppOpLiveData
import com.android.permissioncontroller.permission.data.BroadcastReceiverLiveData
import com.android.permissioncontroller.permission.data.CarrierPrivilegedStatusLiveData
import com.android.permissioncontroller.permission.data.DataRepositoryForPackage
import com.android.permissioncontroller.permission.data.HasIntentAction
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.ServiceLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UnusedAutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.data.UsersLiveData
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
import java.util.Date
import java.util.Random
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

private const val LOG_TAG = "AutoRevokePermissions"
private const val DEBUG_OVERRIDE_THRESHOLDS = false
// TODO eugenesusla: temporarily enabled for extra logs during dogfooding
const val DEBUG_AUTO_REVOKE = true || DEBUG_OVERRIDE_THRESHOLDS

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
        AutoRevokeDumpLiveData(context).getInitializedValue()
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

        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG, "scheduleAutoRevokePermissions " +
                "with frequency ${getCheckFrequencyMs(context)}ms " +
                "and threshold ${getUnusedThresholdMs(context)}ms")
        }

        val userManager = context.getSystemService(UserManager::class.java)!!
        // If this user is a profile, then its auto revoke will be handled by the primary user
        if (userManager.isProfile) {
            if (DEBUG_AUTO_REVOKE) {
                DumpableLog.i(LOG_TAG, "user ${myUserHandle().identifier} is a profile. Not " +
                    "running Auto Revoke.")
            }
            return
        } else if (DEBUG_AUTO_REVOKE) {
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

    // TODO ntmyren: remove once b/154796729 is fixed
    Log.i(LOG_TAG, "getting UserPackageInfoLiveData for all users " +
        "in AutoRevokePermissions")
    val allPackagesByUser = AllPackageInfosLiveData.getInitializedValue()
    val allPackagesByUserByUid = allPackagesByUser.mapValues { (_, pkgs) ->
        pkgs.groupBy { pkg -> pkg.uid }
    }
    val unusedApps = allPackagesByUser.toMutableMap()

    val userStats = UsageStatsLiveData[getUnusedThresholdMs(context),
        if (DEBUG_OVERRIDE_THRESHOLDS) INTERVAL_DAILY else INTERVAL_MONTHLY].getInitializedValue()
    if (DEBUG_AUTO_REVOKE) {
        for ((user, stats) in userStats) {
            DumpableLog.i(LOG_TAG, "Usage stats for user ${user.identifier}: " +
                    stats.map { stat ->
                        stat.packageName to Date(stat.lastTimeVisible)
                    }.toMap())
        }
    }
    for (user in unusedApps.keys.toList()) {
        if (user !in userStats.keys) {
            if (DEBUG_AUTO_REVOKE) {
                DumpableLog.i(LOG_TAG, "Ignoring user ${user.identifier}")
            }
            unusedApps.remove(user)
        }
    }

    for ((user, stats) in userStats) {
        var unusedUserApps = unusedApps[user] ?: continue

        unusedUserApps = unusedUserApps.filter { packageInfo ->
            val pkgName = packageInfo.packageName

            val uidPackages = allPackagesByUserByUid[user]!![packageInfo.uid]
                    ?.map { info -> info.packageName } ?: emptyList()
            if (pkgName !in uidPackages) {
                Log.wtf(LOG_TAG, "Package $pkgName not among packages for " +
                        "its uid ${packageInfo.uid}: $uidPackages")
            }
            var lastTimeVisible: Long = stats.lastTimeVisible(uidPackages)

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
        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG, "Unused apps for user ${user.identifier}: " +
                "${unusedUserApps.map { it.packageName }}")
        }
    }

    val revokedApps = mutableListOf<Pair<String, UserHandle>>()
    val userManager = context.getSystemService(UserManager::class.java)
    for ((user, userApps) in unusedApps) {
        if (userManager == null || !userManager.isUserUnlocked(user)) {
            DumpableLog.w(LOG_TAG, "Skipping $user - locked direct boot state")
            continue
        }
        userApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
            if (pkg.grantedPermissions.isEmpty()) {
                return@forEachInParallel
            }

            if (isPackageAutoRevokePermanentlyExempt(pkg, user)) {
                return@forEachInParallel
            }

            val packageName = pkg.packageName
            if (isPackageAutoRevokeExempt(context, pkg)) {
                return@forEachInParallel
            }

            val anyPermsRevoked = AtomicBoolean(false)
            val pkgPermGroups: Map<String, List<String>>? =
                PackagePermissionsLiveData[packageName, user]
                    .getInitializedValue()

            pkgPermGroups?.entries?.forEachInParallel(Main) { (groupName, _) ->
                if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                    return@forEachInParallel
                }

                val group: LightAppPermGroup =
                    LightAppPermGroupLiveData[packageName, groupName, user]
                        .getInitializedValue()
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

                    if (DEBUG_AUTO_REVOKE) {
                        DumpableLog.i(LOG_TAG, "revokeUnused $packageName - $revocablePermissions" +
                                " - lastVisible on " +
                                userStats[user]?.lastTimeVisible(packageName)?.let(::Date))
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
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG, "revoking $packageName - $revocablePermissions")
                            DumpableLog.i(LOG_TAG, "State pre revocation: ${group.allPermissions}")
                        }
                        anyPermsRevoked.compareAndSet(false, true)

                        val bgRevokedState = KotlinUtils.revokeBackgroundRuntimePermissions(
                                context.application, group,
                                userFixed = false, oneTime = false,
                                filterPermissions = revocablePermissions)
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG,
                                "Bg state post revocation: ${bgRevokedState.allPermissions}")
                        }
                        val fgRevokedState = KotlinUtils.revokeForegroundRuntimePermissions(
                            context.application, group,
                            userFixed = false, oneTime = false,
                            filterPermissions = revocablePermissions)
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG,
                                "Fg state post revocation: ${fgRevokedState.allPermissions}")
                        }

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
        if (DEBUG_AUTO_REVOKE) {
            synchronized(revokedApps) {
                DumpableLog.i(LOG_TAG,
                        "Done auto-revoke for user ${user.identifier} - revoked $revokedApps")
            }
        }
    }
    return revokedApps
}

private fun List<UsageStats>.lastTimeVisible(pkgNames: List<String>): Long {
    var result = 0L
    for (stat in this) {
        if (stat.packageName in pkgNames) {
            result = Math.max(result, stat.lastTimeVisible)
        }
    }
    return result
}

private fun List<UsageStats>.lastTimeVisible(pkgName: String): Long {
    return lastTimeVisible(listOf(pkgName))
}

/**
 * Checks if the given package is exempt from auto revoke in a way that's not user-overridable
 */
suspend fun isPackageAutoRevokePermanentlyExempt(
    pkg: LightPackageInfo,
    user: UserHandle
): Boolean {
    if (!ExemptServicesLiveData[user]
            .getInitializedValue()[pkg.packageName]
            .isNullOrEmpty()) {
        return true
    }
    if (Utils.isUserDisabledOrWorkProfile(user)) {
        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG,
                    "Exempted ${pkg.packageName} - $user is disabled or a work profile")
        }
        return true
    }
    val carrierPrivilegedStatus = CarrierPrivilegedStatusLiveData[pkg.packageName]
            .getInitializedValue()
    if (carrierPrivilegedStatus != CARRIER_PRIVILEGE_STATUS_HAS_ACCESS &&
            carrierPrivilegedStatus != CARRIER_PRIVILEGE_STATUS_NO_ACCESS) {
        DumpableLog.w(LOG_TAG, "Error carrier privileged status for ${pkg.packageName}: " +
                carrierPrivilegedStatus)
    }
    if (carrierPrivilegedStatus == CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} - carrier privileged")
        }
        return true
    }

    if (PermissionControllerApplication.get()
            .packageManager
            .checkPermission(
                    android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                    pkg.packageName) == PERMISSION_GRANTED) {
        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG, "Exempted ${pkg.packageName} " +
                    "- holder of READ_PRIVILEGED_PHONE_STATE")
        }
        return true
    }

    return false
}

/**
 * Checks if the given package is exempt from auto revoke in a way that's user-overridable
 */
suspend fun isPackageAutoRevokeExempt(
    context: Context,
    pkg: LightPackageInfo
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

        // Q- packages exempt by default, except for dogfooding
        return pkg.targetSdkVersion <= android.os.Build.VERSION_CODES.Q &&
                TeamfoodSettings.get(context)?.enabledForPreRApps != true
    }
    // Check whether user/installer exempt
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
        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG, "onStartJob")
        }

        if (SKIP_NEXT_RUN) {
            SKIP_NEXT_RUN = false
            if (DEBUG_AUTO_REVOKE) {
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

        val b = Notification.Builder(this, PERMISSION_REMINDER_CHANNEL_ID)
            .setContentTitle(getString(R.string.auto_revoke_permission_notification_title))
            .setContentText(getString(
                R.string.auto_revoke_permission_notification_content))
            .setStyle(Notification.BigTextStyle().bigText(getString(
                R.string.auto_revoke_permission_notification_content)))
            .setSmallIcon(R.drawable.ic_settings_24dp)
            .setColor(getColor(android.R.color.system_notification_accent_color))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .extend(Notification.TvExtender())
        Utils.getSettingsLabelForNotifications(applicationContext.packageManager)?.let {
            settingsLabel ->
            val extras = Bundle()
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, settingsLabel.toString())
            b.addExtras(extras)
        }

        notificationManager.notify(AutoRevokeService::class.java.simpleName,
            AUTO_REVOKE_NOTIFICATION_ID, b.build())
        // Preload the auto revoked packages
        UnusedAutoRevokedPackagesLiveData.getInitializedValue()
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

/**
 * Packages using exempt services for the current user (package-name -> list<service-interfaces>
 * implemented by the package)
 */
class ExemptServicesLiveData(val user: UserHandle)
    : SmartUpdateMediatorLiveData<Map<String, List<String>>>() {
    private val serviceLiveDatas: List<SmartUpdateMediatorLiveData<Set<String>>> = listOf(
            ServiceLiveData[InputMethod.SERVICE_INTERFACE,
                    Manifest.permission.BIND_INPUT_METHOD,
                    user],
            ServiceLiveData[
                    NotificationListenerService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                    user],
            ServiceLiveData[
                    AccessibilityService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                    user],
            ServiceLiveData[
                    WallpaperService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_WALLPAPER,
                    user],
            ServiceLiveData[
                    VoiceInteractionService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_VOICE_INTERACTION,
                    user],
            ServiceLiveData[
                    PrintService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_PRINT_SERVICE,
                    user],
            ServiceLiveData[
                    DreamService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_DREAM_SERVICE,
                    user],
            ServiceLiveData[
                    AutofillService.SERVICE_INTERFACE,
                    Manifest.permission.BIND_AUTOFILL_SERVICE,
                    user],
            ServiceLiveData[
                    DevicePolicyManager.ACTION_DEVICE_ADMIN_SERVICE,
                    Manifest.permission.BIND_DEVICE_ADMIN,
                    user],
            BroadcastReceiverLiveData[
                    DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED,
                    Manifest.permission.BIND_DEVICE_ADMIN,
                    user]
    )

    init {
        serviceLiveDatas.forEach { addSource(it) { update() } }
    }

    override fun onUpdate() {
        if (serviceLiveDatas.all { it.isInitialized }) {
            val pksToServices = mutableMapOf<String, MutableList<String>>()

            serviceLiveDatas.forEach { serviceLD ->
                serviceLD.value!!.forEach { packageName ->
                    pksToServices.getOrPut(packageName, { mutableListOf() })
                            .add((serviceLD as? HasIntentAction)?.intentAction ?: "???")
                }
            }

            value = pksToServices
        }
    }

    /**
     * Repository for ExemptServiceLiveData
     *
     * <p> Key value is user
     */
    companion object : DataRepositoryForPackage<UserHandle, ExemptServicesLiveData>() {
        override fun newValue(key: UserHandle): ExemptServicesLiveData {
            return ExemptServicesLiveData(key)
        }
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

                if (DEBUG_AUTO_REVOKE) {
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
                if (DEBUG_AUTO_REVOKE) {
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
        val implementedServices: List<String>,
        val groups: List<AutoRevokeDumpGroupData>
    ) {
        fun dump(): PackageProto {
            val dump = PackageProto.newBuilder()
                    .setUid(uid)
                    .setPackageName(packageName)
                    .setFirstInstallTime(firstInstallTime)

            lastTimeVisible?.let { dump.lastTimeVisible = lastTimeVisible }

            implementedServices.forEach { dump.addImplementedServices(it) }

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

    /** All users */
    private val users = UsersLiveData

    /** Exempt services for each user: user -> services */
    private var services: MutableMap<UserHandle, ExemptServicesLiveData>? = null

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
            update()
        }

        addSource(users) {
            services?.values?.forEach { removeSource(it) }
            services = null

            update()
        }

        addSource(usages) {
            update()
        }

        addSource(packages) {
            pkgPermGroupNames?.values?.forEach { removeSource(it) }
            pkgPermGroupNames = null
            pkgPermGroups.values.forEach { it?.values?.forEach { removeSource(it) } }

            update()
        }
    }

    override fun onUpdate() {
        // If a source is already ready, the call onUpdate when added. Suppress this
        if (isUpdating) {
            return
        }
        isUpdating = true

        // services/autoRevokeManifestExemptPackages step 1, users is loaded, nothing else
        if (users.isInitialized && services == null) {
            services = mutableMapOf()

            for (user in users.value!!) {
                val newServices = ExemptServicesLiveData[user]
                services!![user] = newServices

                addSource(newServices) {
                    update()
                }
            }
        }

        // pkgPermGroupNames step 1, packages is loaded, nothing else
        if (packages.isInitialized && pkgPermGroupNames == null) {
            pkgPermGroupNames = mutableMapOf()

            for ((user, userPkgs) in packages.value!!) {
                for (pkg in userPkgs) {
                    val newPermGroupNames = PackagePermissionsLiveData[pkg.packageName, user]
                    pkgPermGroupNames!![user to pkg.packageName] = newPermGroupNames

                    addSource(newPermGroupNames) {
                        pkgPermGroups[user to pkg.packageName]?.forEach { removeSource(it.value) }
                        pkgPermGroups.remove(user to pkg.packageName)

                        update()
                    }
                }
            }
        }

        // pkgPermGroupNames step 2, packages and pkgPermGroupNames are loaded, but pkgPermGroups
        // are not loaded yet
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

                            addSource(newPkgPermGroup) { update() }
                        }
                    }
                }
            }
        }

        // Final step, everything is loaded, generate data
        if (packages.isInitialized && usages.isInitialized && revokedPermGroupNames.isInitialized &&
                pkgPermGroupNames?.values?.all { it.isInitialized } == true &&
                pkgPermGroupNames?.size == pkgPermGroups.size &&
                pkgPermGroups.values.all { it?.values?.all { it.isInitialized } == true } &&
                services?.values?.all { it.isInitialized } == true) {
            val users = mutableListOf<AutoRevokeDumpUserData>()

            for ((user, userPkgs) in packages.value!!) {
                val pkgs = mutableListOf<AutoRevokeDumpPackageData>()

                for (pkg in userPkgs) {
                    val groups = mutableListOf<AutoRevokeDumpGroupData>()

                    for (groupName in pkgPermGroupNames!![user to pkg.packageName]!!.value!!.keys) {
                        if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                            continue
                        }

                        pkgPermGroups[user to pkg.packageName]?.let {
                            it[groupName]?.value?.apply {
                                groups.add(AutoRevokeDumpGroupData(groupName,
                                        isBackgroundFixed || isForegroundFixed,
                                        permissions.any { (_, p) -> p.isGrantedIncludingAppOp },
                                        isGrantedByDefault,
                                        isGrantedByRole,
                                        isUserSensitive,
                                    revokedPermGroupNames.value?.let {
                                        it[pkg.packageName to user]
                                            ?.contains(groupName)
                                    } == true
                                ))
                            }
                        }
                    }

                    pkgs.add(AutoRevokeDumpPackageData(pkg.uid, pkg.packageName,
                            pkg.firstInstallTime,
                            usages.value!![user]?.lastTimeVisible(pkg.packageName),
                            services!![user]?.value!![pkg.packageName] ?: emptyList(),
                            groups))
                }

                users.add(AutoRevokeDumpUserData(user, pkgs))
            }

            value = AutoRevokeDumpData(users)
        }

        isUpdating = false
    }
}
