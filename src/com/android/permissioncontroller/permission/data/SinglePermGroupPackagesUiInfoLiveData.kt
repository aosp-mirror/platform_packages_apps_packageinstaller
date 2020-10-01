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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.utils.Utils

/**
 * LiveData for the UI info for all packages in a single permission group. Tracks which packages
 * have permissions in the given group, which should be shown on the UI, and which are granted or
 * not.
 *
 * @param app The current application
 * @param permGroupName The name of the permission group this LiveData represents
 */
class SinglePermGroupPackagesUiInfoLiveData private constructor(
    private val app: Application,
    private val permGroupName: String
) : SmartUpdateMediatorLiveData<Map<Pair<String, UserHandle>, AppPermGroupUiInfo>>() {

    private val permGroupLiveData = PermGroupLiveData[permGroupName]
    private val isCustomGroup = !Utils.getPlatformPermissionGroups().contains(permGroupName)
    private val permGroupPackagesLiveData = PermGroupsPackagesLiveData.get(
        customGroups = isCustomGroup)

    /**
     * Map<Pair<package name, UserHandle>, UI data LiveData>
     */
    private val appPermGroupLiveDatas = mutableMapOf<Pair<String, UserHandle>,
        AppPermGroupUiInfoLiveData>()

    /**
     * Map<Pair<packageName, userHandle>, UI data>.
     */
    private val shownPackages = mutableMapOf<Pair<String, UserHandle>, AppPermGroupUiInfo>()

    init {
        addSource(permGroupLiveData) { newPermGroup ->
            if (newPermGroup == null) {
                invalidateSingle(permGroupName)
                value = null
            }
        }

        addSource(permGroupPackagesLiveData) {
            updateIfActive()
        }
    }

    override fun onUpdate() {
        val thisPermGroupPackages = permGroupPackagesLiveData.value?.get(permGroupName)
        if (thisPermGroupPackages != null) {
            addAndRemoveAppPermGroupLiveDatas(thisPermGroupPackages.toList())

            if (thisPermGroupPackages.isEmpty()) {
                permGroupLiveData.value?.groupInfo?.let {
                    value = emptyMap()
                }
            }
        }
    }

    private fun addAndRemoveAppPermGroupLiveDatas(pkgs: List<Pair<String, UserHandle>>) {
        val getLiveData = { key: Pair<String, UserHandle> ->
            AppPermGroupUiInfoLiveData[key.first, permGroupName, key.second]
        }

        setSourcesToDifference(pkgs, appPermGroupLiveDatas, getLiveData) { key ->
            val appPermGroupUiInfoLiveData = appPermGroupLiveDatas[key]
            val appPermGroupUiInfo = appPermGroupUiInfoLiveData?.value
            shownPackages.remove(key)

            if (appPermGroupUiInfo == null) {
                if (appPermGroupUiInfoLiveData != null &&
                    appPermGroupUiInfoLiveData.isInitialized) {
                    removeSource(appPermGroupUiInfoLiveData)
                    appPermGroupLiveDatas.remove(key)
                }
            } else {
                shownPackages[key] = appPermGroupUiInfo
            }

            if (appPermGroupLiveDatas.all { entry -> entry.value.isInitialized }) {
                permGroupLiveData.value?.groupInfo?.let {
                    value = shownPackages.toMap()
                }
            }
        }
    }

    /**
     * Repository for SinglePermGroupPackagesUiInfoLiveData objects.
     * <p> Key value is a string permission group name, value is its corresponding LiveData.
     */
    companion object : DataRepository<String,
        SinglePermGroupPackagesUiInfoLiveData>() {
        override fun newValue(key: String): SinglePermGroupPackagesUiInfoLiveData {
            return SinglePermGroupPackagesUiInfoLiveData(PermissionControllerApplication.get(),
                key)
        }
    }
}