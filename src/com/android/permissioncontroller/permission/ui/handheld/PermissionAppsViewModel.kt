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

import android.Manifest
import android.app.Application
import android.os.Bundle
import android.os.UserHandle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData
import com.android.permissioncontroller.permission.data.SinglePermGroupPackagesUiInfoLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsViewModel.Companion.CREATION_LOGGED_KEY
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsViewModel.Companion.HAS_SYSTEM_APPS_KEY
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsViewModel.Companion.SHOULD_SHOW_SYSTEM_KEY
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsViewModel.Companion.SHOW_ALWAYS_ALLOWED

/**
 * ViewModel for the PermissionAppsFragment. Has a liveData with all of the UI info for each
 * package which requests permissions in this permission group, a liveData which tracks whether or
 * not to show system apps, and a liveData tracking whether there are any system apps which request
 * permissions in this group.
 *
 * @param app The current application
 * @param groupName The name of the permission group this viewModel is representing
 */
class PermissionAppsViewModel(
    private val state: SavedStateHandle,
    private val app: Application,
    groupName: String
) : ViewModel() {

    companion object {
        internal const val SHOULD_SHOW_SYSTEM_KEY = "showSystem"
        internal const val HAS_SYSTEM_APPS_KEY = "hasSystem"
        internal const val SHOW_ALWAYS_ALLOWED = "showAlways"
        internal const val CREATION_LOGGED_KEY = "creationLogged"
    }

    val shouldShowSystemLiveData = state.getLiveData(SHOULD_SHOW_SYSTEM_KEY, false)
    val hasSystemAppsLiveData = state.getLiveData(HAS_SYSTEM_APPS_KEY, true)
    val showAllowAlwaysStringLiveData = state.getLiveData(SHOW_ALWAYS_ALLOWED, false)
    val categorizedAppsLiveData = CategorizedAppsLiveData(groupName)

    fun updateShowSystem(showSystem: Boolean) {
        if (showSystem != state.get(SHOULD_SHOW_SYSTEM_KEY)) {
            state.set(SHOULD_SHOW_SYSTEM_KEY, showSystem)
        }
    }

    var creationLogged
        get() = state.get(CREATION_LOGGED_KEY) ?: false
        set(value) = state.set(CREATION_LOGGED_KEY, value)

    inner class CategorizedAppsLiveData(groupName: String)
        : MediatorLiveData<@kotlin.jvm.JvmSuppressWildcards
    Map<Category, List<Pair<String, UserHandle>>>>() {
        private val packagesUiInfoLiveData = SinglePermGroupPackagesUiInfoLiveData[groupName]

        init {
            var fullStorageLiveData: FullStoragePermissionAppsLiveData? = null

            addSource(packagesUiInfoLiveData) {
                if (fullStorageLiveData == null || fullStorageLiveData?.isInitialized == true)
                update()
            }
            addSource(shouldShowSystemLiveData) {
                if (fullStorageLiveData == null || fullStorageLiveData?.isInitialized == true)
                update()
            }

            // If this is the Storage group, observe a FullStoragePermissionAppsLiveData, update
            // the packagesWithFullFileAccess list, and call update to populate the subtitles.
            if (groupName == Manifest.permission_group.STORAGE) {
                fullStorageLiveData = FullStoragePermissionAppsLiveData
                addSource(FullStoragePermissionAppsLiveData) { fullAccessPackages ->
                    if (fullAccessPackages != packagesWithFullFileAccess) {
                        packagesWithFullFileAccess = fullAccessPackages ?: emptyList()
                        if (packagesUiInfoLiveData.isInitialized) {
                            update()
                        }
                    }
                }
            }

            if ((fullStorageLiveData == null || fullStorageLiveData.isInitialized) &&
                packagesUiInfoLiveData.isInitialized) {
                update()
            }
        }

        fun update() {
            val categoryMap = mutableMapOf<Category, MutableList<Pair<String, UserHandle>>>()
            val showSystem: Boolean = state.get(SHOULD_SHOW_SYSTEM_KEY) ?: false

            categoryMap[Category.ALLOWED] = mutableListOf()
            categoryMap[Category.ALLOWED_FOREGROUND] = mutableListOf()
            categoryMap[Category.ASK] = mutableListOf()
            categoryMap[Category.DENIED] = mutableListOf()

            val packageMap = packagesUiInfoLiveData.value ?: run {
                if (packagesUiInfoLiveData.isInitialized) {
                    value = categoryMap
                }
                return
            }

            val hasSystem = packageMap.any { it.value.isSystem && it.value.shouldShow }
            if (hasSystem != state.get(HAS_SYSTEM_APPS_KEY)) {
                state.set(HAS_SYSTEM_APPS_KEY, hasSystem)
            }

            var showAlwaysAllowedString = false

            for ((packageUserPair, uiInfo) in packageMap) {
                if (!uiInfo.shouldShow) {
                    continue
                }

                if (uiInfo.isSystem && !showSystem) {
                    continue
                }

                if (uiInfo.permGrantState == PermGrantState.PERMS_ALLOWED_ALWAYS ||
                    uiInfo.permGrantState == PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY) {
                    showAlwaysAllowedString = true
                }

                val category = when (uiInfo.permGrantState) {
                    PermGrantState.PERMS_ALLOWED -> Category.ALLOWED
                    PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY -> Category.ALLOWED_FOREGROUND
                    PermGrantState.PERMS_ALLOWED_ALWAYS -> Category.ALLOWED
                    PermGrantState.PERMS_DENIED -> Category.DENIED
                    PermGrantState.PERMS_ASK -> Category.ASK
                }
                categoryMap[category]!!.add(packageUserPair)
            }
            showAllowAlwaysStringLiveData.value = showAlwaysAllowedString
            value = categoryMap
        }
    }

    // If this is the storage permission group, some apps have full access to storage, while
    // Others just have access to media files. This list contains the packages with full access
    // To listen for changes, create and observe a FullStoragePermissionAppsLiveData
    private var packagesWithFullFileAccess = listOf<Pair<String, UserHandle>>()

    /**
     * Whether or not to show the "Files and Media" subtitle label for a package, vs. the normal
     * "Media". Requires packagesWithFullFileAccess to be updated in order to work. To do this,
     * create and observe a FullStoragePermissionAppsLiveData.
     *
     * @param packageName The name of the package we want to check
     * @param user The name of the user whose package we want to check
     *
     * @return true if the package and user should show the full files subtitle
     */
    fun shouldUseFullStorageString(packageName: String, user: UserHandle): Boolean {
        return (packageName to user) in packagesWithFullFileAccess
    }

    /**
     * Whether or not packages have been loaded from the system.
     * To update, need to observe the allPackageInfosLiveData.
     *
     * @return Whether or not all packages have been loaded
     */
    fun arePackagesLoaded(): Boolean {
        return AllPackageInfosLiveData.isInitialized
    }
}

/**
 * Factory for a PermissionAppsViewModel
 *
 * @param app The current application of the fragment
 * @param groupName The name of the permission group this viewModel is representing
 * @param owner The owner of this saved state
 * @param defaultArgs The default args to pass
 */
class PermissionAppsViewModelFactory(
    private val app: Application,
    private val groupName: String,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel?> create(p0: String, p1: Class<T>, state: SavedStateHandle): T {
        state.set(SHOULD_SHOW_SYSTEM_KEY, state.get<Boolean>(SHOULD_SHOW_SYSTEM_KEY) ?: false)
        state.set(HAS_SYSTEM_APPS_KEY, state.get<Boolean>(HAS_SYSTEM_APPS_KEY) ?: true)
        state.set(SHOW_ALWAYS_ALLOWED, state.get<Boolean>(SHOW_ALWAYS_ALLOWED) ?: false)
        state.set(CREATION_LOGGED_KEY, state.get<Boolean>(CREATION_LOGGED_KEY) ?: false)
        @Suppress("UNCHECKED_CAST")
        return PermissionAppsViewModel(state, app, groupName) as T
    }
}