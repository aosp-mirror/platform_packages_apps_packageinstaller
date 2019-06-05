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

import android.app.AppOpsManager
import android.app.Application

/**
 * Listens to a particular App Op for all packages. Allows other classes to register a callback,
 * to be notified when the app op mode is updated for a specific package.
 *
 * @param app: The application this multiplexer is being instantiated in
 * @param opName: The name of the op this multiplexer will watch
 */
class AppOpModeChangeListenerMultiplexer(val app: Application, val opName: String)
    : AppOpsManager.OnOpChangedListener,
    DataRepository.InactiveTimekeeper {
    private val appOpsManager =
        app.applicationContext.getSystemService(AppOpsManager::class.java)
    /**
     * Maps a String package name to a list of listeners
     */
    private val callbacks = mutableMapOf<String, MutableList<OnAppOpModeChangeListener>>()

    override var timeWentInactive: Long? = 0

    /**
     * Adds a listener for this op for a specific package.
     *
     * @param packageName the name of the package this listener wishes to be notified for
     * @param listener the callback that will be notified
     */
    fun addListener(packageName: String, listener: OnAppOpModeChangeListener) {
        val wasEmpty = callbacks.isEmpty()

        callbacks.getOrPut(packageName, ::mutableListOf).add(listener)

        if (wasEmpty) {
            appOpsManager.startWatchingMode(opName, null, this)
        }
    }

    /**
     * Removes a listener for this specific op.
     *
     * @param packageName the package the listener to be removed is listening on
     * @param listener the listener to be removed
     */
    fun removeListener(packageName: String, listener: OnAppOpModeChangeListener) {
        if (callbacks[packageName]?.remove(listener) != true) {
            return
        }

        if (callbacks[packageName]!!.isEmpty()) {
            callbacks.remove(packageName)
        }

        if (callbacks.isEmpty()) {
            appOpsManager.stopWatchingMode(this)
        }
    }

    /**
     * Callback called when the op is changed in the system.
     *
     * @param op the name of the op whose mode was changed (should always be the op we listened to)
     * @param packageName the package for which the op mode was changed
     */
    override fun onOpChanged(op: String, packageName: String) {
        app.applicationContext.mainExecutor.execute {
            callbacks[packageName]?.forEach { callback ->
                callback.onChanged(op, packageName)
            }
        }
    }

    override fun hasObservers(): Boolean {
        return callbacks.isNotEmpty()
    }

    /**
     * An interface for other classes to receive callbacks from this class
     */
    interface OnAppOpModeChangeListener {
        fun onChanged(op: String, packageName: String)
    }
}