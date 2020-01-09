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
import android.content.pm.PackageManager
import android.os.UserHandle
import com.android.permissioncontroller.permission.data.PackageInfoRepository.getPackageBroadcastReceiver
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks all of the packageinfos installed for a given user.
 *
 * @param app The current application
 * @param user The user whose packages are desired
 */
class UserPackageInfosLiveData(
    private val app: Application,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<@kotlin.jvm.JvmSuppressWildcards List<LightPackageInfo>>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    override fun setValue(newValue: List<LightPackageInfo>?) {
        newValue?.let {
            for (packageInfo in newValue) {
                // This is an optimization, since setting the individual package liveDatas is
                // very low cost, and will save time and computation later down the line.
                PackageInfoRepository.setPackageInfoLiveData(app, packageInfo)
            }
        }
        super.setValue(newValue)
    }

    override fun onPackageUpdate(packageName: String) {
        updateAsync()
    }

    /**
     * Get all of the packages in the system, organized by user.
     */
    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }
        val packageInfos = app.applicationContext.packageManager
            .getInstalledPackagesAsUser(PackageManager.GET_PERMISSIONS,
                user.identifier)
        postValue(packageInfos.map { packageInfo -> LightPackageInfo(packageInfo) })
    }

    override fun onActive() {
        super.onActive()

        getPackageBroadcastReceiver(app).addAllCallback(this)
        updateAsync()
    }

    override fun onInactive() {
        super.onInactive()

        getPackageBroadcastReceiver(app).removeAllCallback(this)
    }
}

/**
 * A LiveData which tracks the PackageInfos of all of the packages in the system, for all users.
 *
 * @param app The current application
 */
class AllPackageInfosLiveData(
    private val app: Application
) : SmartUpdateMediatorLiveData<Map<UserHandle, List<LightPackageInfo>>>() {
    private val usersLiveData = UsersLiveData.get(app)
    private val userPackageInfosLiveDatas = mutableMapOf<UserHandle, UserPackageInfosLiveData>()
    private val userPackageInfos = mutableMapOf<UserHandle, List<LightPackageInfo>>()

    init {
        addSource(usersLiveData) {
            update()
        }
    }

    override fun update() {
        usersLiveData.value?.let { users ->
            val (usersToAdd, usersToRemove) =
                KotlinUtils.getMapAndListDifferences(users, userPackageInfosLiveDatas)
            for (user in usersToRemove) {
                userPackageInfosLiveDatas[user]?.let { userPackageInfosLiveData ->
                    removeSource(userPackageInfosLiveData)
                    userPackageInfosLiveDatas.remove(user)
                }
            }
            for (user in usersToAdd) {
                val userPackageInfosLiveData =
                    UserPackageInfosRepository.getUserPackageInfosLiveData(app, user)
                userPackageInfosLiveDatas[user] = userPackageInfosLiveData
            }

            for (user in usersToAdd) {
                addSource(userPackageInfosLiveDatas[user]!!) {
                    it?.let { packageInfos ->
                        onUserPackageUpdates(user, packageInfos)
                    }
                }
            }
        }
    }

    private fun onUserPackageUpdates(user: UserHandle, packageInfos: List<LightPackageInfo>?) {
        if (packageInfos == null) {
            userPackageInfos.remove(user)
        } else {
            userPackageInfos[user] = packageInfos
        }
        if (userPackageInfosLiveDatas.all { it.value.isInitialized }) {
            value = userPackageInfos.toMap()
        }
    }
}

/**
 * Repository for UserPackageInfosLiveDatas, as well as the AllPackageInfosLiveData
 * <p> Key value is a UserHandle, value is its corresponding LiveData.
 */
object UserPackageInfosRepository : DataRepository<UserHandle, UserPackageInfosLiveData>() {

    /**
     * Gets the UserPackageInfosLiveData for a given user, creating it if need be.
     *
     * @param app The current application
     *
     * @return The cached or newly created UserPackageNamesLiveData
     */
    fun getUserPackageInfosLiveData(app: Application, user: UserHandle): UserPackageInfosLiveData {
        return getDataObject(app, user)
    }

    override fun newValue(app: Application, key: UserHandle): UserPackageInfosLiveData {
        return UserPackageInfosLiveData(app, key)
    }

    private var allPackageInfosLiveData: AllPackageInfosLiveData? = null

    /**
     * Gets the AllPackageNamesLiveData, creating it if need be.
     *
     * @param app The current application
     *
     * @return The cached or newly created AllPackageNamesLiveData
     */
    fun getAllPackageInfosLiveData(
        app: Application
    ): AllPackageInfosLiveData {
        return allPackageInfosLiveData ?: run {
            val liveData = AllPackageInfosLiveData(app)
            allPackageInfosLiveData = liveData
            liveData
        }
    }
}