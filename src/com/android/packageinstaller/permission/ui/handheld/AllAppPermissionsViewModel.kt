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

package com.android.packageinstaller.permission.ui.handheld

import android.app.Application
import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.packageinstaller.permission.data.PackagePermsAndGroupsRepository
import com.android.packageinstaller.permission.data.SmartUpdateMediatorLiveData

/**
 * ViewModel for the AllAppPermissionsFragment. Has a liveData with the UI information for all
 * Permissions (organized by group) that this package requests, and all the installed, non-runtime,
 * normal protection permissions as well.
 *
 * @param app: The current application
 * @param packageName: The name of the package this viewModel is representing
 * @param user: The user of the package this viewModel is representing
 * @param filterGroup: An optional single group that should be shown, no other groups will be
 * shown
 */
class AllAppPermissionsViewModel(
    app: Application,
    packageName: String,
    user: UserHandle,
    filterGroup: String?
) : ViewModel() {

    val allPackagePermissionsLiveData = AllPackagePermissionsLiveData(app, packageName, user,
        filterGroup)

    class AllPackagePermissionsLiveData(
        app: Application,
        packageName: String,
        user: UserHandle,
        private val filterGroup: String?
    ) : SmartUpdateMediatorLiveData<@kotlin.jvm.JvmSuppressWildcards
    Map<String, List<String>>>() {

        private val packagePermsLiveData =
            PackagePermsAndGroupsRepository.getSinglePermGroupPackagesUiInfoLiveData(app,
                packageName, user)

        init {
            addSource(packagePermsLiveData) {
                update()
            }
            update()
        }

        private fun update() {
            val permissions = packagePermsLiveData.value
            if (permissions == null) {
                value = null
                return
            }
            value = permissions.filter { filterGroup == null || it.key == filterGroup }
        }
    }
}

/**
 * Factory for an AllAppPermissionsViewModel.
 *
 * @param app: The current application
 * @param packageName: The name of the package this viewModel is representing
 * @param user: The user of the package this viewModel is representing
 * @param filterGroup: An optional single group that should be shown, no other groups will be
 * shown
 */
class AllAppPermissionsViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle,
    private val filterGroup: String?
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AllAppPermissionsViewModel(app, packageName, user, filterGroup) as T
    }
}
