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

package com.android.permissioncontroller.permission.ui.handheld

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.permission.data.PermGroupPackagesUiInfoRepository
import com.android.permissioncontroller.permission.data.PermGroupsPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.StandardPermGroupNamesLiveData

/**
 * A ViewModel for the ManageStandardPermissionsFragment. Provides a LiveData which watches over all
 * platform permission groups, and sends async updates when these groups have changes. It also
 * provides a liveData which watches the custom permission groups of the system, and provides
 * a list of group names.
 * @param app: The current application of the fragment
 */
class ManageStandardPermissionsViewModel(
    private val app: Application
) : AndroidViewModel(app) {

    val uiDataLiveData = PermGroupsPackagesUiInfoLiveData(app,
        StandardPermGroupNamesLiveData())
    val numCustomPermGroups = NumCustomPermGroupsWithPackagesLiveData(app)
}

/**
 * Factory for a ManageStandardPermissionsViewModel
 *
 * @param app: The current application of the fragment
 */
class ManageStandardPermissionsViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ManageStandardPermissionsViewModel(app) as T
    }
}

/**
 * A LiveData which tracks the number of custom permission groups that are used by at least one
 * package
 *
 * @param app: The current application
 */
class NumCustomPermGroupsWithPackagesLiveData(app: Application) :
    SmartUpdateMediatorLiveData<Int>() {

    private val customPermGroupPackages =
        PermGroupPackagesUiInfoRepository.getAllCustomPermGroupsPackagesLiveData(app)

    init {
        addSource(customPermGroupPackages) {
            update()
        }
    }

    override fun update() {
        value = customPermGroupPackages.value?.size ?: 0
    }
}