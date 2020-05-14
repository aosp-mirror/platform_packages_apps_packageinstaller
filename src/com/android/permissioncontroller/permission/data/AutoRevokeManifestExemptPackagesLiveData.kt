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
import android.os.UserHandle
import android.permission.PermissionManager
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils.getUserContext
import kotlinx.coroutines.Job

/**
 * A Livedata for the list of packages that have permissions that specified
 * {@code allowDontAutoRevokePermissions=true} in their {@code application} manifest declaration.
 *
 * @param app The current application
 * @param user The users the packages belong to
 */
class AutoRevokeManifestExemptPackagesLiveData(
    private val app: Application,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<Set<String>>(), PackageBroadcastReceiver.PackageBroadcastListener {
    override fun onPackageUpdate(packageName: String) {
        updateAsync()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        postValue(getUserContext(app, user)
                .getSystemService<PermissionManager>(PermissionManager::class.java)!!
                .autoRevokeExemptionGrantedPackages)
    }

    override fun onActive() {
        super.onActive()

        PackageBroadcastReceiver.addAllCallback(this)

        updateAsync()
    }

    override fun onInactive() {
        super.onInactive()

        PackageBroadcastReceiver.addAllCallback(this)
    }

    /**
     * Repository for AutoRevokeExemptionGrantedPackages
     *
     * <p> Key value is the user, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<UserHandle,
            AutoRevokeManifestExemptPackagesLiveData>() {
        override fun newValue(key: UserHandle): AutoRevokeManifestExemptPackagesLiveData {
            return AutoRevokeManifestExemptPackagesLiveData(PermissionControllerApplication.get(),
                    key)
        }
    }
}