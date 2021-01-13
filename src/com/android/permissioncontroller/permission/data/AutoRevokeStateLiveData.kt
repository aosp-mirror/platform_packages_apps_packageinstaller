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
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.model.livedatatypes.AutoRevokeState
import com.android.permissioncontroller.permission.service.ExemptServicesLiveData
import com.android.permissioncontroller.permission.service.isAutoRevokeEnabled
import com.android.permissioncontroller.permission.service.isPackageAutoRevokeExempt
import com.android.permissioncontroller.permission.service.isPackageAutoRevokePermanentlyExempt
import kotlinx.coroutines.Job

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
    private val exemptServicesLiveData = ExemptServicesLiveData[user]
    private val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!

    init {
        addSource(packagePermsLiveData) {
            update()
        }
        addSource(packageLiveData) {
            update()
        }
        addSource(exemptServicesLiveData) {
            update()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (!packageLiveData.isInitialized || !packagePermsLiveData.isInitialized ||
            !exemptServicesLiveData.isInitialized) {
            return
        }

        val groups = packagePermsLiveData.value?.keys?.filter { it != NON_RUNTIME_NORMAL_PERMS }

        if (packageLiveData.value?.uid == null || groups == null) {
            postValue(null)
            return
        }

        val getLiveData = { groupName: String -> PermStateLiveData[packageName, groupName, user] }
        setSourcesToDifference(groups, permStateLiveDatas, getLiveData)

        if (!permStateLiveDatas.all { it.value.isInitialized }) {
            return
        }

        val revocable = !isPackageAutoRevokeExempt(app, packageLiveData.value!!)
        val revocableGroups = mutableListOf<String>()
        if (!isPackageAutoRevokePermanentlyExempt(packageLiveData.value!!, user)) {
            permStateLiveDatas.forEach { (groupName, liveData) ->
                val default = liveData.value?.any { (_, permState) ->
                    permState.permFlags and (FLAG_PERMISSION_GRANTED_BY_DEFAULT or
                            FLAG_PERMISSION_GRANTED_BY_ROLE) != 0
                } ?: false
                if (!default) {
                    revocableGroups.add(groupName)
                }
            }
        }

        postValue(AutoRevokeState(isAutoRevokeEnabled(app), revocable, revocableGroups))
    }

    override fun onOpChanged(op: String?, packageName: String?) {
        if (op == OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED && packageName == packageName) {
            update()
        }
    }

    override fun onActive() {
        super.onActive()
        appOpsManager.startWatchingMode(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, packageName, this)
        update()
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
