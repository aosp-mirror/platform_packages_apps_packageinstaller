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
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks enabled input method packages
 *
 * @param app The current application
 * @param user The user the services should be determined for
 */
// TODO(eugenesusla): think of ways to observe the data
class EnabledInputMethodsLiveData(
    private val app: Application,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<List<String>>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val packageNames = Utils.getUserContext(app, user)
                .getSystemService(InputMethodManager::class.java)!!
                .enabledInputMethodList
                .map { info: InputMethodInfo ->
                    info.component.packageName
                }

        postValue(packageNames)
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }

    /**
     * Repository for [EnabledInputMethodsLiveData]
     *
     * <p> Key value is a user, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<UserHandle,
            EnabledInputMethodsLiveData>() {
        override fun newValue(key: UserHandle): EnabledInputMethodsLiveData {
            return EnabledInputMethodsLiveData(PermissionControllerApplication.get(), key)
        }
    }
}