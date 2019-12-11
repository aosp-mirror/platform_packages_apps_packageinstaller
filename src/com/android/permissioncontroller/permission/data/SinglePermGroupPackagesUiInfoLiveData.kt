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
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils

/**
 * LiveData for the UI info for all packages in a single permission group. Tracks which packages
 * have permissions in the given group, which should be shown on the UI, and which are granted or
 * not.
 *
 * @param app: The current application
 * @param permissionGroupName: The name of the permission group this LiveData represents
 */
class SinglePermGroupPackagesUiInfoLiveData(
    private val app: Application,
    private val permissionGroupName: String
) : SmartUpdateMediatorLiveData<Map<Pair<String, UserHandle>, AppPermGroupUiInfo>>() {

    private val permGroupLiveData =
        PermGroupRepository.getPermGroupLiveData(app, permissionGroupName)
    private val permGroupPackagesLiveData = if (
        Utils.getPlatformPermissionGroups().contains(permissionGroupName)) {
        PermGroupPackagesUiInfoRepository.getAllStandardPermGroupsPackagesLiveData(app)
    } else {
        PermGroupPackagesUiInfoRepository.getAllCustomPermGroupsPackagesLiveData(app)
    }

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
                value = null
            }
        }

        addSource(permGroupPackagesLiveData) {
            update()
        }
    }

    override fun update() {
        val thisPermGroupPackages = permGroupPackagesLiveData.value?.get(permissionGroupName)
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
        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(pkgs,
            appPermGroupLiveDatas)

        for (packageUserPair in toRemove) {
            appPermGroupLiveDatas[packageUserPair]?.let { removeSource(it) }
        }

        for ((packageName, userHandle) in toAdd) {
            val key = packageName to userHandle
            val appPermGroupLiveData =
                AppPermGroupUiInfoRepository.getAppPermGroupUiInfoLiveData(app, packageName,
                    permissionGroupName, userHandle)
            appPermGroupLiveDatas[key] = appPermGroupLiveData
        }

        for (key in toAdd) {
            val appPermGroupLiveData = appPermGroupLiveDatas[key]!!
            addSource(appPermGroupLiveData) { appPermGroupUiInfo ->
                shownPackages.remove(key)

                if (appPermGroupUiInfo == null) {
                    val appPermGroupUiInfoLiveData = appPermGroupLiveDatas[key]
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
            appPermGroupLiveDatas[key] = appPermGroupLiveData
        }
    }
}

/**
 * Repository for SinglePermGroupPackagesUiInfoLiveData objects, as well as the
 * AllStandardPermGroupsPackagesUiInfoLiveData and AllCustomPermGroupsPackagesUiInfoLiveData.
 * <p> Key value is a string permission group name, value is its corresponding LiveData.
 */
object PermGroupPackagesUiInfoRepository
    : DataRepository<String, SinglePermGroupPackagesUiInfoLiveData>() {

    /**
     * Get the SinglePermGroupPackagesUiInfo associated with the given parameters, creating it if
     * need be.
     *
     * @param app: The current application
     * @param groupName: The name of the permission group desired
     *
     * @return the cached or newly generated SinglePermGroupPackagesUiInfoLiveData
     */
    fun getSinglePermGroupPackagesUiInfoLiveData(app: Application, groupName: String):
        SinglePermGroupPackagesUiInfoLiveData {
        return getDataObject(app, groupName)
    }

    override fun newValue(app: Application, key: String):
        SinglePermGroupPackagesUiInfoLiveData {
        return SinglePermGroupPackagesUiInfoLiveData(app, key)
    }

    private var allStandardPermGroupPackagesLiveData: PermGroupsPackagesLiveData? = null
    private var allCustomPermGroupPackagesLiveData: PermGroupsPackagesLiveData? = null

    /**
     * Get our AllCustomPermGroupsPackagesLiveData, creating it if need be.
     *
     * @param app: The current application
     *
     * @return The cached or created PermGroupsPackagesLiveData
     */
    fun getAllCustomPermGroupsPackagesLiveData(app: Application):
        PermGroupsPackagesLiveData {
        return allCustomPermGroupPackagesLiveData ?: run {
            val liveData = PermGroupsPackagesLiveData(app,
                PermGroupRepository.getCustomPermGroupNamesLiveData(app))
            allCustomPermGroupPackagesLiveData = liveData
            liveData
        }
    }

    /**
     * Get our AllStandardPermGroupsPackagesLiveData, creating it if need be.
     *
     * @param app: The current application
     *
     * @return The cached or created PermGroupsPackagesLiveData
     */
    fun getAllStandardPermGroupsPackagesLiveData(app: Application):
        PermGroupsPackagesLiveData {
        return allStandardPermGroupPackagesLiveData ?: run {
            val liveData = PermGroupsPackagesLiveData(app, StandardPermGroupNamesLiveData())
            allStandardPermGroupPackagesLiveData = liveData
            liveData
        }
    }
}
