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
import android.os.UserHandle

/**
 * Repository for PermissionGroupLiveDatas.
 * Maps a pair of String Permission Group Name and UserHandle to a PermissionGroupLiveData.
 */
object PermissionGroupRepository
    : DataRepository<Pair<String, UserHandle>, PermissionGroupLiveData>() {

    /**
     * Gets the PermissionGroupLiveData associated with the provided group name and user,
     * creating it if need be.
     *
     * @param app: The application this is being called from
     * @param groupName: The name of the permission group desired
     * @param user: The UserHandle of the user for whom we want the permission group
     *
     * @return The cached or newly created PermissionGroupListener
     */
    fun getPermissionGroupLiveData(app: Application, groupName: String, user: UserHandle):
        PermissionGroupLiveData {
        return getDataObject(app, groupName to user)
    }

    override fun newValue(
        app: Application,
        key: Pair<String, UserHandle>
    ): PermissionGroupLiveData {
        return PermissionGroupLiveData(app, key.first, key.second)
    }
}