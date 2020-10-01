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

package com.android.permissioncontroller.permission.data

import com.android.permissioncontroller.permission.utils.Utils

/**
 * LiveData for a map of background permission name -> list of foreground permission names for every
 * installed, runtime permission in every platform permission group. This LiveData's value is
 * static, since the background/foreground permission relationships are defined by the system.
 */
object ForegroundPermNamesLiveData : SmartUpdateMediatorLiveData<Map<String, List<String>>>() {

    // Since the value will be static, initialize the value upon creating the LiveData.
    init {
        onUpdate()
    }

    override fun onUpdate() {
        val systemGroups = Utils.getPlatformPermissionGroups()
        val groupLiveDatas = systemGroups.map { PermGroupLiveData[it] }
        val permMap = mutableMapOf<String, MutableList<String>>()
        var numLiveDatasSeen = 0
        for (groupLiveData in groupLiveDatas) {
            addSource(groupLiveData) { permGroup ->
                if (permGroup == null) {
                    if (groupLiveData.isInitialized) {
                        numLiveDatasSeen ++
                    }
                    return@addSource
                }
                for (permInfo in permGroup.permissionInfos.values) {
                    val backgroundPerm: String? = permInfo.backgroundPermission
                    if (backgroundPerm != null) {
                        val foregroundPerms = permMap.getOrPut(backgroundPerm) { mutableListOf() }
                        foregroundPerms.add(permInfo.name)
                    }
                }
                numLiveDatasSeen ++
                if (numLiveDatasSeen == groupLiveDatas.size) {
                    value = permMap
                }
            }
        }
    }
}