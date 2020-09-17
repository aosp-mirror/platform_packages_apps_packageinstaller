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
import com.android.permissioncontroller.PermissionControllerApplication
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Listens for package additions, replacements, and removals, and notifies listeners.
 */
object PackageBroadcastReceiver : BroadcastReceiver() {

    private val app: Application = PermissionControllerApplication.get()
    private val intentFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addDataScheme("package")
    }

    /**
     * Map<packageName, callbacks listenening to package>
     */
    private val changeCallbacks = mutableMapOf<String, MutableSet<PackageBroadcastListener>>()
    /**
     * A list of listener IDs, which listen to all package additions, changes, and removals.
     */
    private val allCallbacks = mutableSetOf<PackageBroadcastListener>()

    /**
     * Add a callback which will be notified when the specified packaged is changed or removed.
     */
    fun addChangeCallback(packageName: String, listener: PackageBroadcastListener) {
        GlobalScope.launch(Main.immediate) {
            val wasEmpty = hasNoListeners()

            changeCallbacks.getOrPut(packageName, { mutableSetOf() }).add(listener)

            if (wasEmpty) {
                app.applicationContext.registerReceiverForAllUsers(this@PackageBroadcastReceiver,
                        intentFilter, null, null)
            }
        }
    }

    /**
     * Add a callback which will be notified any time a package is added, removed, or changed.
     *
     * @param listener the listener to be added
     * @return returns the integer ID assigned to the
     */
    fun addAllCallback(listener: PackageBroadcastListener) {
        GlobalScope.launch(Main.immediate) {
            val wasEmpty = hasNoListeners()

            allCallbacks.add(listener)

            if (wasEmpty) {
                app.applicationContext.registerReceiverForAllUsers(this@PackageBroadcastReceiver,
                        intentFilter, null, null)
            }
        }
    }

    /**
     * Removes a package add/remove/change callback.
     *
     * @param listener the listener we wish to remove
     */
    fun removeAllCallback(listener: PackageBroadcastListener) {
        GlobalScope.launch(Main.immediate) {
            val wasEmpty = hasNoListeners()

            if (allCallbacks.remove(listener) && hasNoListeners() && !wasEmpty) {
                app.applicationContext.unregisterReceiver(this@PackageBroadcastReceiver)
            }
        }
    }

    /**
     * Removes a change callback.
     *
     * @param packageName the package the listener is listening for
     * @param listener the listener we wish to remove
     */
    fun removeChangeCallback(packageName: String?, listener: PackageBroadcastListener) {
        GlobalScope.launch(Main.immediate) {
            val wasEmpty = hasNoListeners()

            changeCallbacks[packageName]?.let { callbackSet ->
                callbackSet.remove(listener)
                if (callbackSet.isEmpty()) {
                    changeCallbacks.remove(packageName)
                }
                if (hasNoListeners() && !wasEmpty) {
                    app.applicationContext.unregisterReceiver(this@PackageBroadcastReceiver)
                }
            }
        }
    }

    private fun getNumListeners(): Int {
        var numListeners = allCallbacks.size
        for ((_, changeCallbackSet) in changeCallbacks) {
            numListeners += changeCallbackSet.size
        }
        return numListeners
    }

    private fun hasNoListeners(): Boolean {
        return getNumListeners() == 0
    }

    /**
     * Upon receiving a broadcast, rout it to the proper callbacks.
     *
     * @param context the context of the broadcast
     * @param intent data about the broadcast which was sent
     */
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        for (callback in allCallbacks.toList()) {
            callback.onPackageUpdate(packageName)
        }

        if (intent.action != Intent.ACTION_PACKAGE_ADDED) {
            changeCallbacks[packageName]?.toList()?.let { callbacks ->
                for (callback in callbacks) {
                    callback.onPackageUpdate(packageName)
                }
            }
        }

        if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            // Invalidate all livedatas associated with this package
            LightPackageInfoLiveData.invalidateAllForPackage(packageName)
            PermStateLiveData.invalidateAllForPackage(packageName)
            PackagePermissionsLiveData.invalidateAllForPackage(packageName)
            AutoRevokeStateLiveData.invalidateAllForPackage(packageName)
            LightAppPermGroupLiveData.invalidateAllForPackage(packageName)
            AppPermGroupUiInfoLiveData.invalidateAllForPackage(packageName)
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