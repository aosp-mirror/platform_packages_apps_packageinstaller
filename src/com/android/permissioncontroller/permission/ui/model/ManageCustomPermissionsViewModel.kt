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

package com.android.permissioncontroller.permission.ui.model

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.PermGroupsPackagesLiveData
import com.android.permissioncontroller.permission.data.PermGroupsPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.utils.navigateSafe

/**
 * A ViewModel for the ManageCustomPermissionsFragment. Provides a LiveData which watches over all
 * custom permission groups, and sends async updates when these groups have changes.
 *
 * @param app The current application of the fragment
 */
class ManageCustomPermissionsViewModel(
    private val app: Application
) : AndroidViewModel(app) {

    val uiDataLiveData = PermGroupsPackagesUiInfoLiveData(app,
        UsedCustomPermGroupNamesLiveData())

    /**
     * Navigate to a Permission Apps fragment
     *
     * @param fragment The fragment we are navigating from
     * @param args The args to pass to the new fragment
     */
    fun showPermissionApps(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigateSafe(R.id.manage_to_perm_apps, args)
    }
}

/**
 * Factory for a ManageCustomPermissionsViewModel
 *
 * @param app The current application of the fragment
 */
class ManageCustomPermissionsViewModelFactory(
    private val app: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ManageCustomPermissionsViewModel(app) as T
    }
}

/**
 * A LiveData which tracks the names of Custom Permission Groups which are used by at least one
 * package. This includes single-permission permission groups, as well as the Undefined permission
 * group, and any other permission groups not defined by the system.
 */
class UsedCustomPermGroupNamesLiveData :
    SmartUpdateMediatorLiveData<List<String>>() {

    init {
        addSource(PermGroupsPackagesLiveData.get(customGroups = true)) {
            value = it.keys.toList()
        }
    }

    override fun onUpdate() { /* No op override */ }
}
