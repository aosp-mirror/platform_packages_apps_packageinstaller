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

import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.Manifest.permission_group.STORAGE
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_FOREGROUND
import android.app.AppOpsManager.OPSTR_LEGACY_STORAGE
import android.app.AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE
import android.app.Application
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import kotlinx.coroutines.Job

/**
 * A liveData which tracks all packages in the system which have full file permissions, as
 * represented by the OPSTR_LEGACY_STORAGE app op, not just media-only storage permissions.
 *
 */
object FullStoragePermissionAppsLiveData :
    SmartAsyncMediatorLiveData<List<FullStoragePermissionAppsLiveData.FullStoragePackageState>>() {

    private val app: Application = PermissionControllerApplication.get()
    private val standardPermGroupsPackagesLiveData = PermGroupsPackagesLiveData.get(
        customGroups = false)

    data class FullStoragePackageState(
        val packageName: String,
        val user: UserHandle,
        val isLegacy: Boolean,
        val isGranted: Boolean
    )

    init {
        addSource(standardPermGroupsPackagesLiveData) {
            updateAsync()
        }
        addSource(AllPackageInfosLiveData) {
            updateAsync()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        val storagePackages = standardPermGroupsPackagesLiveData.value?.get(STORAGE) ?: return
        val appOpsManager = app.getSystemService(AppOpsManager::class.java) ?: return

        val fullStoragePackages = mutableListOf<FullStoragePackageState>()
        for ((user, packageInfoList) in AllPackageInfosLiveData.value ?: emptyMap()) {
            val userPackages = packageInfoList.filter {
                storagePackages.contains(it.packageName to user) ||
                    it.requestedPermissions.contains(MANAGE_EXTERNAL_STORAGE)
            }

            for (packageInfo in userPackages) {
                fullStoragePackages.add(getFullStorageStateForPackage(appOpsManager,
                    packageInfo, user) ?: continue)
            }
        }

        postValue(fullStoragePackages)
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }

    /**
     * Gets the full storage package information for a given package
     *
     * @param appOpsManager The App Ops manager to use, if applicable
     * @param packageInfo The package whose state is to be determined
     * @param userHandle A preexisting UserHandle object to use. Otherwise, one will be created
     *
     * @return the FullStoragePackageState for the package, or null if the package does not request
     * full storage permissions
     */
    fun getFullStorageStateForPackage(
        appOpsManager: AppOpsManager,
        packageInfo: LightPackageInfo,
        userHandle: UserHandle? = null
    ): FullStoragePackageState? {
        val sdk = packageInfo.targetSdkVersion
        val user = userHandle ?: UserHandle.getUserHandleForUid(packageInfo.uid)
        if (sdk < Build.VERSION_CODES.P) {
            return FullStoragePackageState(packageInfo.packageName, user,
                isLegacy = true, isGranted = true)
        } else if (sdk <= Build.VERSION_CODES.Q &&
            appOpsManager.unsafeCheckOpNoThrow(OPSTR_LEGACY_STORAGE, packageInfo.uid,
                packageInfo.packageName) == MODE_ALLOWED) {
            return FullStoragePackageState(packageInfo.packageName, user,
                isLegacy = true, isGranted = true)
        }
        if (MANAGE_EXTERNAL_STORAGE in packageInfo.requestedPermissions) {
            val mode = appOpsManager.unsafeCheckOpNoThrow(OPSTR_MANAGE_EXTERNAL_STORAGE,
                packageInfo.uid, packageInfo.packageName)
            val granted = mode == MODE_ALLOWED || mode == MODE_FOREGROUND ||
                (mode == MODE_DEFAULT &&
                    MANAGE_EXTERNAL_STORAGE in packageInfo.grantedPermissions)
            return FullStoragePackageState(packageInfo.packageName, user,
                isLegacy = false, isGranted = granted)
        }
        return null
    }

    /**
     * Recalculate the LiveData
     * TODO ntmyren: Make livedata properly observe app ops
     */
    fun recalculate() {
        updateAsync()
    }
}