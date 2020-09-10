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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.os.UserManager

import com.android.permissioncontroller.PermissionControllerApplication

/**
 * Live data of the users of the current profile group.
 *
 *
 * Data source: system server
 */
object UsersLiveData : SmartUpdateMediatorLiveData<List<UserHandle>>() {

    @SuppressLint("StaticFieldLeak")
    private val app = PermissionControllerApplication.get()

    /** Monitors changes to the users on this device  */
    private val mUserMonitor = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onUpdate()
        }
    }

    /**
     * Update the encapsulated data with the current list of users.
     */
    override fun onUpdate() {
        value = app.getSystemService(UserManager::class.java)!!.userProfiles
    }

    override fun onActive() {
        onUpdate()

        val userChangeFilter = IntentFilter()
        userChangeFilter.addAction(Intent.ACTION_USER_ADDED)
        userChangeFilter.addAction(Intent.ACTION_USER_REMOVED)

        app.registerReceiver(mUserMonitor, userChangeFilter)

        super.onActive()
    }

    override fun onInactive() {
        app.unregisterReceiver(mUserMonitor)
        super.onInactive()
    }
}
