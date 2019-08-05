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
 * limitations under the License
 */

package com.android.packageinstaller.permission.data

import android.app.AppOpsManager
import android.app.Application
import android.os.UserHandle
import androidx.lifecycle.MediatorLiveData

/**
 * Live Data representing the modes of all app ops used by a particular packagename and permission
 * group.
 *
 * @param app: The application this LiveData is being instantiated in
 * @param packageName: The name of the package this LiveData will watch for mode changes for
 * @param permissionGroupName: The name of the permission group whose app ops this LiveData
 * will watch
 * @param user: The user whose App Ops will be observed
 */
class AppOpsLiveData(
    private val app: Application,
    private val packageName: String,
    private val permissionGroupName: String,
    user: UserHandle
) : MediatorLiveData<Map<String, Int>?>(),
    AppOpModeChangeListenerMultiplexer.OnAppOpModeChangeListener {

    private val context = app.applicationContext

    /**
     * Maps an String op name to its current mode (Int).
     */
    private val ops = mutableMapOf<String, Int>()
    private val appOpsManager =
        context.getSystemService(AppOpsManager::class.java)
    private val groupLiveData =
        PermissionGroupRepository.getPermissionGroupLiveData(app, permissionGroupName, user)
    private val packageLiveData =
        PackageInfoRepository.getPackageInfoLiveData(app, packageName, user)
    private val uid = packageLiveData.value!!.applicationInfo.uid

    init {
        populateOps()
        addSource(groupLiveData) {
            populateOps()
        }
    }

    override fun onChanged(op: String, packageName: String) {
        if (packageName == this.packageName) {
            val opMode = appOpsManager.unsafeCheckOpNoThrow(op, uid, packageName)
            if (opMode != ops[op]) {
                ops[op] = opMode
                value = ops
            }
        }
    }

    /**
     * Get updates for all ops in the given package name and permission group,
     * then updates the map, and notifies observers.
     */
    private fun populateOps() {
        removeListeners()
        ops.clear()

        val permissionInfos = groupLiveData.value?.permissionInfos
        if (permissionInfos == null) {
            this.value = null
            return
        }
        for ((_, permissionInfo) in permissionInfos) {
            val opName = AppOpsManager.permissionToOp(permissionInfo.name) ?: continue
            val opMode = appOpsManager.unsafeCheckOpNoThrow(opName, uid, packageName)
            ops[opName] = opMode
        }

        addListeners()
        value = ops
    }

    private fun addListeners() {
        for (key in ops.keys) {
            AppOpRepository.getAppOpChangeListener(app, key).addListener(packageName, this)
        }
    }

    private fun removeListeners() {
        for (key in ops.keys) {
            AppOpRepository.getAppOpChangeListener(app, key).removeListener(packageName, this)
        }
    }

    override fun onActive() {
        super.onActive()

        addListeners()
    }

    override fun onInactive() {
        super.onInactive()

        removeListeners()
    }
}