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

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermState
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.Utils.isModernPermissionGroup

/**
 * A LiveData representing UI properties of an App Permission Group:
 * <ul>
 *     <li>shouldShow</li>
 *     <li>isSystem</li>
 *     <li>isGranted</li>
 * </ul>
 *
 * @param app The current application
 * @param packageName The name of the package
 * @param permGroupName The name of the permission group whose permissions are observed
 * @param user The user of the package
 */
class AppPermGroupUiInfoLiveData private constructor(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle
) : SmartUpdateMediatorLiveData<AppPermGroupUiInfo>(), LocationUtils.LocationListener {

    private var isSpecialLocation = false
    private val isMicrophone = permGroupName == Manifest.permission_group.MICROPHONE
    private val packageInfoLiveData = LightPackageInfoLiveData[packageName, user]
    private val permGroupLiveData = PermGroupLiveData[permGroupName]
    private val permissionStateLiveData = PermStateLiveData[packageName, permGroupName, user]

    init {
        isSpecialLocation = LocationUtils.isLocationGroupAndProvider(app,
            permGroupName, packageName) ||
            LocationUtils.isLocationGroupAndControllerExtraPackage(app, permGroupName, packageName)

        addSource(packageInfoLiveData) {
            updateIfActive()
        }

        addSource(permGroupLiveData) {
            updateIfActive()
        }

        addSource(permissionStateLiveData) {
            updateIfActive()
        }
    }

    override fun onUpdate() {
        val packageInfo = packageInfoLiveData.value
        val permissionGroup = permGroupLiveData.value
        val permissionState = permissionStateLiveData.value

        if (packageInfo == null || permissionGroup == null || permissionState == null) {
            if (packageInfoLiveData.isInitialized && permGroupLiveData.isInitialized &&
                permissionStateLiveData.isInitialized) {
                invalidateSingle(Triple(packageName, permGroupName, user))
                value = null
            }
            return
        }

        value = getAppPermGroupUiInfo(packageInfo, permissionGroup.groupInfo,
            permissionGroup.permissionInfos, permissionState)
    }

    /**
     * Determines if the UI should show a given package, if that package is a system app, and
     * if it has granted permissions in this LiveData's permission group.
     *
     * @param packageInfo The PackageInfo of the package we wish to examine
     * @param groupInfo The groupInfo of the permission group we wish to examine
     * @param allPermInfos All of the PermissionInfos in the permission group
     * @param permissionState The flags and grant state for all permissions in the permission
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
     * @param packageInfo The PackageInfo of the package we are examining
     * @param groupInfo The Permission Group Info of the permission group we are examining
     * @param permissionInfos The LightPermInfos corresponding to the permissions in the
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
            !isModernPermissionGroup(groupInfo.name)) {
            return false
        }
        return true
    }

    /**
     * Determines if an app's permission group is user-sensitive. If an app is not user sensitive,
     * then it is considered a system app, and hidden in the UI by default.
     *
     * @param permissionState The permission flags and grant state corresponding to the permissions
     * in this group requested by a given app
     *
     * @return Whether or not this package requests a user sensitive permission in the given
     * permission group
     */
    private fun isUserSensitive(permissionState: Map<String, PermState>): Boolean {
        if (!isModernPermissionGroup(permGroupName)) {
            return true
        }

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
     * @param permissionState The permission flags and grant state corresponding to the permissions
     * in this group requested by a given app
     * @param allPermInfos All of the permissionInfos in the permission group of this app
     * permission group
     *
     * @return The int code corresponding to the app permission group state, either allowed, allowed
     * in foreground only, or denied.
     */
    private fun getGrantedIncludingBackground(
        permissionState: Map<String, PermState>,
        allPermInfos: Map<String, LightPermInfo>
    ): PermGrantState {
        val specialLocationState = getIsSpecialLocationState()

        var hasPermWithBackground = false
        var isUserFixed = false
        var isOneTime = false
        for ((permName, permState) in permissionState) {
            val permInfo = allPermInfos[permName] ?: continue
            permInfo.backgroundPermission?.let { backgroundPerm ->
                hasPermWithBackground = true
                if (permissionState[backgroundPerm]?.granted == true &&
                    specialLocationState != false) {
                    return PermGrantState.PERMS_ALLOWED_ALWAYS
                }
            }
            isUserFixed = isUserFixed ||
                    permState.permFlags and PackageManager.FLAG_PERMISSION_USER_FIXED != 0
            isOneTime = isOneTime ||
                    permState.permFlags and PackageManager.FLAG_PERMISSION_ONE_TIME != 0
        }

        val anyAllowed = specialLocationState ?: permissionState.any { it.value.granted }
        if (anyAllowed && (hasPermWithBackground || shouldShowAsForegroundGroup())) {
            if (isOneTime) {
                return PermGrantState.PERMS_ASK
            } else {
                if (Utils.couldHaveForegroundCapabilities(
                                Utils.getUserContext(app, user), packageName) ||
                        Utils.isEmergencyApp(Utils.getUserContext(app, user), packageName)) {
                    return PermGrantState.PERMS_ALLOWED_ALWAYS
                } else {
                    return PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY
                }
            }
        } else if (anyAllowed) {
            if (isOneTime) {
                return PermGrantState.PERMS_ASK
            } else {
                return PermGrantState.PERMS_ALLOWED
            }
        }
        if (isUserFixed) {
            return PermGrantState.PERMS_DENIED
        }
        if (isOneTime) {
            return PermGrantState.PERMS_ASK
        }
        return PermGrantState.PERMS_DENIED
    }

    private fun getIsSpecialLocationState(): Boolean? {
        if (!isSpecialLocation) {
            return null
        }

        val userContext = Utils.getUserContext(app, user)
        if (LocationUtils.isLocationGroupAndProvider(userContext, permGroupName, packageName)) {
            return LocationUtils.isLocationEnabled(userContext)
        }
        // The permission of the extra location controller package is determined by the
        // status of the controller package itself.
        if (LocationUtils.isLocationGroupAndControllerExtraPackage(userContext,
                permGroupName, packageName)) {
            return LocationUtils.isExtraLocationControllerPackageEnabled(userContext)
        }
        return null
    }

    // TODO moltmann-team: Actually change mic/camera to be a foreground only permission
    private fun shouldShowAsForegroundGroup(): Boolean {
        return permGroupName.equals(Manifest.permission_group.CAMERA) ||
                permGroupName.equals(Manifest.permission_group.MICROPHONE)
    }

    override fun onLocationStateChange(enabled: Boolean) {
        updateIfActive()
    }

    override fun onActive() {
        super.onActive()
        if (isSpecialLocation) {
            LocationUtils.addLocationListener(this)
            updateIfActive()
        }
    }

    override fun onInactive() {
        super.onInactive()

        if (isSpecialLocation) {
            LocationUtils.removeLocationListener(this)
        }
    }

    /**
     * Repository for AppPermGroupUiInfoLiveDatas.
     * <p> Key value is a triple of string package name, string permission group name, and UserHandle,
     * value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<Triple<String, String, UserHandle>,
            AppPermGroupUiInfoLiveData>() {
        override fun newValue(key: Triple<String, String, UserHandle>):
                AppPermGroupUiInfoLiveData {
            return AppPermGroupUiInfoLiveData(PermissionControllerApplication.get(),
                    key.first, key.second, key.third)
        }
    }
}