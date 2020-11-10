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

import android.app.AppOpsManager.permissionToOp
import android.app.Application
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils.getPlatformPermissionNamesOfGroup
import kotlin.collections.set

/**
 * LiveData that loads the last usage of permission group for every package/attributionTag-pair.
 *
 * <p>This relies on app-ops data, hence this only works for platform defined permission groups.
 *
 * <p>For app-ops with duration the end of the access is considered.
 *
 * <p>Returns map perm-group-name -> {@link OpUsageLiveData.OpAccess}
 *
 * @param app The current application
 * @param permGroupsNames The names of the permission groups we wish to search for
 * @param usageDurationMs how much ago can an access have happened to be considered
 */
class PermGroupUsageLiveData(
    private val app: Application,
    private val permGroupsNames: List<String>,
    private val usageDurationMs: Long
) : SmartUpdateMediatorLiveData<Map<String, List<OpAccess>>>() {
    /** Perm group name -> OpUsageLiveData */
    private val permGroupUsages = permGroupsNames.map { permGroup ->
        val appops = getPlatformPermissionNamesOfGroup(permGroup).mapNotNull { permName ->
            permissionToOp(permName)
        }

        permGroup to OpUsageLiveData[appops, usageDurationMs]
    }.toMap()

    init {
        for (usage in permGroupUsages.values) {
            addSource(usage) {
                update()
            }
        }
    }

    override fun onUpdate() {
        if (permGroupUsages.values.any { !it.isInitialized }) {
            return
        }

        if (permGroupUsages.values.any { it.value == null }) {
            value = null
            return
        }

        // Only keep the last access for a permission group
        value = permGroupUsages.map { (permGroupName, usageLiveData) ->
            // (packageName, attributionTag) -> access
            val lastAccess = mutableMapOf<Pair<String, String?>, OpAccess>()
            for (access in usageLiveData.value!!.values.flatten()) {
                val key = access.packageName to access.attributionTag
                if (access.isRunning ||
                        lastAccess[key]?.lastAccessTime ?: 0 < access.lastAccessTime) {
                    lastAccess[key] = access
                }
            }

            permGroupName to lastAccess.values.toList()
        }.toMap()
    }

    companion object : DataRepository<Pair<List<String>, Long>, PermGroupUsageLiveData>() {
        override fun newValue(key: Pair<List<String>, Long>): PermGroupUsageLiveData {
            return PermGroupUsageLiveData(PermissionControllerApplication.get(), key.first,
                    key.second)
        }
    }
}
