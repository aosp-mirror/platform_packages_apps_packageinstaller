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

import android.Manifest.permission_group.STORAGE
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_LEGACY_STORAGE
import android.app.Application
import android.os.Build
import android.os.UserHandle
import kotlinx.coroutines.Job

/**
 * A liveData which tracks all packages in the system which have full file permissions, as
 * represented by the OPSTR_LEGACY_STORAGE app op, not just media-only storage permissions.
 *
 * @param app The current application
 */
class FullStoragePermissionAppsLiveData(private val app: Application) :
    SmartAsyncMediatorLiveData<List<Pair<String, UserHandle>>>() {

    private val standardPermGroupsPackagesLiveData =
        PermGroupPackagesUiInfoRepository.getAllStandardPermGroupsPackagesLiveData(app)

    init {
        addSource(standardPermGroupsPackagesLiveData) {
            updateAsync()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        val storagePackages = standardPermGroupsPackagesLiveData.value?.get(STORAGE) ?: return
        val appOpsManager = app.getSystemService(AppOpsManager::class.java) ?: return

        val legacyPackages = mutableListOf<Pair<String, UserHandle>>()
        for ((user, packageInfoList) in UserPackageInfosRepository.getAllPackageInfosLiveData(app)
            .value ?: emptyMap()) {
            val userPackages = packageInfoList.filter {
                storagePackages.contains(it.packageName to user)
            }

            for (packageInfo in userPackages) {
                val sdk = packageInfo.targetSdkVersion
                if (sdk < Build.VERSION_CODES.P) {
                    legacyPackages.add(packageInfo.packageName to user)
                } else if (sdk < Build.VERSION_CODES.R &&
                    appOpsManager.unsafeCheckOpNoThrow(OPSTR_LEGACY_STORAGE, packageInfo.uid,
                        packageInfo.packageName) != MODE_IGNORED) {
                    legacyPackages.add(packageInfo.packageName to user)
                }
            }
        }

        postValue(legacyPackages)
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }
}