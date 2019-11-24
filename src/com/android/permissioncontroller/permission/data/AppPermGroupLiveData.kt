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
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.SoftRestrictedPermissionPolicy
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.OS_PKG

/**
 * A LiveData which represents the permissions for one package and permission group.
 *
 * @param app: The current application
 * @param packageName: The name of the package
 * @param permGroupName: The name of the permission group
 * @param user: The user of the package
 */
class AppPermGroupLiveData(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle
) : SmartUpdateMediatorLiveData<LightAppPermGroup>() {

    private val permStateLiveData = PermStateRepository.getPermStateLiveData(app, packageName,
        permGroupName, user)

    private val permGroupLiveData = PermGroupRepository.getPermGroupLiveData(app, permGroupName)

    private val packageInfoLiveData = PackageInfoRepository.getPackageInfoLiveData(app,
        packageName, user)

    init {
        addSource(permStateLiveData) { permStates ->
            if (permStates == null && permStateLiveData.isInitialized) {
                value = null
            } else {
                update()
            }
        }

        addSource(permGroupLiveData) { permGroup ->
            if (permGroup == null && permGroupLiveData.isInitialized) {
                value = null
            } else {
                update()
            }
        }

        addSource(packageInfoLiveData) { packageInfo ->
            if (packageInfo == null && packageInfoLiveData.isInitialized) {
                value = null
            } else {
                update()
            }
        }
    }

    private fun update() {
        val permStates = permStateLiveData.value ?: return
        val permGroup = permGroupLiveData.value ?: return
        val packageInfo = packageInfoLiveData.value ?: return

        // Do not allow toggling pre-M custom perm groups
        if (packageInfo.targetSdkVersion < Build.VERSION_CODES.M &&
            permGroup.groupInfo.packageName != OS_PKG) {
            value = LightAppPermGroup(packageInfo, permGroup.groupInfo, emptyMap())
            return
        }

        val permissionMap = mutableMapOf<String, LightPermission>()
        val whitelistedRestricted = app.packageManager.getWhitelistedRestrictedPermissions(
                packageName, Utils.FLAGS_PERMISSION_WHITELIST_ALL)
        for ((permName, permState) in permStates) {
            val permInfo = permGroup.permissionInfos[permName] ?: continue
            val isHardRestricted = permInfo.flags and PermissionInfo.FLAG_HARD_RESTRICTED != 0
            val isWhitelisted = whitelistedRestricted.contains(permName)
            val isSoftRestricted = permInfo.flags and PermissionInfo.FLAG_SOFT_RESTRICTED != 0 &&
                    !SoftRestrictedPermissionPolicy.shouldShow(packageInfo, permName,
                            permState.permFlags)
            if ((!isHardRestricted || isWhitelisted) && (!isSoftRestricted)) {
                permissionMap[permName] = LightPermission(permInfo, permState)
            }
        }

        // Determine if this app permission group is a special location package or provider
        var specialLocationGrant: Boolean? = null
        val userContext = Utils.getUserContext(app, user)
        if (LocationUtils.isLocationGroupAndProvider(userContext, permGroupName, packageName)) {
            specialLocationGrant = LocationUtils.isLocationEnabled(app)
        }
        // The permission of the extra location controller package is determined by the status of
        // the controller package itself.
        if (LocationUtils.isLocationGroupAndControllerExtraPackage(app, permGroupName,
                packageName)) {
            specialLocationGrant = LocationUtils.isExtraLocationControllerPackageEnabled(
                userContext)
        }
        value = LightAppPermGroup(packageInfo, permGroup.groupInfo, permissionMap,
            specialLocationGrant)
    }
}