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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermState
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks the permission state for one permission group for one package. It
 * includes both the granted state of every permission in the group, and the flags stored
 * in the PermissionController service.
 *
 * @param app The current application
 * @param packageName The name of the package this LiveData will watch for mode changes for
 * @param permissionGroupName The name of the permission group whose app ops this LiveData
 * will watch
 * @param user The user of the package
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
     */
    override suspend fun loadDataAndPostValue(job: Job) {
        if (!packageInfoLiveData.isInitialized || !groupLiveData.isInitialized) {
            return
        }
        val packageInfo = packageInfoLiveData.value
        val permissionGroup = groupLiveData.value
        if (packageInfo == null || permissionGroup == null) {
            postValue(null)
            return
        }
        val permissionStates = mutableMapOf<String, PermState>()
        for ((index, permissionName) in packageInfo.requestedPermissions.withIndex()) {

            permissionGroup.permissionInfos[permissionName]?.let { permInfo ->
                val packageFlags = packageInfo.requestedPermissionsFlags[index]
                val permFlags = context.packageManager.getPermissionFlags(permInfo.name,
                    packageName, user)
                var granted = packageFlags and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 &&
                    permFlags and PackageManager.FLAG_PERMISSION_REVOKED_COMPAT == 0

                if (job.isCancelled) {
                    return
                }
                permissionStates[permissionName] = PermState(permFlags, granted)
            }
        }

        postValue(permissionStates)
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
     * @param app The current application
     * @param packageName The name of the package whose permission state we want
     * @param permissionGroupName The name of the permission group whose state we want
     * @param user The UserHandle for whom we want the permission state
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
