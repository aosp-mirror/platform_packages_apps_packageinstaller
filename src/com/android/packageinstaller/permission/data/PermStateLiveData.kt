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

package com.android.packageinstaller.permission.data

import android.Manifest
import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.packageinstaller.permission.model.livedatatypes.LightPackageInfo
import com.android.packageinstaller.permission.model.livedatatypes.PermState
import com.android.packageinstaller.permission.utils.LocationUtils
import com.android.packageinstaller.permission.utils.Utils

/**
 * A LiveData which tracks the permission state for one permission group for one package. It
 * includes both the granted state of every permission in the group, and the flags stored
 * in the PermissionController service.
 *
 * @param app: The current application
 * @param packageName: The name of the package this LiveData will watch for mode changes for
 * @param permissionGroupName: The name of the permission group whose app ops this LiveData
 * will watch
 * @param user: The user of the package
 */
class PermStateLiveData(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<Map<String, PermState>>(),
    PermissionListenerMultiplexer.PermissionChangeCallback {

    private val context = Utils.getUserContext(app, user)
    private val packageInfoLiveData =
        PackageInfoRepository.getPackageInfoLiveData(app, packageName, user)
    private val groupLiveData =
        PermGroupRepository.getPermGroupLiveData(app, permissionGroupName)

    private var uid: Int? = null
    private var registeredUid: Int? = null

    init {
        addSource(packageInfoLiveData) {
            checkForUidUpdate(it)
            updateAsync()
        }

        addSource(groupLiveData) {
            updateAsync()
        }
    }

    /**
     * Gets the system flags from the package manager, and the grant state from those flags, plus
     * the RequestedPermissionFlags of the PackageInfo.
     *
     * @param isCancelled: A boolean function saying whether or not this task should been cancelled
     *
     * @return A map of permission name to a PermState object with both types of flag.
     */
    override fun loadData(isCancelled: () -> Boolean): Map<String, PermState>? {
        val packageInfo = packageInfoLiveData.value ?: return value
        val permissionGroup = groupLiveData.value ?: return value
        val allPermissionFlags = mutableMapOf<String, PermState>()
        for ((index, permissionName) in packageInfo.requestedPermissions.withIndex()) {
            if (isCancelled()) {
                // return the current value, which will be ignored
                return value
            }

            permissionGroup.permissionInfos[permissionName]?.let { permInfo ->
                val packageFlags = packageInfo.requestedPermissionsFlags[index]
                val permFlags = context.packageManager.getPermissionFlags(permInfo.name,
                    packageName, user)
                var granted = packageFlags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 &&
                    permFlags and PackageManager.FLAG_PERMISSION_REVOKED_COMPAT == 0

                // Check if this package is a location provider
                if (permissionGroupName == Manifest.permission_group.LOCATION) {
                    val userContext = Utils.getUserContext(app, user)
                    if (LocationUtils.isLocationGroupAndProvider(userContext, permissionGroupName,
                                    packageName)) {
                        granted = LocationUtils.isLocationEnabled(userContext)
                    }
                    // The permission of the extra location controller package is determined by the
                    // status of the controller package itself.
                    if (LocationUtils.isLocationGroupAndControllerExtraPackage(userContext,
                                    permissionGroupName, packageName)) {
                        granted = LocationUtils.isExtraLocationControllerPackageEnabled(userContext)
                    }
                }
                allPermissionFlags[permissionName] = PermState(permFlags, granted)
            }
        }

        return allPermissionFlags
    }

    override fun onPermissionChange() {
        updateAsync()
    }

    private fun checkForUidUpdate(packageInfo: LightPackageInfo?) {
        if (packageInfo == null) {
            registeredUid?.let {
                PackageInfoRepository.permissionListenerMultiplexer?.removeCallback(it, this)
            }
            return
        }
        uid = packageInfo.uid
        if (uid != registeredUid) {
            PackageInfoRepository.permissionListenerMultiplexer?.addOrReplaceCallback(
                registeredUid, packageInfo.uid, this)
            registeredUid = uid
        }
    }

    override fun onInactive() {
        super.onInactive()
        registeredUid?.let {
            PackageInfoRepository.permissionListenerMultiplexer?.removeCallback(it, this)
            registeredUid = null
        }
    }

    override fun onActive() {
        super.onActive()
        uid?.let {
            PackageInfoRepository.permissionListenerMultiplexer?.addCallback(it, this)
            registeredUid = uid
        }
        updateAsync()
    }
}

/**
 * Repository for PermStateLiveDatas.
 * <p> Key value is a triple of string package name, string permission group name, and UserHandle,
 * value is its corresponding LiveData.
 */
object PermStateRepository
    : DataRepository<Triple<String, String, UserHandle>, PermStateLiveData>() {

    /**
     * Gets the PermStateLiveData associated with the provided package name, permission group,
     * and user, creating it if need be.
     *
     * @param app: The current application
     * @param packageName: The name of the package whose permission state we want
     * @param permissionGroupName: The name of the permission group whose state we want
     * @param user: The UserHandle for whom we want the permission state
     *
     * @return The cached or newly created PackageInfoLiveData
     */
    fun getPermStateLiveData(
        app: Application,
        packageName: String,
        permissionGroupName: String,
        user: UserHandle
    ): PermStateLiveData {
        return getDataObject(app, Triple(packageName, permissionGroupName, user))
    }

    override fun newValue(
        app: Application,
        key: Triple<String, String, UserHandle>
    ): PermStateLiveData {
        return PermStateLiveData(app, key.first, key.second, key.third)
    }
}
