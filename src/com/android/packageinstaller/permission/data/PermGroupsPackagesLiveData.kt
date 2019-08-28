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

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.UserHandle
import androidx.lifecycle.LiveData
import com.android.packageinstaller.permission.model.livedatatypes.PermGroup
import com.android.packageinstaller.permission.utils.KotlinUtils
import com.android.packageinstaller.permission.utils.Utils.OS_PKG

/**
 * A LiveData which tracks either all platform permission groups, or all custom permission groups,
 * and the packages which contain runtime permissions in one of those group.
 *
 * @param app: The current application
 */
class PermGroupsPackagesLiveData(
    private val app: Application,
    groupNamesLiveData: LiveData<List<String>>
) : SmartUpdateMediatorLiveData<Map<String, Set<Pair<String, UserHandle>>>>() {

    private val packagesLiveData = UserPackageInfosRepository.getAllPackageInfosLiveData(app)
    private val permGroupLiveDatas = mutableMapOf<String, PermGroupLiveData>()

    private var groupNames = emptyList<String>()

    init {
        addSource(groupNamesLiveData) {
            groupNames = it ?: emptyList()
            addPermissionGroups()
        }

        addSource(packagesLiveData) {
            if (permGroupLiveDatas.all { it.value.isInitialized }) {
                update()
            }
        }
    }

    /**
     * Called after updating groupNames to add and remove PermGroupLiveDatas to match the new
     * value of groupNames.
     */
    private fun addPermissionGroups() {
        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(groupNames, permGroupLiveDatas)
        for (groupToRemove in toRemove) {
            permGroupLiveDatas[groupToRemove]?.let { permGroupLiveData ->
                permGroupLiveDatas.remove(groupToRemove)
                removeSource(permGroupLiveData)
            }
        }

        // We add all groups to our map first, so that the check if all are initialized can see
        // all of the LiveDatas.
        for (groupToAdd in toAdd) {
            permGroupLiveDatas[groupToAdd] =
                PermGroupRepository.getPermGroupLiveData(app, groupToAdd)
        }

        for (groupToAdd in toAdd) {
            addSource(permGroupLiveDatas[groupToAdd]!!) {
                if (packagesLiveData.isInitialized &&
                    permGroupLiveDatas.all { it.value.isInitialized }) {
                    update()
                }
            }
        }
    }

    /**
     * Using the current list of permission groups, go through all packages in the system,
     * and figure out which permission groups they have permissions for. If applicable, remove
     * any lone-permission permission that are not requested by any packages.
     */
    private fun update() {
        if (groupNames.isEmpty()) {
            return
        }

        val groupApps = mutableMapOf<String, MutableSet<Pair<String, UserHandle>>>()
        val permGroups = mutableListOf<PermGroup>()
        for (groupName in groupNames) {
            val permGroup = permGroupLiveDatas[groupName]?.value
            if (permGroup == null || !permGroup.hasRuntimePermissions) {
                continue
            }
            permGroups.add(permGroup)
            groupApps[groupName] = mutableSetOf()
        }

        val allPackages = packagesLiveData.value ?: return
        for ((userHandle, packageInfos) in allPackages) {
            for (packageInfo in packageInfos) {
                val isPreMApp = packageInfo.targetSdkVersion < Build.VERSION_CODES.M

                for ((groupInfo, permissionInfos) in permGroups) {
                    // Do not allow toggling non-platform permission groups for legacy apps via app
                    // ops.
                    if (isPreMApp && groupInfo.packageName != OS_PKG) {
                        continue
                    }
                    // Categorize all requested permissions of this package
                    for (permissionName in packageInfo.requestedPermissions) {
                        if (permissionInfos.containsKey(permissionName)) {
                            groupApps[groupInfo.name]?.add(packageInfo.packageName to userHandle)
                        }
                    }
                }
            }
        }

        /*
         * Remove any lone permission groups that are not used by any package, and the UNDEFINED
         * group, if also empty.
         */
        for (permGroup in permGroups) {
            if (permGroup.groupInfo.isSinglePermGroup ||
                permGroup.name == Manifest.permission_group.UNDEFINED) {
                val groupPackages = groupApps[permGroup.name] ?: continue
                if (groupPackages.isEmpty()) {
                    groupApps.remove(permGroup.name)
                }
            }
        }

        value = groupApps
    }
}
