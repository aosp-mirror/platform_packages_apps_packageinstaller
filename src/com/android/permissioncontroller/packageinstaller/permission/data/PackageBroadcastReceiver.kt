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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Listens for package additions, replacements, and removals, and notifies listeners.
 *
 * @param app: The current application
 */
class PackageBroadcastReceiver(private val app: Application) : BroadcastReceiver() {

    private val intentFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
    }

    /**
     * Map<packageName, callbacks listenening to package>
     */
    private val changeCallbacks = mutableMapOf<String, MutableList<PackageBroadcastListener>>()
    /**
     * A list of listener IDs, which listen to all package additions, changes, and removals.
     */
    private val allCallbacks = mutableListOf<PackageBroadcastListener>()

    /**
     * Add a callback which will be notified when the specified packaged is changed or removed.
     */
    fun addChangeCallback(packageName: String, listener: PackageBroadcastListener) {
        val wasEmpty = hasNoListeners()

        changeCallbacks.getOrPut(packageName, { mutableListOf() }).add(listener)

        if (wasEmpty) {
            app.registerReceiver(this, intentFilter)
        }
    }

    /**
     * Add a callback which will be notified any time a package is added, removed, or changed.
     *
     * @param listener the listener to be added
     * @return returns the integer ID assigned to the
     */
    fun addAllCallback(listener: PackageBroadcastListener) {
        val wasEmpty = hasNoListeners()

        allCallbacks.add(listener)

        if (wasEmpty) {
            app.registerReceiver(this, intentFilter)
        }
    }

    /**
     * Removes a package add/remove/change callback.
     *
     * @param listener the listener we wish to remove
     */
    fun removeAllCallback(listener: PackageBroadcastListener) {
        if (!allCallbacks.remove(listener)) {
            return
        }
        if (hasNoListeners()) {
            app.unregisterReceiver(this)
        }
    }

    /**
     * Removes a change callback.
     *
     * @param packageName the package the listener is listening for
     * @param listener the listener we wish to remove
     */
    fun removeChangeCallback(packageName: String?, listener: PackageBroadcastListener) {
        if (changeCallbacks.contains(packageName)) {
            if (!changeCallbacks[packageName]!!.remove(listener)) {
                return
            }
            if (changeCallbacks[packageName]!!.isEmpty()) {
                changeCallbacks.remove(packageName)
            }
            if (hasNoListeners()) {
                app.unregisterReceiver(this)
            }
        }
    }

    fun getNumListeners(): Int {
        var numListeners = allCallbacks.size
        for ((_, changeCallbackList) in changeCallbacks) {
            numListeners += changeCallbackList.size
        }
        return numListeners
    }

    fun hasNoListeners(): Boolean {
        return getNumListeners() == 0
    }

    /**
     * Upon receiving a broadcast, rout it to the proper callbacks.
     *
     * @param context: the context of the broadcast
     * @param intent: data about the broadcast which was sent
     */
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.dataString!!

        for (callback in allCallbacks) {
            callback.onPackageUpdate(packageName)
        }

        if (intent.action != Intent.ACTION_PACKAGE_ADDED) {
            changeCallbacks[packageName]?.forEach { callback ->
                callback.onPackageUpdate(packageName)
            }
        }
    }

    /**
     * A listener interface for objects desiring to be notified of package broadcasts.
     */
    interface PackageBroadcastListener {
        /**
         * To be called when a specific package has been changed, or when any package has been
         * installed.
         *
         * @param packageName the name of the package which was updated
         */
        fun onPackageUpdate(packageName: String)
    }
}