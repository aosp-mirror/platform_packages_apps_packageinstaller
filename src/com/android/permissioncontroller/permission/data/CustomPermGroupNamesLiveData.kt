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
import android.content.pm.PermissionInfo
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils

/**
 * A class which tracks the names of all custom permission groups in the system, including
 * non-grouped runtime permissions, the UNDEFINED group, and any group not defined by the system.
 *
 */
object CustomPermGroupNamesLiveData : SmartUpdateMediatorLiveData<List<String>>() {

    private val app: Application = PermissionControllerApplication.get()
    private val packagesLiveData = AllPackageInfosLiveData

    init {
        // TODO ntmyren: remove once b/154796729 is fixed
        Log.i("CustomPermGroupNames", "observing UserPackageInfoLiveData for all " +
            "users in CustomPermGroupNamesLiveData")
        addSource(packagesLiveData) {
            update()
        }
    }

    override fun onUpdate() {
        val platformGroupNames = Utils.getPlatformPermissionGroups()
        val groupNames = mutableListOf<String>()

        val allPackages = packagesLiveData.value ?: return

        for ((_, packageInfos) in allPackages) {
            for (packageInfo in packageInfos) {
                // Look for possible lone runtime permissions or custom groups
                packageInfo.permissions.let {
                    for (permission in it) {
                        // We care only about installed runtime permissions.
                        if (permission.protection != PermissionInfo.PROTECTION_DANGEROUS ||
                            permission.flags and PermissionInfo.FLAG_INSTALLED == 0) {
                            continue
                        }

                        // If this permission is already in a group, no more work to do
                        if (groupNames.contains(permission.group) ||
                            platformGroupNames.contains(permission.group) ||
                            groupNames.contains(permission.name)) {
                            continue
                        }

                        if (permission.group != null) {
                            groupNames.add(permission.group)
                        } else {
                            groupNames.add(permission.name)
                        }
                    }
                }
            }
        }
        value = groupNames
    }
}
