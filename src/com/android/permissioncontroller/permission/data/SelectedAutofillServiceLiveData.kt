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
import android.view.autofill.AutofillManager
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks selected autofill service package
 *
 * @param app The current application
 * @param user The user the services should be determined for
 */
class SelectedAutofillServiceLiveData(
    private val app: Application,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<String?>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val packageName = Utils.getUserContext(app, user)
            .getSystemService(AutofillManager::class.java)
            ?.autofillServiceComponentName
            ?.packageName

        postValue(packageName)
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }

    /**
     * Repository for [SelectedAutofillServiceLiveData]
     *
     * <p> Key value is a user, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<UserHandle,
            SelectedAutofillServiceLiveData>() {
        override fun newValue(key: UserHandle): SelectedAutofillServiceLiveData {
            return SelectedAutofillServiceLiveData(PermissionControllerApplication.get(), key)
        }
    }
}