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
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermState
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.Utils

/**
 * A LiveData representing UI properties of an App Permission Group:
 * <ul>
 *     <li>shouldShow</li>
 *     <li>isSystem</li>
 *     <li>isGranted</li>
 * </ul>
 *
 * @param app: The current application
 * @param packageName: The name of the package
 * @param permissionGroupName: The name of the permission group whose permissions are observed
 * @param user: The user of the package
 */
class AppPermGroupUiInfoLiveData(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    private val user: UserHandle
) : SmartUpdateMediatorLiveData<AppPermGroupUiInfo>() {

    private val packageInfoLiveData = PackageInfoRepository.getPackageInfoLiveData(app, packageName,
        user)
    private val permGroupLiveData = PermGroupRepository.getPermGroupLiveData(app,
        permissionGroupName)
    private val permissionStateLiveData = PermStateRepository.getPermStateLiveData(app, packageName,
        permissionGroupName, user)

    init {
        addSource(packageInfoLiveData) {
            update()
        }

        addSource(permGroupLiveData) {
            update()
        }

        addSource(permissionStateLiveData) {
            update()
        }
    }

    private fun update() {
        val packageInfo = packageInfoLiveData.value
        val permissionGroup = permGroupLiveData.value
        val permissionFlags = permissionStateLiveData.value

        if (packageInfo == null || permissionGroup == null || permissionFlags == null) {
            if (packageInfoLiveData.isInitialized && permGroupLiveData.isInitialized &&
                permissionStateLiveData.isInitialized) {
                value = null
            }
            return
        }

        value = getAppPermGroupUiInfo(packageInfo, permissionGroup.groupInfo,
            permissionGroup.permissionInfos, permissionFlags)
    }

    /**
     * Determines if the UI should show a given package, if that package is a system app, and
     * if it has granted permissions in this LiveData's permission group.
     *
     * @param packageInfo: The PackageInfo of the package we wish to examine
     * @param groupInfo: The groupInfo of the permission group we wish to examine
     * @param allPermInfos: All of the PermissionInfos in the permission group
     * @param permissionState: The flags and grant state for all permissions in the permission
     * group that this package requests
     */
    private fun getAppPermGroupUiInfo(
        packageInfo: LightPackageInfo,
        groupInfo: LightPermGroupInfo,
        allPermInfos: Map<String, LightPermInfo>,
        permissionState: Map<String, PermState>
    ): AppPermGroupUiInfo {
        /*
         * Filter out any permission infos in the permission group that this package
         * does not request.
         */
        val requestedPermissionInfos =
            allPermInfos.filter { permissionState.containsKey(it.key) }.values

        val shouldShow = packageInfo.enabled && isGrantableAndNotLegacyPlatform(packageInfo,
            groupInfo, requestedPermissionInfos)

        val isSystemApp = !isUserSensitive(permissionState)

        val isGranted = getGrantedIncludingBackground(permissionState, allPermInfos)

        return AppPermGroupUiInfo(shouldShow, isGranted, isSystemApp)
    }

    /**
     * Determines if a package permission group is able to be granted, and whether or not it is a
     * legacy system permission group.
     *
     * @param packageInfo: The PackageInfo of the package we are examining
     * @param groupInfo: The Permission Group Info of the permission group we are examining
     * @param permissionInfos: The LightPermInfos corresponding to the permissions in the
     * permission group that this package requests
     *
     * @return True if the app permission group is grantable, and is not a legacy system permission,
     * false otherwise.
     */
    private fun isGrantableAndNotLegacyPlatform(
        packageInfo: LightPackageInfo,
        groupInfo: LightPermGroupInfo,
        permissionInfos: Collection<LightPermInfo>
    ): Boolean {
        var hasPreRuntime = false

        for (permissionInfo in permissionInfos) {
            if (permissionInfo.protectionFlags and
                PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY == 0) {
                hasPreRuntime = true
                break
            }
        }

        val isGrantingAllowed = !packageInfo.isInstantApp &&
            (packageInfo.targetSdkVersion >= Build.VERSION_CODES.M || hasPreRuntime)
        if (!isGrantingAllowed) {
            return false
        }

        if (groupInfo.packageName == Utils.OS_PKG &&
            !Utils.isModernPermissionGroup(groupInfo.name)) {
            return false
        }
        return true
    }

    /**
     * Determines if an app's permission group is user-sensitive. If an app is not user sensitive,
     * then it is considered a system app, and hidden in the UI by default.
     *
     * @param permissionState: The permission flags and grant state corresponding to the permissions
     * in this group requested by a given app
     *
     * @return Whether or not this package requests a user sensitive permission in the given
     * permission group
     */
    private fun isUserSensitive(permissionState: Map<String, PermState>): Boolean {
        for (permissionName in permissionState.keys) {
            val flags = permissionState[permissionName]?.permFlags ?: return true
            val granted = permissionState[permissionName]?.granted ?: return true
            if ((granted &&
                    flags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED != 0) ||
                (!granted &&
                    flags and PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED != 0)) {
                return true
            }
        }
        return false
    }

    /**
     * Determines if this app permission group is granted, granted in foreground only, or denied.
     * It is granted if it either requests no background permissions, and has at least one requested
     * permission that is granted, or has granted at least one requested background permission.
     * It is granted in foreground only if it has at least one non-background permission granted,
     * and has denied all requested background permissions. It is denied if all requested
     * permissions are denied.
     *
     * @param permissionState: The permission flags and grant state corresponding to the permissions
     * in this group requested by a given app
     * @param allPermInfos: All of the permissionInfos in the permission group of this app
     * permission group
     *
     * @return The int code corresponding to the app permission group state, either allowed, allowed
     * in foreground only, or denied.
     */
    private fun getGrantedIncludingBackground(
        permissionState: Map<String, PermState>,
        allPermInfos: Map<String, LightPermInfo>
    ): PermGrantState {

        var hasPermWithBackground = false
        for ((permName, _) in permissionState) {
            val permInfo = allPermInfos[permName] ?: continue
            permInfo.backgroundPermission?.let { backgroundPerm ->
                hasPermWithBackground = true
                if (permissionState[backgroundPerm]?.granted == true) {
                    return PermGrantState.PERMS_ALLOWED
                }
            }
        }

        val anyAllowed = getIsSpecialLocationState() ?: permissionState.any { it.value.granted }
        if (anyAllowed && hasPermWithBackground) {
            return PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
        } else if (anyAllowed) {
            return PermGrantState.PERMS_ALLOWED
        }
        return PermGrantState.PERMS_DENIED
    }

    private fun getIsSpecialLocationState(): Boolean? {
        val userContext = Utils.getUserContext(app, user)
        if (LocationUtils.isLocationGroupAndProvider(userContext, permissionGroupName,
                packageName)) {
            return LocationUtils.isLocationEnabled(userContext)
        }
        // The permission of the extra location controller package is determined by the
        // status of the controller package itself.
        if (LocationUtils.isLocationGroupAndControllerExtraPackage(userContext,
                permissionGroupName, packageName)) {
            return LocationUtils.isExtraLocationControllerPackageEnabled(userContext)
        }
        return null
    }
}

/**
 * Repository for PermissionFlagsLiveDatas.
 * <p> Key value is a triple of string package name, string permission group name, and UserHandle,
 * value is its corresponding LiveData.
 */
object AppPermGroupUiInfoRepository
    : DataRepository<Triple<String, String, UserHandle>, AppPermGroupUiInfoLiveData>() {

    /**
     * Gets the AppPermGroupUiInfoLiveData associated with the provided package name, permission
     * group, and user, creating it if need be.
     *
     * @param app: The current application
     * @param packageName: The name of the package whose UI info we want
     * @param permissionGroupName: The name of the permission group whose UI info we want
     * @param user: The UserHandle for whom we want the UI info
     *
     * @return The cached or newly created AppPermGroupUiInfoLiveData
     */
    fun getAppPermGroupUiInfoLiveData(
        app: Application,
        packageName: String,
        permissionGroupName: String,
        user: UserHandle
    ): AppPermGroupUiInfoLiveData {
        return getDataObject(app, Triple(packageName, permissionGroupName, user))
    }

    override fun newValue(
        app: Application,
        key: Triple<String, String, UserHandle>
    ): AppPermGroupUiInfoLiveData {
        return AppPermGroupUiInfoLiveData(app, key.first, key.second, key.third)
    }
}