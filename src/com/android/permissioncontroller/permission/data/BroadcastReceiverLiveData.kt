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
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerApplication
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
    private val DEBUG = false

    override fun onPackageUpdate(packageName: String) {
        updateAsync()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
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
                    resolveInfo?.activityInfo?.packageName
                }.toSet()
        if (DEBUG) {
            DumpableLog.i(LOG_TAG,
                    "Detected ${intentAction.substringAfterLast(".")}s: $packageNames")
        }

        postValue(packageNames)
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
        override fun newValue(key: Triple<String, String, UserHandle>): BroadcastReceiverLiveData {
            return BroadcastReceiverLiveData(PermissionControllerApplication.get(),
                    key.first, key.second, key.third)
        }
    }
}