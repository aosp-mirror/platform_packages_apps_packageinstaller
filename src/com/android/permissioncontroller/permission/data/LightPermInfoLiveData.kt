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
import android.content.pm.PackageManager
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermInfo
import com.android.permissioncontroller.permission.utils.Utils.OS_PKG
import com.android.permissioncontroller.permission.utils.Utils.isRuntimePlatformPermission
import kotlinx.coroutines.Job

/**
 * LiveData for a LightPermInfo.
 *
 * @param app current Application
 * @param permissionName name of the permission this LiveData will watch for mode changes for
 */
class LightPermInfoLiveData private constructor(
    private val app: Application,
    private val permissionName: String
) : SmartAsyncMediatorLiveData<LightPermInfo>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    private val LOG_TAG = LightPermInfoLiveData::class.java.simpleName

    /** Is this liveData currently listing for changes */
    private var isListeningForChanges = false

    /**
     * Callback from the PackageBroadcastReceiver.
     *
     * <p>Package updates might change permission properties
     */
    override fun onPackageUpdate(ignored: String) {
        updateAsync()
    }

    override fun updateAsync() {
        // No need to update if the value can never change
        if (value != null && isImmutable()) {
            return
        }

        super.updateAsync()
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        val newValue = try {
            LightPermInfo(app.packageManager.getPermissionInfo(permissionName, 0))
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(LOG_TAG, "Permission \"$permissionName\" not found")
            invalidateSingle(permissionName)
            null
        }

        if (isImmutable()) {
            stopListeningForChanges()
        }

        postValue(newValue)
    }

    /**
     * @return if the permission state can never change
     */
    private fun isImmutable(): Boolean {
        // The os package never changes
        value?.let {
            if (it.packageName == OS_PKG) {
                return true
            }
        }

        // Platform permissions never change
        return isRuntimePlatformPermission(permissionName)
    }

    /**
     * Start listing for changes to this permission if needed
     */
    private fun startListeningForChanges() {
        if (!isListeningForChanges && !isImmutable()) {
            isListeningForChanges = true
            PackageBroadcastReceiver.addAllCallback(this)
        }
    }

    /**
     * Stop listing for changes to this permission
     */
    private fun stopListeningForChanges() {
        if (isListeningForChanges) {
            PackageBroadcastReceiver.removeAllCallback(this)
            isListeningForChanges = false
        }
    }

    override fun onActive() {
        super.onActive()

        startListeningForChanges()
        updateAsync()
    }

    override fun onInactive() {
        super.onInactive()

        stopListeningForChanges()
    }

    /**
     * Repository for LightPermInfoLiveData
     *
     * <p>Key value is a string permission name, value is its corresponding LiveData.
     */
    companion object : DataRepositoryForPackage<String, LightPermInfoLiveData>() {
        override fun newValue(key: String): LightPermInfoLiveData {
            return LightPermInfoLiveData(PermissionControllerApplication.get(), key)
        }
    }
}
