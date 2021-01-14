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
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermInfo
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroup
import com.android.permissioncontroller.permission.utils.Utils

/**
 * LiveData for a Permission Group. Contains GroupInfo and a list of PermissionInfos. Loads
 * synchronously.
 *
 * @param app The current application
 * @param groupName The name of the permission group this LiveData represents
 */
class PermGroupLiveData private constructor(
    private val app: Application,
    private val groupName: String
) : SmartUpdateMediatorLiveData<PermGroup>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    private val LOG_TAG = this::class.java.simpleName

    private val context = app.applicationContext!!

    /**
     * Map<packageName, LiveData<PackageInfo>>
     */
    private val packageLiveDatas = mutableMapOf<String, LightPackageInfoLiveData>()

    private lateinit var groupInfo: PackageItemInfo

    /**
     * Called when a package is installed, changed, or removed.
     *
     * @param packageName the package which was added or changed
     */
    override fun onPackageUpdate(packageName: String) {
        update()
    }

    /**
     * Initializes this permission group from scratch. Resets the groupInfo, PermissionInfos, and
     * PackageInfoLiveDatas, then re-adds them.
     */
    override fun onUpdate() {
        val permissionInfos = mutableMapOf<String, LightPermInfo>()

        groupInfo = Utils.getGroupInfo(groupName, context) ?: run {
            Log.e(LOG_TAG, "Invalid permission group $groupName")
            invalidateSingle(groupName)
            value = null
            return
        }

        when (groupInfo) {
            is PermissionGroupInfo -> {
                val permInfos = try {
                    Utils.getInstalledRuntimePermissionInfosForGroup(context.packageManager,
                        groupName)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(LOG_TAG, "Invalid permission group $groupName")
                    invalidateSingle(groupName)
                    value = null
                    return
                }

                for (permInfo in permInfos) {
                    permissionInfos[permInfo.name] = LightPermInfo(permInfo)
                }
            }
            is PermissionInfo -> {
                permissionInfos[groupInfo.name] = LightPermInfo(groupInfo as PermissionInfo)
            }
            else -> return
        }

        val permGroup = PermGroup(LightPermGroupInfo(groupInfo), permissionInfos)

        value = permGroup

        val packageNames = permissionInfos.values.map { permInfo -> permInfo.packageName }
            .toMutableSet()
        packageNames.add(groupInfo.packageName)

        // TODO ntmyren: What if the package isn't installed for the system user?
        val getLiveData = { packageName: String ->
            LightPackageInfoLiveData[packageName, UserHandle.SYSTEM]
        }
        setSourcesToDifference(packageNames, packageLiveDatas, getLiveData)
    }

    override fun onInactive() {
        super.onInactive()

        PackageBroadcastReceiver.removeAllCallback(this)
    }

    /**
     * Load data, and register a package change listener. We must watch for package changes,
     * because there is currently no listener for permission changes.
     */
    override fun onActive() {
        update()

        super.onActive()

        PackageBroadcastReceiver.addAllCallback(this)
    }

    /**
     * Repository for PermGroupLiveDatas.
     * <p> Key value is a string permission group name, value is its corresponding LiveData.
     */
    companion object : DataRepository<String, PermGroupLiveData>() {
        override fun newValue(key: String): PermGroupLiveData {
            return PermGroupLiveData(PermissionControllerApplication.get(), key)
        }
    }
}
