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

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.app.usage.UsageStatsManager.INTERVAL_MONTHLY
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.os.Process.myUserHandle
import android.provider.DeviceConfig
import android.util.Log
import androidx.annotation.MainThread
import com.android.permissioncontroller.Constants
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED
import com.android.permissioncontroller.permission.data.*
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS
import com.android.permissioncontroller.permission.utils.Utils.PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS
import com.android.permissioncontroller.permission.utils.application
import com.android.permissioncontroller.permission.utils.forEachInParallel
import com.android.permissioncontroller.permission.utils.updatePermissionFlags
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.random.Random

private const val LOG_TAG = "AutoRevokePermissions"
private const val DEBUG = false

private val UNUSED_THRESHOLD_MS = if (DEBUG)
    SECONDS.toMillis(1)
else
    DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
        PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS,
        DAYS.toMillis(90))

private val CHECK_FREQUENCY_MS = DeviceConfig.getLong(
    DeviceConfig.NAMESPACE_PERMISSIONS,
    PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS,
    DAYS.toMillis(1))

private val SERVER_LOG_ID =
    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_UNUSED_APP_PERMISSION_REVOKED

private val isAutoRevokeEnabled: Boolean get() {
    return CHECK_FREQUENCY_MS > 0 && UNUSED_THRESHOLD_MS > 0
}

/**
 * Receiver of the onBoot event.
 */
class AutoRevokeOnBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (DEBUG) {
            Log.i(LOG_TAG, "scheduleAutoRevokePermissions " +
                "with frequency ${CHECK_FREQUENCY_MS}ms" +
                "and threshold ${UNUSED_THRESHOLD_MS}ms")
        }
        val jobInfo = JobInfo.Builder(
                Constants.AUTO_REVOKE_JOB_ID,
                ComponentName(context, AutoRevokeService::class.java))
            .setPeriodic(CHECK_FREQUENCY_MS)
            .build()
        val status = context.getSystemService(JobScheduler::class.java)!!.schedule(jobInfo)
        if (status != JobScheduler.RESULT_SUCCESS) {
            Log.e(LOG_TAG,
                "Could not schedule ${AutoRevokeService::class.java.simpleName}: $status")
        }
    }
}

@MainThread
private suspend fun revokePermissionsOnUnusedApps(context: Context) {
    if (!isAutoRevokeEnabled) {
        return
    }

    val now = System.currentTimeMillis()

    val unusedApps: MutableList<LightPackageInfo> = UserPackageInfosLiveData[myUserHandle()]
        .getInitializedValue(staleOk = true)
        .toMutableList()

    // TODO eugenesusla: adapt UsageStats into a LiveData
    val stats = withContext(IO) {
        context.getSystemService(UsageStatsManager::class.java)!!
            .queryUsageStats(
                if (DEBUG) INTERVAL_DAILY else INTERVAL_MONTHLY,
                now - UNUSED_THRESHOLD_MS,
                now)
    }

    for (stat in stats) {
        if (now - stat.lastTimeVisible <= UNUSED_THRESHOLD_MS) {
            unusedApps.removeAll { it.packageName == stat.packageName }
        }
    }

    if (DEBUG) {
        Log.i(LOG_TAG, "Unused apps: ${unusedApps.map { it.packageName }}")
    }

    unusedApps.forEachInParallel(Main) { pkg: LightPackageInfo ->
        if (pkg.grantedPermissions.isEmpty()) {
            return@forEachInParallel
        }

        val whitelistAppOpMode =
            AppOpLiveData[pkg.packageName, OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, pkg.uid]
                .getInitializedValue()
        if (whitelistAppOpMode == AppOpsManager.MODE_IGNORED) {
            // User exempt
            return@forEachInParallel
        }
        if (whitelistAppOpMode != AppOpsManager.MODE_ALLOWED) {
            // Override whitelist exemption when debugging to allow for testing
            if (!DEBUG) {
                // TODO eugenesusla: if manifest flag exempt -> return
            }
        }

        val pkgPermGroups: Map<String, List<String>> =
            PackagePermissionsLiveData[pkg.packageName, myUserHandle()]
                .getInitializedValue(staleOk = true)

        pkgPermGroups.entries.forEachInParallel(Main) { (groupName, groupPermNames) ->
            if (groupName == PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS) {
                return@forEachInParallel
            }

            val group: LightAppPermGroup =
                LightAppPermGroupLiveData[pkg.packageName, groupName, myUserHandle()]
                    .getInitializedValue(staleOk = true)
                    ?: return@forEachInParallel

            val fixed = group.isBackgroundFixed || group.isForegroundFixed
            if (!fixed &&
                group.permissions.any { (_, perm) -> perm.isGrantedIncludingAppOp } &&
                !group.isGrantedByDefault &&
                !group.isGrantedByRole) {

                val revocablePermissions = group.permissions.keys.toList()

                if (revocablePermissions.isEmpty()) {
                    return@forEachInParallel
                }

                if (DEBUG) {
                    Log.i(LOG_TAG, "revokeUnused ${pkg.packageName} - $revocablePermissions")
                }

                val uid = group.packageInfo.uid
                for (permName in revocablePermissions) {
                    PermissionControllerStatsLog.write(
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
                        Random.nextLong(), uid, pkg.packageName, permName, false, SERVER_LOG_ID)
                }

                val packageImportance = context
                    .getSystemService(ActivityManager::class.java)!!
                    .getPackageImportance(pkg.packageName)
                if (packageImportance > IMPORTANCE_TOP_SLEEPING) {
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
                            permission, pkg.packageName, myUserHandle(),
                            FLAG_PERMISSION_AUTO_REVOKED to true,
                            FLAG_PERMISSION_USER_SET to false)
                    }
                } else {
                    Log.i(LOG_TAG,
                        "Skipping auto-revoke - app running with importance $packageImportance")
                }
            }
        }
    }
}

/**
 * A job to check for apps unused in the last [UNUSED_THRESHOLD_MS]ms every
 * [CHECK_FREQUENCY_MS]ms and [revokePermissionsOnUnusedApps] for them
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
                revokePermissionsOnUnusedApps(this@AutoRevokeService)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to auto-revoke permissions", e)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.w(LOG_TAG, "onStopJob after ${System.currentTimeMillis() - jobStartTime}ms")
        job?.cancel()
        return true
    }
}