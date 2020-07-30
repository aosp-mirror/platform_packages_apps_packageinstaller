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

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.os.UserHandle
import android.view.accessibility.AccessibilityManager
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.componentInfo
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks enabled accessibility service packages
 *
 * @param app The current application
 * @param user The user the services should be determined for
 */
// TODO(eugenesusla): think of a way to observe the data
class EnabledAccessibilityServicesLiveData(
    private val app: Application,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<List<String>>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val packageNames = Utils.getUserContext(app, user)
                .getSystemService(AccessibilityManager::class.java)!!
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .map { info: AccessibilityServiceInfo ->
                    info.resolveInfo.componentInfo.packageName
                }

        postValue(packageNames)
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }

    /**
     * Repository for [EnabledAccessibilityServicesLiveData]
     *
     * <p> Key value is a user, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<UserHandle,
            EnabledAccessibilityServicesLiveData>() {
        override fun newValue(key: UserHandle): EnabledAccessibilityServicesLiveData {
            return EnabledAccessibilityServicesLiveData(PermissionControllerApplication.get(), key)
        }
    }
}