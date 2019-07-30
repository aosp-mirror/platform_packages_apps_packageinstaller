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
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import androidx.lifecycle.LiveData

/**
 * LiveData for PackageInfo.
 *
 * @param app: The application this LiveData will watch
 * @param packageName: The name of the package this LiveData will watch for mode changes for
 * @param user: The user for whom the packageInfo will be defined
 */
class PackageInfoLiveData(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) : LiveData<PackageInfo>(),
    PackageBroadcastReceiver.PackageBroadcastListener,
    PermissionListenerMultiplexer.PermissionChangeCallback,
    DataRepository.InactiveTimekeeper {

    private val context: Context = app.applicationContext
    private var uid = context.packageManager.getPackageUid(packageName, 0)

    override var timeWentInactive: Long? = null

    /**
     * Callback from the PackageBroadcastReceiver. Either deletes or generates package data.
     *
     * @param packageName the name of the package which was updated. Ignored in this method.
     */
    override fun onPackageUpdate(packageName: String) {
        generatePackageData()
    }

    override fun getValue(): PackageInfo? {
        return super.getValue() ?: run {
            generatePackageData()
            return super.getValue()
        }
    }

    /**
     * Generates a PackageInfo for our given package.
     */
    private fun generatePackageData() {
        try {
            uid = context.packageManager.getPackageUid(packageName, 0)
            this.value = context.packageManager.getPackageInfo(packageName,
                PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            this.value = null
        }
    }

    override fun onPermissionChange() {
        generatePackageData()
    }

    override fun onActive() {
        super.onActive()

        PackageInfoRepository.getPackageBroadcastReceiver(app)
            .addChangeCallback(packageName, this)
        PackageInfoRepository.permissionListenerMultiplexer?.addCallback(uid, this)
    }

    override fun onInactive() {
        super.onInactive()

        timeWentInactive = System.nanoTime()
        PackageInfoRepository.getPackageBroadcastReceiver(app)
            .removeChangeCallback(packageName, this)
        PackageInfoRepository.permissionListenerMultiplexer?.removeCallback(uid, this)
    }
}