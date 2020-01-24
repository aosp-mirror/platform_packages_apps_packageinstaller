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

import android.app.Application
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData.addSource
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * A LiveData which tracks the PackageInfos of all of the packages in the system, for all users.
 */
object AllPackageInfosLiveData :
    SmartUpdateMediatorLiveData<Map<UserHandle, List<LightPackageInfo>>>() {

    private val app: Application = PermissionControllerApplication.get()
    private val usersLiveData = UsersLiveData.get(app)
    private val userPackageInfosLiveDatas = mutableMapOf<UserHandle, UserPackageInfosLiveData>()
    private val userPackageInfos = mutableMapOf<UserHandle, List<LightPackageInfo>>()

    init {
        addSource(usersLiveData) {
            updateIfActive()
        }
    }

    override fun onUpdate() {
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
                    UserPackageInfosLiveData[user]
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
