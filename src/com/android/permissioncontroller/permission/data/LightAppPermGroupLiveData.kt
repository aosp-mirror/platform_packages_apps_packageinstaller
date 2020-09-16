/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.UserHandle
import android.permission.PermissionManager
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.OS_PKG

/**
 * A LiveData which represents the permissions for one package and permission group.
 *
 * @param app The current application
 * @param packageName The name of the package
 * @param permGroupName The name of the permission group
 * @param user The user of the package
 */
class LightAppPermGroupLiveData private constructor(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle
) : SmartUpdateMediatorLiveData<LightAppPermGroup?>(), LocationUtils.LocationListener {

    val LOG_TAG = this::class.java.simpleName

    private var isSpecialLocation = false
    private val permStateLiveData = PermStateLiveData[packageName, permGroupName, user]
    private val permGroupLiveData = PermGroupLiveData[permGroupName]
    private val packageInfoLiveData = LightPackageInfoLiveData[packageName, user]
    private val fgPermNamesLiveData = ForegroundPermNamesLiveData

    init {
        isSpecialLocation = LocationUtils.isLocationGroupAndProvider(app,
            permGroupName, packageName) ||
            LocationUtils.isLocationGroupAndControllerExtraPackage(app, permGroupName, packageName)

        addSource(fgPermNamesLiveData) {
            update()
        }

        val key = Triple(packageName, permGroupName, user)

        addSource(permStateLiveData) { permStates ->
            if (permStates == null && permStateLiveData.isInitialized) {
                invalidateSingle(key)
                value = null
            } else {
                update()
            }
        }

        addSource(permGroupLiveData) { permGroup ->
            if (permGroup == null && permGroupLiveData.isInitialized) {
                invalidateSingle(key)
                value = null
            } else {
                update()
            }
        }

        addSource(packageInfoLiveData) { packageInfo ->
            if (packageInfo == null && packageInfoLiveData.isInitialized) {
                invalidateSingle(key)
                value = null
            } else {
                update()
            }
        }
    }

    override fun onUpdate() {
        val permStates = permStateLiveData.value ?: return
        val permGroup = permGroupLiveData.value ?: return
        val packageInfo = packageInfoLiveData.value ?: return
        val allForegroundPerms = fgPermNamesLiveData.value ?: return

        // Do not allow toggling pre-M custom perm groups
        if (packageInfo.targetSdkVersion < Build.VERSION_CODES.M &&
            permGroup.groupInfo.packageName != OS_PKG) {
            value = LightAppPermGroup(packageInfo, permGroup.groupInfo, emptyMap())
            return
        }

        val permissionMap = mutableMapOf<String, LightPermission>()
        for ((permName, permState) in permStates) {
            val permInfo = permGroup.permissionInfos[permName] ?: continue
            val foregroundPerms = allForegroundPerms[permName]
            permissionMap[permName] = LightPermission(packageInfo, permInfo, permState,
                    foregroundPerms)
        }

        // Determine if this app permission group is a special location package or provider
        var specialLocationGrant: Boolean? = null
        val userContext = Utils.getUserContext(app, user)
        if (LocationUtils.isLocationGroupAndProvider(userContext, permGroupName, packageName)) {
            specialLocationGrant = LocationUtils.isLocationEnabled(userContext)
        } else if (LocationUtils.isLocationGroupAndControllerExtraPackage(app, permGroupName,
                packageName)) {
            // The permission of the extra location controller package is determined by the status
            // of the controller package itself.
            specialLocationGrant = LocationUtils.isExtraLocationControllerPackageEnabled(
                userContext)
        }

        val hasInstallToRuntimeSplit = hasInstallToRuntimeSplit(packageInfo, permissionMap)
        value = LightAppPermGroup(packageInfo, permGroup.groupInfo, permissionMap,
            hasInstallToRuntimeSplit, specialLocationGrant)
    }

    /**
     * Check if permission group contains a runtime permission that split from an installed
     * permission and the split happened in an Android version higher than app's targetSdk.
     *
     * @return `true` if there is such permission, `false` otherwise
     */
    private fun hasInstallToRuntimeSplit(
        packageInfo: LightPackageInfo,
        permissionMap: Map<String, LightPermission>
    ): Boolean {
        val permissionManager = app.getSystemService(PermissionManager::class.java) ?: return false

        for (spi in permissionManager.splitPermissions) {
            val splitPerm = spi.splitPermission

            val pi = try {
                app.packageManager.getPermissionInfo(splitPerm, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(LOG_TAG, "No such permission: $splitPerm", e)
                continue
            }

            // Skip if split permission is not "install" permission.
            if (pi.protection != PermissionInfo.PROTECTION_NORMAL) {
                continue
            }

            val newPerms = spi.newPermissions
            for (permName in newPerms) {
                val newPerm = permissionMap[permName]?.permInfo ?: continue

                // Skip if new permission is not "runtime" permission.
                if (newPerm.protection != PermissionInfo.PROTECTION_DANGEROUS) {
                    continue
                }

                if (packageInfo.targetSdkVersion < spi.targetSdk) {
                    return true
                }
            }
        }
        return false
    }

    override fun onLocationStateChange(enabled: Boolean) {
        update()
    }

    override fun onActive() {
        super.onActive()

        if (isSpecialLocation) {
            LocationUtils.addLocationListener(this)
            update()
        }
    }

    override fun onInactive() {
        super.onInactive()

        if (isSpecialLocation) {
            LocationUtils.removeLocationListener(this)
        }
    }

    /**
     * Repository for AppPermGroupLiveDatas.
     * <p> Key value is a triple of string package name, string permission group name, and
     * UserHandle, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<Triple<String, String, UserHandle>,
        LightAppPermGroupLiveData>() {
        override fun newValue(key: Triple<String, String, UserHandle>):
            LightAppPermGroupLiveData {
            return LightAppPermGroupLiveData(PermissionControllerApplication.get(),
                key.first, key.second, key.third)
        }
    }
}