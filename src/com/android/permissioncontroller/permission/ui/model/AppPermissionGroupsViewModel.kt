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

import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
import android.Manifest
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_DISABLED
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_ENABLED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.AutoRevokeStateLiveData
import com.android.permissioncontroller.permission.data.FullStoragePermissionAppsLiveData
import com.android.permissioncontroller.permission.data.LightPackageInfoLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.ui.Category
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.utils.navigateSafe
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * ViewModel for the AppPermissionGroupsFragment. Has a liveData with the UI information for all
 * permission groups that this package requests runtime permissions from
 *
 * @param packageName The name of the package this viewModel is representing
 * @param user The user of the package this viewModel is representing
 */
class AppPermissionGroupsViewModel(
    private val packageName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModel() {

    companion object {
        val LOG_TAG: String = AppPermissionGroupsViewModel::class.java.simpleName
    }

    val app = PermissionControllerApplication.get()!!

    enum class PermSubtitle(val value: Int) {
        NONE(0),
        MEDIA_ONLY(1),
        ALL_FILES(2),
        FOREGROUND_ONLY(3),
    }

    data class GroupUiInfo(
        val groupName: String,
        val isSystem: Boolean = false,
        val subtitle: PermSubtitle
    ) {
        constructor(groupName: String, isSystem: Boolean) :
            this(groupName, isSystem, PermSubtitle.NONE)
    }

    val autoRevokeLiveData = AutoRevokeStateLiveData[packageName, user]

    /**
     * LiveData whose data is a map of grant category (either allowed or denied) to a list
     * of permission group names that match the key, and two booleans representing if this is a
     * system group, and a subtitle resource ID, if applicable.
     */
    val packagePermGroupsLiveData = object : SmartUpdateMediatorLiveData<@JvmSuppressWildcards
    Map<Category, List<GroupUiInfo>>>() {

        private val packagePermsLiveData =
            PackagePermissionsLiveData[packageName, user]
        private val appPermGroupUiInfoLiveDatas = mutableMapOf<String, AppPermGroupUiInfoLiveData>()
        private val fullStoragePermsLiveData = FullStoragePermissionAppsLiveData

        init {
            addSource(packagePermsLiveData) {
                update()
            }
            addSource(fullStoragePermsLiveData) {
                update()
            }
            addSource(autoRevokeLiveData) {
                removeSource(autoRevokeLiveData)
                update()
            }
            update()
        }

        override fun onUpdate() {
            val groups = packagePermsLiveData.value?.keys?.filter { it != NON_RUNTIME_NORMAL_PERMS }
            if (groups == null && packagePermsLiveData.isInitialized) {
                value = null
                return
            } else if (groups == null || (Manifest.permission_group.STORAGE in groups &&
                    !fullStoragePermsLiveData.isInitialized) || !autoRevokeLiveData.isInitialized) {
                return
            }

            val getLiveData = { groupName: String ->
                AppPermGroupUiInfoLiveData[packageName, groupName, user]
            }
            setSourcesToDifference(groups, appPermGroupUiInfoLiveDatas, getLiveData)

            if (!appPermGroupUiInfoLiveDatas.all { it.value.isInitialized }) {
                return
            }

            val groupGrantStates = mutableMapOf<Category,
                MutableList<GroupUiInfo>>()
            groupGrantStates[Category.ALLOWED] = mutableListOf()
            groupGrantStates[Category.ASK] = mutableListOf()
            groupGrantStates[Category.DENIED] = mutableListOf()

            val fullStorageState = fullStoragePermsLiveData.value?.find { pkg ->
                pkg.packageName == packageName && pkg.user == user
            }

            for (groupName in groups) {
                val isSystem = Utils.getPlatformPermissionGroups().contains(groupName)
                appPermGroupUiInfoLiveDatas[groupName]?.value?.let { uiInfo ->
                    if (groupName == Manifest.permission_group.STORAGE &&
                        (fullStorageState?.isGranted == true && !fullStorageState.isLegacy)) {
                        groupGrantStates[Category.ALLOWED]!!.add(
                            GroupUiInfo(groupName, isSystem, PermSubtitle.ALL_FILES))
                        return@let
                    }
                    when (uiInfo.permGrantState) {
                        PermGrantState.PERMS_ALLOWED -> {
                            val subtitle = if (groupName == Manifest.permission_group.STORAGE) {
                                if (fullStorageState?.isLegacy == true) {
                                    PermSubtitle.ALL_FILES
                                } else {
                                    PermSubtitle.MEDIA_ONLY
                                }
                            } else {
                                PermSubtitle.NONE
                            }
                            groupGrantStates[Category.ALLOWED]!!.add(
                                GroupUiInfo(groupName, isSystem, subtitle))
                        }
                        PermGrantState.PERMS_ALLOWED_ALWAYS -> groupGrantStates[
                            Category.ALLOWED]!!.add(GroupUiInfo(groupName, isSystem))
                        PermGrantState.PERMS_ALLOWED_FOREGROUND_ONLY -> groupGrantStates[
                            Category.ALLOWED]!!.add(GroupUiInfo(groupName, isSystem,
                            PermSubtitle.FOREGROUND_ONLY))
                        PermGrantState.PERMS_DENIED -> groupGrantStates[Category.DENIED]!!.add(
                            GroupUiInfo(groupName, isSystem))
                        PermGrantState.PERMS_ASK -> groupGrantStates[Category.ASK]!!.add(
                            GroupUiInfo(groupName, isSystem))
                    }
                }
            }

            value = groupGrantStates
        }
    }

    fun setAutoRevoke(enabled: Boolean) {
        GlobalScope.launch(IPC) {
            val aom = app.getSystemService(AppOpsManager::class.java)!!
            val uid = LightPackageInfoLiveData[packageName, user].getInitializedValue()?.uid

            if (uid != null) {
                Log.i(LOG_TAG, "sessionId $sessionId setting auto revoke enabled to $enabled for" +
                    "$packageName $user")
                val tag = if (enabled) {
                    APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_ENABLED
                } else {
                    APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__SWITCH_DISABLED
                }
                PermissionControllerStatsLog.write(
                    APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION, sessionId, uid, packageName,
                    tag)

                val mode = if (enabled) {
                    MODE_ALLOWED
                } else {
                    MODE_IGNORED
                }
                aom.setUidMode(OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, uid, mode)
            }
        }
    }

    fun showExtraPerms(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigateSafe(R.id.perm_groups_to_custom, args)
    }

    fun showAllPermissions(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigateSafe(R.id.perm_groups_to_all_perms, args)
    }
}

/**
 * Factory for an AppPermissionGroupsViewModel
 *
 * @param packageName The name of the package this viewModel is representing
 * @param user The user of the package this viewModel is representing
 */
class AppPermissionGroupsViewModelFactory(
    private val packageName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppPermissionGroupsViewModel(packageName, user, sessionId) as T
    }
}
