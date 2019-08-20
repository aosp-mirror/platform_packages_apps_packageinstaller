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
import android.os.UserHandle

/**
 * Repository for PackageInfo LiveDatas, and a PackageBroadcastReceiver.
 * Key value is a String package name and UserHandle, value is a PackageInfoLiveData.
 */
object PackageInfoRepository : DataRepository<Pair<String, UserHandle>, PackageInfoLiveData>() {

    private var broadcastReceiver: PackageBroadcastReceiver? = null
    private val userContexts = mutableMapOf<UserHandle, Context>()

    /**
     * Gets the PackageBroadcastReceiver, instantiating it if need be.
     *
     * @param app: The application this is being called from
     *
     * @return The cached or newly created PackageBroadcastReceiver
     */
    @JvmStatic
    fun getPackageBroadcastReceiver(app: Application): PackageBroadcastReceiver {
        if (broadcastReceiver == null) {
            broadcastReceiver = PackageBroadcastReceiver(app)
        }
        return broadcastReceiver!!
    }

    /**
     * Used by the PackageInfoLiveData objects. Must be instantiated if used before
     * getPackageInfoLiveData is called for the first time.
     */
    var permissionListenerMultiplexer: PermissionListenerMultiplexer? = null

    /**
     * Gets the PackageInfoLiveData associated with the provided package name and user,
     * creating it if need be.
     *
     * @param app: The application this is being called from
     * @param packageName: The name of the package desired
     * @param user: The UserHandle for whom we want the package
     *
     * @return The cached or newly created PackageInfoLiveData
     */
    @JvmStatic
    fun getPackageInfoLiveData(app: Application, packageName: String, user: UserHandle):
        PackageInfoLiveData {
        if (permissionListenerMultiplexer == null) {
            permissionListenerMultiplexer =
                PermissionListenerMultiplexer(app)
        }
        return getDataObject(app, packageName to user)
    }

    @JvmStatic
    fun getUserContext(app: Application, user: UserHandle): Context {
        return userContexts.getOrPut(user) {
            app.applicationContext.createPackageContextAsUser(app.packageName, 0, user)
        }
    }

    override fun newValue(app: Application, key: Pair<String, UserHandle>): PackageInfoLiveData {
        return PackageInfoLiveData(app, key.first, key.second)
    }
}