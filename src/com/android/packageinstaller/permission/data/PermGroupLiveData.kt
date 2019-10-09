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
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.os.UserHandle
import com.android.packageinstaller.permission.data.PackageInfoRepository.getPackageInfoLiveData
import com.android.packageinstaller.permission.data.PackageInfoRepository.getPackageBroadcastReceiver
import com.android.packageinstaller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.packageinstaller.permission.model.livedatatypes.LightPermInfo
import com.android.packageinstaller.permission.model.livedatatypes.PermGroup
import com.android.packageinstaller.permission.utils.KotlinUtils
import com.android.packageinstaller.permission.utils.Utils

/**
 * LiveData for a Permission Group. Contains GroupInfo and a list of PermissionInfos. Loads
 * synchronously.
 *
 * @param app: The current application
 * @param groupName: The name of the permission group this LiveData represents
 */
class PermGroupLiveData(
    private val app: Application,
    private val groupName: String
) : SmartUpdateMediatorLiveData<PermGroup>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    private val context = app.applicationContext!!

    /**
     * Map<packageName, LiveData<PackageInfo>>
     */
    private val pkgsUsingGroup = mutableMapOf<String, PackageInfoLiveData>()

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
     * Adds a PackageInfoLiveData as a source, if we don't already have it.
     *
     * @param packageName the name of the package the PackageInfoLiveData watches
     * @param liveData the PackageInfoLiveData to be inserted
     */
    private fun addPackageLiveData(packageName: String, liveData: PackageInfoLiveData) {
        if (!pkgsUsingGroup.contains(packageName)) {
            pkgsUsingGroup[packageName] = liveData
            addSource(liveData) {
                update()
            }
        }
    }

    /**
     * Initializes this permission group from scratch. Resets the groupInfo, PermissionInfos, and
     * PackageInfoLiveDatas, then re-adds them.
     * TODO ntmyren: Make private once AppPermissionFragment is fixed
     */
    fun update() {
        val permissionInfos = mutableMapOf<String, LightPermInfo>()

        groupInfo = Utils.getGroupInfo(groupName, context) ?: run {
            value = null
            return
        }

        when (groupInfo) {
            is PermissionGroupInfo -> {
                val permInfos = try {
                    Utils.getInstalledRuntimePermissionInfosForGroup(context.packageManager,
                        groupName)
                } catch (e: PackageManager.NameNotFoundException) {
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

        // TODO ntmyren: What if the package isn't installed for the system user?
        addPackageLiveData(groupInfo.packageName,
            getPackageInfoLiveData(app, groupInfo.packageName, UserHandle.SYSTEM))

        val (toAdd, toRemove) = KotlinUtils.getMapAndListDifferences(packageNames, pkgsUsingGroup)
        for (packageName in toAdd) {
            if (!packageNames.contains(packageName)) {
                addPackageLiveData(groupInfo.packageName,
                    getPackageInfoLiveData(app, packageName, UserHandle.SYSTEM))
            }
        }

        for (packageName in toRemove) {
            pkgsUsingGroup[packageName]?.let { liveData ->
                removeSource(liveData)
                pkgsUsingGroup.remove(packageName)
            }
        }
    }

    override fun onInactive() {
        super.onInactive()

        getPackageBroadcastReceiver(app).removeAllCallback(this)
    }

    /**
     * Load data, and register a package change listener. We must watch for package changes,
     * because there is currently no listener for permission changes.
     */
    override fun onActive() {
        update()

        super.onActive()

        getPackageBroadcastReceiver(app).addAllCallback(this)
    }
}

/**
 * Repository for PermissionGroupLiveDatas.
 * <p> Key value is a string permission group name, value is its corresponding LiveData.
 */
object PermGroupRepository
    : DataRepository<String, PermGroupLiveData>() {

    /**
     * Gets the PermGroupLiveData associated with the provided group name, creating it
     * if need be.
     *
     * @param app: The current application
     * @param groupName: The name of the permission group desired
     *
     * @return The cached or newly created PermGroupLiveData for the given groupName
     */
    fun getPermGroupLiveData(app: Application, groupName: String):
        PermGroupLiveData {
        return getDataObject(app, groupName)
    }

    override fun newValue(
        app: Application,
        key: String
    ): PermGroupLiveData {
        return PermGroupLiveData(app, key)
    }

    private var customPermGroupNamesLiveData: CustomPermGroupNamesLiveData? = null

    /**
     * Gets the CustomPermGroupNamesLiveData, creating it if need be.
     *
     * @param app: The current application
     *
     * @return The cached or newly created AllPermGroupsNamesLiveData
     */
    fun getCustomPermGroupNamesLiveData(app: Application): CustomPermGroupNamesLiveData {
        return customPermGroupNamesLiveData ?: run {
            val liveData = CustomPermGroupNamesLiveData(app)
            customPermGroupNamesLiveData = liveData
            liveData
        }
    }
}
