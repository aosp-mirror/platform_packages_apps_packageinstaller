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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.android.permissioncontroller.PermissionControllerApplication
import kotlinx.coroutines.Job

/**
 * Tracks whether the mic is muted or not
 */
val micMutedLiveData = object : SmartAsyncMediatorLiveData<Boolean>() {
    private val app = PermissionControllerApplication.get()

    private val isMicMuteRecevicer = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            update()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        postValue(app.getSystemService(AudioManager::class.java).isMicrophoneMute())
    }

    override fun onActive() {
        super.onActive()

        app.registerReceiver(isMicMuteRecevicer,
                IntentFilter(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED))
        update()
    }

    override fun onInactive() {
        super.onInactive()

        app.unregisterReceiver(isMicMuteRecevicer)
    }
}
