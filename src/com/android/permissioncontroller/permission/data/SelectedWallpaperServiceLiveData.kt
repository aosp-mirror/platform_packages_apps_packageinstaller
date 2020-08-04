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
import android.app.WallpaperManager
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * A LiveData which tracks selected wallpaper service package
 *
 * @param app The current application
 * @param user The user the services should be determined for
 */
class SelectedWallpaperServiceLiveData(
    private val app: Application,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<String?>() {

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val packageName = try {
            Utils.getUserContext(app, user)
                    .getSystemService(WallpaperManager::class.java)
                    ?.wallpaperInfo
                    ?.packageName
        } catch (e: NullPointerException) {
            null
        }

        postValue(packageName)
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }

    /**
     * Repository for [SelectedWallpaperServiceLiveData]
     *
     * <p> Key value is a user, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<UserHandle,
            SelectedWallpaperServiceLiveData>() {
        override fun newValue(key: UserHandle): SelectedWallpaperServiceLiveData {
            return SelectedWallpaperServiceLiveData(PermissionControllerApplication.get(), key)
        }
    }
}