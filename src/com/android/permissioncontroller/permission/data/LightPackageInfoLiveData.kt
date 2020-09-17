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
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.Observer
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job

/**
 * LiveData for a LightPackageInfo.
 *
 * @param app The current Application
 * @param packageName The name of the package this LiveData will watch for mode changes for
 * @param user The user for whom the packageInfo will be defined
 */
class LightPackageInfoLiveData private constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<LightPackageInfo>(),
    PackageBroadcastReceiver.PackageBroadcastListener,
    PermissionListenerMultiplexer.PermissionChangeCallback {

    private val LOG_TAG = LightPackageInfoLiveData::class.java.simpleName
    private val userPackagesLiveData = UserPackageInfosLiveData[user]

    private var context = Utils.getUserContext(app, user)
    private var uid: Int? = null
    /**
     * The currently registered UID on which this LiveData is listening for permission changes.
     */
    private var registeredUid: Int? = null
    /**
     * Whether or not this package livedata is watching the UserPackageInfosLiveData
     */
    private var watchingUserPackagesLiveData: Boolean = false

    /**
     * Callback from the PackageBroadcastReceiver. Either deletes or generates package data.
     *
     * @param packageName the name of the package which was updated. Ignored in this method
     */
    override fun onPackageUpdate(packageName: String) {
        updateAsync()
    }

    override fun setValue(newValue: LightPackageInfo?) {
        newValue?.let { packageInfo ->
            if (packageInfo.uid != uid) {
                uid = packageInfo.uid

                if (hasActiveObservers()) {
                    PermissionListenerMultiplexer.addOrReplaceCallback(registeredUid,
                            packageInfo.uid, this)
                    registeredUid = uid
                }
            }
        }
        super.setValue(newValue)
    }

    override fun updateAsync() {
        // If we were watching the userPackageInfosLiveData, stop, since we will override its value
        if (watchingUserPackagesLiveData) {
            removeSource(userPackagesLiveData)
            watchingUserPackagesLiveData = false
        }
        super.updateAsync()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }
        postValue(try {
            LightPackageInfo(context.packageManager.getPackageInfo(packageName,
                PackageManager.GET_PERMISSIONS))
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(LOG_TAG, "Package \"$packageName\" not found")
            invalidateSingle(packageName to user)
            null
        })
    }

    /**
     * Callback from the PermissionListener. Either deletes or generates package data.
     */
    override fun onPermissionChange() {
        updateAsync()
    }

    override fun onActive() {
        super.onActive()

        PackageBroadcastReceiver.addChangeCallback(packageName, this)
        uid?.let {
            registeredUid = uid
            PermissionListenerMultiplexer.addCallback(it, this)
        }
        if (userPackagesLiveData.hasActiveObservers() && !watchingUserPackagesLiveData &&
            !userPackagesLiveData.permChangeStale) {
            watchingUserPackagesLiveData = true
            addSource(userPackagesLiveData, userPackageInfosObserver)
        } else {
            updateAsync()
        }
    }

    val userPackageInfosObserver = Observer<List<LightPackageInfo>> {
        updateFromUserPackageInfosLiveData()
    }

    @MainThread
    private fun updateFromUserPackageInfosLiveData() {
        if (!userPackagesLiveData.isInitialized) {
            return
        }

        val packageInfo = userPackagesLiveData.value!!.find { it.packageName == packageName }
        if (packageInfo != null) {
            // Once we get one non-stale update, stop listening, as any further updates will likely
            // be individual package updates.
            if (!userPackagesLiveData.isStale) {
                removeSource(UserPackageInfosLiveData[user])
                watchingUserPackagesLiveData = false
            }

            value = packageInfo
        } else {
            // If the UserPackageInfosLiveData does not contain this package, check for removal, and
            // stop watching.
            updateAsync()
        }
    }

    override fun onInactive() {
        super.onInactive()

        PackageBroadcastReceiver.removeChangeCallback(packageName, this)
        registeredUid?.let {
            PermissionListenerMultiplexer.removeCallback(it, this)
            registeredUid = null
        }
        if (watchingUserPackagesLiveData) {
            removeSource(userPackagesLiveData)
            watchingUserPackagesLiveData = false
        }
    }

    /**
     * Repository for LightPackageInfoLiveDatas
     * <p> Key value is a string package name and UserHandle pair, value is its corresponding
     * LiveData.
     */
    companion object : DataRepositoryForPackage<Pair<String, UserHandle>,
        LightPackageInfoLiveData>() {
        override fun newValue(key: Pair<String, UserHandle>): LightPackageInfoLiveData {
            return LightPackageInfoLiveData(PermissionControllerApplication.get(),
                key.first, key.second)
        }
    }
}
