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

package com.android.permissioncontroller.permission.service

import android.content.Intent
import android.os.Process.myUserHandle
import android.os.UserHandle
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.utils.KotlinUtils
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A service to re-grant auto revoked permissions on a one-time basis
 */
class AutoRevokeReGrantService : android.app.Service() {
    companion object {
        private const val LOG_TAG = "AutoRevokeReGrantService"
    }

    lateinit var job: Job

    override fun onCreate() {
        super.onCreate()
        job = GlobalScope.launch(Main) {
            try {
                reGrantAutoRevokedPermissions()
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        if (!job.isCompleted) {
            DumpableLog.e(LOG_TAG, "${javaClass.simpleName} terminated before completing",
                    RuntimeException())
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private suspend fun reGrantAutoRevokedPermissions() {
        DumpableLog.i(LOG_TAG, "reGrantAutoRevokedPermissions")
        val startTime = System.currentTimeMillis()
        try {
            for ((user, packageToGroups) in loadAllPermissionGroups()) {
                for ((packageName, groups) in packageToGroups) {
                    for (group in groups) {
                        val autoRevokedPermissions = group
                                .allPermissions
                                .filter { (permName, perm) -> perm.isAutoRevoked }
                                .map { (permName, perm) -> permName }
                        if (autoRevokedPermissions.isNotEmpty()) {
                            DumpableLog.i(LOG_TAG,
                                    "Re-granting to u${user.identifier} $packageName: " +
                                    "$autoRevokedPermissions")
                            KotlinUtils.grantForegroundRuntimePermissions(
                                    PermissionControllerApplication.get(),
                                    group,
                                    autoRevokedPermissions)
                            KotlinUtils.grantBackgroundRuntimePermissions(
                                    PermissionControllerApplication.get(),
                                    group,
                                    autoRevokedPermissions)
                        }
                    }
                }
            }
            DumpableLog.i(LOG_TAG, "Done reGrantAutoRevokedPermissions in " +
                    "${System.currentTimeMillis() - startTime}ms")
        } catch (t: Throwable) {
            DumpableLog.e(LOG_TAG, "Failed reGrantAutoRevokedPermissions in " +
                    "${System.currentTimeMillis() - startTime}ms", t)
            throw t
        }
    }

    /** @return user -> packageName -> [permissionGroup] for all users for all packages */
    private suspend fun loadAllPermissionGroups():
            Map<UserHandle, Map<String, List<LightAppPermGroup>>> {
        DumpableLog.i(LOG_TAG, "loadAllPermissionGroups")
        val startTime = System.currentTimeMillis()
        val result = AllPackageInfosLiveData
                .getInitializedValue()
                .map { (user, packages) ->
                    val packagesToGroups = packages?.map { packageInfo ->
                        val packageName = packageInfo.packageName
                        val permissionGroups = PackagePermissionsLiveData[
                                packageName, myUserHandle()]
                                .getInitializedValue()
                                ?.mapNotNull { (groupName, _) ->
                                    LightAppPermGroupLiveData[
                                            packageName, groupName, myUserHandle()]
                                            .getInitializedValue()
                                } ?: emptyList()
                        packageName to permissionGroups
                    }.toMap()
                    user to packagesToGroups
                }.toMap()
        DumpableLog.i(LOG_TAG, "Done loadAllPermissionGroups in " +
                "${System.currentTimeMillis() - startTime}ms")
        return result
    }
}