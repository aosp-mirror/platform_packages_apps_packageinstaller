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
import android.content.pm.PackageInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.os.UserHandle
import androidx.lifecycle.MediatorLiveData
import com.android.packageinstaller.permission.data.PackageInfoRepository.getPackageInfoLiveData
import com.android.packageinstaller.permission.data.PackageInfoRepository.getPackageBroadcastReceiver
import com.android.packageinstaller.permission.utils.Utils

/**
 * LiveData for a Permission Group. Contains GroupInfo and a list of PermissionInfos
 *
 * @param app: The application this LiveData is being instantiated in
 * @param groupName: The name of the permission group this LiveData represents
 * @param user: The user for which this permission group is defined
 */
class PermissionGroupLiveData(
    private val app: Application,
    private val groupName: String,
    private val user: UserHandle
) : MediatorLiveData<PermissionGroupLiveData.PermissionGroup>(),
    PackageBroadcastReceiver.PackageBroadcastListener,
    DataRepository.InactiveTimekeeper {

    private val context = app.applicationContext!!
    /**
     * Maps a String permission name to its corresponding PermissionInfo
     */
    private val permissionInfos = mutableMapOf<String, PermissionInfo>()
    /**
     * Maps a String packageName to its corresponding PackageInfoLiveData
     */
    private val liveDatas = mutableMapOf<String, PackageInfoLiveData>()

    private lateinit var groupInfo: PackageItemInfo
    override var timeWentInactive: Long? = null

    override fun getValue(): PermissionGroup? {
        return super.getValue() ?: run {
            initializeGroup()
            return super.getValue()
        }
    }

    /**
     * Called when a package is installed, changed, or removed. If we aren't already watching the
     * package, gets its packageInfo, and passes it to onPackageChanged.
     *
     * @param packageName the package which was added
     */
    override fun onPackageUpdate(packageName: String) {
        if (!liveDatas.contains(packageName)) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS)
                onPackageChanged(packageInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                /*
                 * If we can't find the package, and aren't already listening, we don't need to care
                 * about it
                 */
            }
        }
    }

    /**
     * Responds to package changes, either addition, replacement, or removal. Removes all
     * permissions that were defined by the affected package, and then re-adds any currently
     * defined.
     *
     * @param packageInfo the packageInfo of the package which was changed
     */
    private fun onPackageChanged(packageInfo: PackageInfo) {
        if (groupInfo.packageName == packageInfo.packageName) {
            groupInfo = Utils.getGroupInfo(groupInfo.name, context) ?: run {
                removeAllDataAndSetValueNull()
                return
            }
        }
        removePackagePermissions(packageInfo.packageName)

        var hasPackagePermissions = false
        for (newPermission in packageInfo.permissions) {
            if (Utils.getGroupOfPermission(newPermission) == groupInfo.name) {
                permissionInfos[newPermission.name] = newPermission
                hasPackagePermissions = true
            }
        }

        if (hasPackagePermissions) {
            val liveData = liveDatas[packageInfo.packageName]
                ?: getPackageInfoLiveData(app, packageInfo.packageName, user)
            addPackageLiveData(packageInfo.packageName, liveData)
        }

        /**
         * If this isn't the package defining the permission group, and it doesn't define any
         * permissions in the group, and we currently listen to the package, stop listening
         */
        if (groupInfo.packageName != packageInfo.packageName && !hasPackagePermissions &&
            liveDatas.contains(packageInfo.packageName)) {
            removeSource(liveDatas[packageInfo.packageName]!!)
            liveDatas.remove(packageInfo.packageName)
        }

        this.value = PermissionGroup(groupInfo, permissionInfos)
    }

    /**
     * Remove a package. If the package was the one defining this group, remove all sources and
     * clear all data. Otherwise, remove all permissions defined by the removed package, then
     * update observers.
     *
     * @param packageName the package which was removed
     */
    private fun onPackageRemoved(packageName: String) {
        if (groupInfo.packageName == packageName) {
            /**
             * If the package defining this permission group is removed, stop listening to any
             * packages, clear any held data, and set value to null
             */
            removeAllDataAndSetValueNull()
            return
        }

        liveDatas[packageName]?.let {
            removeSource(it)
            liveDatas.remove(packageName)
        }

        removePackagePermissions(packageName)
        this.value = PermissionGroup(groupInfo, permissionInfos)
    }

    /**
     * Remove all permissions defined by a particular package
     *
     * @param packageName the package whose permissions we wish to remove
     */
    private fun removePackagePermissions(packageName: String) {
        permissionInfos.entries.removeAll { (_, permInfo) ->
            permInfo.packageName == packageName
        }
    }

    /**
     * Adds a PackageInfoLiveData as a source, if we don't already have it.
     *
     * @param packageName the name of the package the PackageInfoLiveData watches
     * @param data the PackageInfoLiveData to be inserted
     */
    private fun addPackageLiveData(packageName: String, data: PackageInfoLiveData) {
        if (!liveDatas.contains(packageName)) {
            liveDatas[packageName] = data
            addSource(data) {
                if (it != null) {
                    onPackageChanged(it)
                } else {
                    onPackageRemoved(packageName)
                }
            }
        }
    }

    /**
     * Initializes this permission group from scratch. Resets the groupInfo, PermissionInfos, and
     * PackageInfoLiveInfos, then re-adds them.
     */
    private fun initializeGroup() {
        permissionInfos.clear()
        for ((_, liveData) in liveDatas) {
            removeSource(liveData)
        }
        liveDatas.clear()

        groupInfo = Utils.getGroupInfo(groupName, context) ?: run {
            value = null
            return
        }

        when (groupInfo) {
            is PermissionGroupInfo -> {
                try {
                    val permInfos =
                        Utils.getPermissionInfosForGroup(context.packageManager, groupName)
                    for (permInfo in permInfos) {
                        permissionInfos[permInfo.name] = permInfo
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    value = null
                    return
                }
            }
            is PermissionInfo -> permissionInfos[groupName] = groupInfo as PermissionInfo
            else -> {
                value = null
                return
            }
        }

        value = PermissionGroup(groupInfo, permissionInfos)

        addPackageLiveData(groupInfo.packageName,
            getPackageInfoLiveData(app, groupInfo.packageName, user))

        for ((_, permissionInfo) in permissionInfos) {
            val pkgName = permissionInfo.packageName
            if (!liveDatas.contains(pkgName)) {
                addPackageLiveData(pkgName, getPackageInfoLiveData(app, pkgName, user))
            }
        }
    }

    private fun removeAllDataAndSetValueNull() {
        for ((_, liveData) in liveDatas) {
            removeSource(liveData)
        }
        liveDatas.clear()
        permissionInfos.clear()
        value = null
    }

    override fun onInactive() {
        super.onInactive()

        timeWentInactive = System.nanoTime()
        getPackageBroadcastReceiver(app).removeAllCallback(this)
    }

    override fun onActive() {
        super.onActive()

        initializeGroup()
        getPackageBroadcastReceiver(app).addAllCallback(this)
    }

    /**
     * A permission Group, represented as a PackageItemInfo groupInfo, and a map of permission name
     * to PermissionInfo objects.
     *
     * @param permissionInfos: the Permissions in this group
     * @param groupInfo: information about the permission group
     */
    data class PermissionGroup(
        var groupInfo: PackageItemInfo,
        val permissionInfos: MutableMap<String, PermissionInfo>

    )
}
