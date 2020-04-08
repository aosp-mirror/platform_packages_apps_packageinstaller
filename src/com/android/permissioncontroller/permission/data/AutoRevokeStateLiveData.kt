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

package com.android.permissioncontroller.permission.data

import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
import android.app.Application
import android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT
import android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_ROLE
import android.os.UserHandle
import android.provider.DeviceConfig
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.model.livedatatypes.AutoRevokeState
import com.android.permissioncontroller.permission.service.isPackageAutoRevokeExempt
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job
import java.util.concurrent.TimeUnit

/**
 * A LiveData which tracks the AutoRevoke state for one user package.
 *
 * @param app The current application
 * @param packageName The package name whose state we want
 * @param user The user for whom we want the package
 */
class AutoRevokeStateLiveData private constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<AutoRevokeState>(), AppOpsManager.OnOpChangedListener {

    private val packagePermsLiveData =
        PackagePermissionsLiveData[packageName, user]
    private val packageLiveData = LightPackageInfoLiveData[packageName, user]
    private val permStateLiveDatas = mutableMapOf<String, PermStateLiveData>()
    private val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!

    init {
        addSource(packagePermsLiveData) {
            updateIfActive()
        }
        addSource(packageLiveData) {
            updateIfActive()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        val uid = packageLiveData.value?.uid
        if (uid == null && packageLiveData.isInitialized) {
            postValue(null)
            return
        } else if (uid == null) {
            return
        }

        val groups = packagePermsLiveData.value?.keys?.filter { it != NON_RUNTIME_NORMAL_PERMS }
        if (groups == null && packagePermsLiveData.isInitialized) {
            postValue(null)
            return
        } else if (groups == null) {
            return
        }

        addAndRemovePermStateLiveDatas(groups)

        if (!permStateLiveDatas.all { it.value.isInitialized }) {
            return
        }

        val revocable = !isPackageAutoRevokeExempt(app, packageLiveData.getInitializedValue())
        val autoRevokeState = mutableListOf<String>()
        permStateLiveDatas.forEach { (groupName, liveData) ->
            val default = liveData.value?.any { (_, permState) ->
                permState.permFlags and (FLAG_PERMISSION_GRANTED_BY_DEFAULT or
                    FLAG_PERMISSION_GRANTED_BY_ROLE) != 0
            } ?: false
            if (!default) {
                autoRevokeState.add(groupName)
            }
        }

        postValue(AutoRevokeState(isAutoRevokeEnabledGlobal(), revocable, autoRevokeState))
    }

    private fun isAutoRevokeEnabledGlobal(): Boolean {
        val unusedThreshold = DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                Utils.PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS, TimeUnit.DAYS.toMillis(90))
        val checkFrequency = DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
            Utils.PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS, TimeUnit.DAYS.toMillis(1))
        return unusedThreshold > 0 && checkFrequency > 0
    }

    private fun addAndRemovePermStateLiveDatas(groupNames: List<String>) {
        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(groupNames,
            permStateLiveDatas)

        for (groupToAdd in toAdd) {
            val permStateLiveData =
                PermStateLiveData[packageName, groupToAdd, user]
            permStateLiveDatas[groupToAdd] = permStateLiveData
        }

        for (groupToAdd in toAdd) {
            addSource(permStateLiveDatas[groupToAdd]!!) {
                updateIfActive()
            }
        }

        for (groupToRemove in toRemove) {
            removeSource(permStateLiveDatas[groupToRemove]!!)
            permStateLiveDatas.remove(groupToRemove)
        }
    }

    override fun onOpChanged(op: String?, packageName: String?) {
        if (op == OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED && packageName == packageName) {
            updateIfActive()
        }
    }

    override fun onActive() {
        super.onActive()
        appOpsManager.startWatchingMode(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, packageName, this)
        updateIfActive()
    }

    override fun onInactive() {
        super.onInactive()
        appOpsManager.stopWatchingMode(this)
    }
    /**
     * Repository for AutoRevokeStateLiveDatas.
     * <p> Key value is a pair of string package name and UserHandle, value is its corresponding
     * LiveData.
     */
    companion object : DataRepositoryForPackage<Pair<String, UserHandle>,
        AutoRevokeStateLiveData>() {
        override fun newValue(key: Pair<String, UserHandle>): AutoRevokeStateLiveData {
            return AutoRevokeStateLiveData(PermissionControllerApplication.get(),
                key.first, key.second)
        }
    }
}
