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
import com.android.packageinstaller.permission.data.AppPermGroupUiInfoLiveData
import com.android.packageinstaller.permission.data.AppPermGroupUiInfoRepository
import com.android.packageinstaller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.packageinstaller.permission.data.PackagePermsAndGroupsRepository
import com.android.packageinstaller.permission.data.SmartUpdateMediatorLiveData
import com.android.packageinstaller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.packageinstaller.permission.ui.Category
import com.android.packageinstaller.permission.utils.KotlinUtils
import com.android.packageinstaller.permission.utils.Utils

/**
 * ViewModel for the AppPermissionsFragment. Has a liveData with the UI information for all
 * permission groups that this package requests runtime permissions from
 *
 * @param app: The current application
 * @param packageName: The name of the package this viewModel is representing
 * @param user: The user of the package this viewModel is representing
 */
class AppPermissionsViewModel(
    app: Application,
    packageName: String,
    user: UserHandle
) : ViewModel() {

    val packagePermGroupsLiveData = PackagePermGroupsLiveData(app, packageName, user)

    /**
     * LiveData whose data is a map of grant category (either allowed or denied) to a list
     * of permission group names that match the key, and two booleans representing if this is a
     * system group, and, if it is allowed in the foreground only.
     */
    inner class PackagePermGroupsLiveData(
        private val app: Application,
        private val packageName: String,
        private val user: UserHandle
    ) : SmartUpdateMediatorLiveData<@JvmSuppressWildcards
    Map<Category, List<Triple<String, Boolean, Boolean>>>>() {

        private val packagePermsLiveData =
            PackagePermsAndGroupsRepository.getSinglePermGroupPackagesUiInfoLiveData(app,
                packageName, user)
        private val appPermGroupUiInfoLiveDatas = mutableMapOf<String, AppPermGroupUiInfoLiveData>()

        init {
            addSource(packagePermsLiveData) {
                update()
            }
            update()
        }

        private fun update() {
            val groups = packagePermsLiveData.value?.keys?.filter { it != NON_RUNTIME_NORMAL_PERMS }
            if (groups == null && packagePermsLiveData.isInitialized) {
                value = null
                return
            } else if (groups == null) {
                return
            }

            addAndRemoveAppPermGroupLiveDatas(groups)

            if (!appPermGroupUiInfoLiveDatas.all { it.value.isInitialized }) {
                return
            }

            val groupGrantStates = mutableMapOf<Category,
                MutableList<Triple<String, Boolean, Boolean>>>()
            groupGrantStates[Category.ALLOWED] = mutableListOf()
            groupGrantStates[Category.DENIED] = mutableListOf()

            for (groupName in groups) {
                val isSystem = Utils.getPlatformPermissionGroups().contains(groupName)
                appPermGroupUiInfoLiveDatas[groupName]?.value?.let { uiInfo ->
                    when (uiInfo.isGranted) {
                        PermGrantState.PERMS_ALLOWED -> groupGrantStates[Category.ALLOWED]!!.add(
                                Triple(groupName, isSystem, false))
                        PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY -> groupGrantStates[
                            Category.ALLOWED]!!.add(Triple(groupName, isSystem, true))
                        PermGrantState.PERMS_DENIED -> groupGrantStates[Category.DENIED]!!.add(
                                Triple(groupName, isSystem, false))
                    }
                }
            }

            value = groupGrantStates
        }

        private fun addAndRemoveAppPermGroupLiveDatas(groupNames: List<String>) {
            val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(groupNames,
                appPermGroupUiInfoLiveDatas)

            for (groupToAdd in toAdd) {
                val appPermGroupUiInfoLiveData =
                    AppPermGroupUiInfoRepository.getAppPermGroupUiInfoLiveData(app, packageName,
                        groupToAdd, user)
                appPermGroupUiInfoLiveDatas[groupToAdd] = appPermGroupUiInfoLiveData
            }

            for (groupToAdd in toAdd) {
                addSource(appPermGroupUiInfoLiveDatas[groupToAdd]!!) {
                    update()
                }
            }

            for (groupToRemove in toRemove) {
                removeSource(appPermGroupUiInfoLiveDatas[groupToRemove]!!)
                appPermGroupUiInfoLiveDatas.remove(groupToRemove)
            }
        }
    }
}

/**
 * Factory for an AppPermissionsViewModel
 *
 * @param app: The current application
 * @param packageName: The name of the package this viewModel is representing
 * @param user: The user of the package this viewModel is representing
 */
class AppPermissionsViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppPermissionsViewModel(app, packageName, user) as T
    }
}
