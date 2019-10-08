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

package com.android.packageinstaller.permission.data

import android.app.Application
import androidx.lifecycle.LiveData
import com.android.packageinstaller.permission.model.livedatatypes.PermGroupPackagesUiInfo
import com.android.packageinstaller.permission.utils.KotlinUtils

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
    @kotlin.jvm.JvmSuppressWildcards Map<String, PermGroupPackagesUiInfo>>() {

    private data class PermGroupUiLiveDatas(
        val packagesLiveData: SinglePermGroupPackagesUiInfoLiveData,
        val iconLiveData: IconLiveData<*>,
        val labelLiveData: LabelLiveData<*>
    )
    /**
     * Map<permission group name, PermGroupUiLiveDatas>
     */
    private val permGroupUiLiveDatas = mutableMapOf<String, PermGroupUiLiveDatas>()
    private val allPackageData = mutableMapOf<String, PermGroupPackagesUiInfo>()

    private lateinit var groupNames: List<String>

    init {
        addSource(groupNamesLiveData) {
            groupNames = it ?: emptyList()
            update()
        }
    }

    private fun update() {
        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(groupNames,
            permGroupUiLiveDatas)

        for (groupToRemove in toRemove) {
            with(permGroupUiLiveDatas[groupToRemove]!!) {
                removeSource(packagesLiveData)
                removeSource(iconLiveData)
                removeSource(labelLiveData)
            }
            permGroupUiLiveDatas.remove(groupToRemove)
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
            if (!permGroupUiLiveDatas.containsKey(groupName)) {
                groupsAdded.add(groupName)

                val singlePermGroupPackagesUiInfoLiveData =
                    PermGroupPackagesUiInfoRepository.getSinglePermGroupPackagesUiInfoLiveData(app,
                        groupName)
                val iconLiveData = IconRepository.getPermGroupIconLiveData(app, groupName)
                val labelLiveData = LabelRepository.getPermGroupLabelLiveData(app, groupName)
                val uiLiveDatas = PermGroupUiLiveDatas(singlePermGroupPackagesUiInfoLiveData,
                    iconLiveData, labelLiveData)
                permGroupUiLiveDatas[groupName] = uiLiveDatas
            }
        }

        for (groupName in groupsAdded) {
            with(permGroupUiLiveDatas[groupName]!!) {
                addSource(iconLiveData) {
                    checkForLabelOrIconUpdate(groupName)
                }
                addSource(labelLiveData) {
                    checkForLabelOrIconUpdate(groupName)
                }
                addSource(packagesLiveData) { packagesUiInfo ->
                    // If we have already initialized the label and icon for this group, set its
                    // package data
                    allPackageData[groupName]?.let { currUiInfo ->
                        allPackageData[groupName] = PermGroupPackagesUiInfo(currUiInfo.name,
                            packagesUiInfo, currUiInfo.label, currUiInfo.icon)
                    }
                    checkShouldUpdate()
                }
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

    private fun checkForLabelOrIconUpdate(groupName: String) {
        permGroupUiLiveDatas[groupName]?.labelLiveData?.value?.let { label ->
            permGroupUiLiveDatas[groupName]?.iconLiveData?.value?.let { icon ->
                val currPackages = allPackageData[groupName]?.packages
                allPackageData[groupName] = PermGroupPackagesUiInfo(groupName, currPackages, label,
                    icon)
                checkShouldUpdate()
            }
        }
    }

    private fun checkShouldUpdate() {
        /**
         * Only update when either-
         * All packages have loaded their icons and labels, and none have loaded their data, or
         * All packages have loaded their data
         */
        if (permGroupUiLiveDatas.all { allPackageData.containsKey(it.key) &&
                allPackageData[it.key]?.packages == null } ||
            permGroupUiLiveDatas.all { allPackageData[it.key]?.packages != null }) {
            value = allPackageData.toMap()
        }
    }
}
