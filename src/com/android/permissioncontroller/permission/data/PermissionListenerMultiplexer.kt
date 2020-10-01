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
import com.android.permissioncontroller.PermissionControllerApplication

/**
 * Serves as a single shared Permission Change Listener for all AppPermissionGroupLiveDatas.
 *
 */
object PermissionListenerMultiplexer : PackageManager.OnPermissionsChangedListener {

    private val app: Application = PermissionControllerApplication.get()
    /**
     * Map<UID, list of PermissionChangeCallbacks that wish to be informed when
     * permissions are updated for that UID>
     */
    private val callbacks = mutableMapOf<Int, MutableList<PermissionChangeCallback>>()
    private val pm = app.applicationContext.packageManager

    override fun onPermissionsChanged(uid: Int) {
        callbacks[uid]?.toList()?.forEach { callback ->
            callback.onPermissionChange()
        }
    }

    fun addOrReplaceCallback(oldUid: Int?, newUid: Int, callback: PermissionChangeCallback) {
        if (oldUid != null) {
            removeCallback(oldUid, callback)
        }
        addCallback(newUid, callback)
    }

    fun addCallback(uid: Int, callback: PermissionChangeCallback) {
        val wasEmpty = callbacks.isEmpty()

        callbacks.getOrPut(uid, { mutableListOf() }).add(callback)

        if (wasEmpty) {
            pm.addOnPermissionsChangeListener(this)
        }
    }

    fun removeCallback(uid: Int, callback: PermissionChangeCallback) {
        if (!callbacks.contains(uid)) {
            return
        }

        if (!callbacks[uid]!!.remove(callback)) {
            return
        }

        if (callbacks[uid]!!.isEmpty()) {
            callbacks.remove(uid)
        }

        if (callbacks.isEmpty()) {
            pm.removeOnPermissionsChangeListener(this)
        }
    }

    interface PermissionChangeCallback {
        fun onPermissionChange()
    }
}