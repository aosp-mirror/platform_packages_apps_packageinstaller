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

import android.app.Application
import android.app.admin.DeviceAdminReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.service.DEBUG_AUTO_REVOKE
import com.android.permissioncontroller.permission.utils.Utils.getUserContext
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks broadcast receivers for a certain type
 *
 * @param app The current application
 * @param intentAction The name of the action the receiver receives
 * @param permission The permission required for the receiver
 * @param user The user the receivers should be determined for
 */
class BroadcastReceiverLiveData(
    private val app: Application,
    override val intentAction: String,
    private val permission: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<Set<String>>(),
        PackageBroadcastReceiver.PackageBroadcastListener,
        HasIntentAction {

    private val name = intentAction.substringAfterLast(".")

    private val enabledDeviceAdminsLiveDataLiveData = EnabledDeviceAdminsLiveData[user]

    init {
        if (intentAction == DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED) {
            addSource(enabledDeviceAdminsLiveDataLiveData) {
                updateAsync()
            }
        }
    }

    override fun onPackageUpdate(packageName: String) {
        updateAsync()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }
        if (intentAction == DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED &&
                !enabledDeviceAdminsLiveDataLiveData.isInitialized) {
            return
        }

        val packageNames = getUserContext(app, user).packageManager
                .queryBroadcastReceivers(
                        Intent(intentAction),
                        PackageManager.GET_RECEIVERS or PackageManager.GET_META_DATA)
                .mapNotNull { resolveInfo ->
                    if (resolveInfo?.activityInfo?.permission != permission) {
                        return@mapNotNull null
                    }
                    val packageName = resolveInfo?.activityInfo?.packageName
                    if (!isReceiverEnabled(packageName)) {
                        if (DEBUG_AUTO_REVOKE) {
                            DumpableLog.i(LOG_TAG,
                                    "Not exempting $packageName - not an active $name " +
                                            "for u${user.identifier}")
                        }
                        return@mapNotNull null
                    }
                    packageName
                }.toSet()
        if (DEBUG_AUTO_REVOKE) {
            DumpableLog.i(LOG_TAG,
                    "Detected ${intentAction.substringAfterLast(".")}s: $packageNames")
        }

        postValue(packageNames)
    }

    private fun isReceiverEnabled(pkg: String?): Boolean {
        if (pkg == null) {
            return false
        }
        return when (intentAction) {
            DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED -> {
                pkg in enabledDeviceAdminsLiveDataLiveData.value!!
            }
            else -> true
        }
    }

    override fun onActive() {
        super.onActive()

        PackageBroadcastReceiver.addAllCallback(this)

        updateAsync()
    }

    override fun onInactive() {
        super.onInactive()

        PackageBroadcastReceiver.removeAllCallback(this)
    }

    /**
     * Repository for [BroadcastReceiverLiveData]
     *
     * <p> Key value is a (string intent action, required permission, user) triple, value is its
     * corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<Triple<String, String, UserHandle>,
            BroadcastReceiverLiveData>() {
        private const val LOG_TAG = "BroadcastReceiverLiveData"

        override fun newValue(key: Triple<String, String, UserHandle>): BroadcastReceiverLiveData {
            return BroadcastReceiverLiveData(PermissionControllerApplication.get(),
                    key.first, key.second, key.third)
        }
    }
}