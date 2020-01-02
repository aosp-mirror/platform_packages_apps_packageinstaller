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
import androidx.lifecycle.LiveData
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * A LiveData which tracks all app permission groups for a set of permission groups, either platform
 * or custom, as well as the UI information related to each app permission group, and the permission
 * group as a whole.
 *
 * @param app: The current application
 */
class PermGroupsPackagesUiInfoLiveData(
    private val app: Application,
    groupNamesLiveData: LiveData<List<String>>
) : SmartUpdateMediatorLiveData<
    @kotlin.jvm.JvmSuppressWildcards Map<String, PermGroupPackagesUiInfo?>>() {

    /**
     * Map<permission group name, PermGroupUiLiveDatas>
     */
    private val permGroupPackagesLiveDatas = mutableMapOf<String,
        SinglePermGroupPackagesUiInfoLiveData>()
    private val allPackageData = mutableMapOf<String, PermGroupPackagesUiInfo?>()

    private lateinit var groupNames: List<String>

    init {
        addSource(groupNamesLiveData) {
            groupNames = it ?: emptyList()
            update()
        }
    }

    override fun update() {
        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(groupNames,
            permGroupPackagesLiveDatas)

        for (groupToRemove in toRemove) {
            permGroupPackagesLiveDatas[groupToRemove]?.let {
                removeSource(it)
            }
            permGroupPackagesLiveDatas.remove(groupToRemove)
            allPackageData.remove(groupToRemove)
        }

        addPermGroupPackagesUiInfoLiveDatas(toAdd)

        value = allPackageData.toMap()
    }

    /**
     * From a list of permission group names, generates permGroupPackagesUiInfoLiveDatas, and
     * adds them as sources. Will not re-add already watched LiveDatas.
     *
     * @param groupNames The list of group names whose LiveDatas we want to add
     */
    private fun addPermGroupPackagesUiInfoLiveDatas(
        groupNames: Collection<String>
    ) {
        val groupsAdded = mutableListOf<String>()
        for (groupName in groupNames) {
            if (!permGroupPackagesLiveDatas.containsKey(groupName)) {
                groupsAdded.add(groupName)

                val singlePermGroupPackagesUiInfoLiveData =
                    PermGroupPackagesUiInfoRepository.getSinglePermGroupPackagesUiInfoLiveData(app,
                        groupName)
                permGroupPackagesLiveDatas[groupName] = singlePermGroupPackagesUiInfoLiveData
            }
        }

        for (groupName in groupsAdded) {
            allPackageData[groupName] = null
            addSource(permGroupPackagesLiveDatas[groupName]!!) { uiInfo ->
                if (uiInfo == null) {
                    allPackageData[groupName] = null
                } else {
                    allPackageData[groupName] = PermGroupPackagesUiInfo(groupName,
                        getNonSystemTotal(uiInfo), getNonSystemGranted(uiInfo))
                }

                checkShouldUpdate()
            }
        }

        /**
         * If, for whatever reason, we did not update the map when adding sources above (if we
         * somehow got final data before all groups had icons and labels, or a similar situation),
         * then update with the current data.
         */
        if (value == null) {
            value = allPackageData.toMap()
        }
    }

    private fun getNonSystemTotal(uiInfo: Map<Pair<String, UserHandle>, AppPermGroupUiInfo>): Int {
        var shownNonSystem = 0
        for ((_, appPermGroup) in uiInfo) {
            if (appPermGroup.shouldShow && !appPermGroup.isSystem) {
                shownNonSystem++
            }
        }
        return shownNonSystem
    }

    private fun getNonSystemGranted(
        uiInfo: Map<Pair<String, UserHandle>, AppPermGroupUiInfo>
    ): Int {
        var granted = 0
        for ((_, appPermGroup) in uiInfo) {
            if (appPermGroup.shouldShow && !appPermGroup.isSystem &&
                appPermGroup.permGrantState != AppPermGroupUiInfo.PermGrantState.PERMS_DENIED) {
                granted++
            }
        }
        return granted
    }

    private fun checkShouldUpdate() {
        /**
         * Only update when either-
         * We have a list of groups, and none have loaded their data, or
         * All packages have loaded their data
         */
        if (groupNames.all { allPackageData.containsKey(it) && allPackageData[it] == null } ||
            groupNames.all { allPackageData[it] != null }) {
            value = allPackageData.toMap()
        }
    }
}
