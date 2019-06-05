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
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import java.util.concurrent.TimeUnit

/**
 * A generalize data repository, which carries a component callback which trims its data in response
 * to memory pressure
 */
abstract class DataRepository<K, V : DataRepository.InactiveTimekeeper> : ComponentCallbacks2 {

    /**
     * Deadlines for removal based on memory pressure. Live Data objects which have been inactive
     * for longer than the deadline will be removed.
     */
    private val TIME_THRESHOLD_LAX_NANOS: Long = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MINUTES)
    private val TIME_THRESHOLD_TIGHT_NANOS: Long = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MINUTES)
    private val TIME_THRESHOLD_ALL_NANOS: Long = 0

    private val data = mutableMapOf<K, V>()

    /**
     * Whether or not this data repository has been registered as a component callback yet
     */
    private var registered = false

    /**
     * Get a value from this repository, creating it if needed
     *
     * @param app: The application this is being called from
     * @param key: The key associated with the desired Value
     *
     * @return The cached or newly created Value for the given Key
     */
    protected fun getDataObject(app: Application, key: K): V {
        if (!registered) {
            app.registerComponentCallbacks(this)
            registered = true
        }

        return data.getOrPut(key) { newValue(app, key) }
    }

    /**
     * Generate a new value type from the given data
     *
     * @param app: The application this is being called from
     * @param key: Information about this value object, used to instantiate it
     *
     * @return The generated Value
     */
    protected abstract fun newValue(app: Application, key: K): V

    /**
     * Remove LiveData objects with no observer based on the severity of the memory pressure.
     *
     * @param level The severity of the current memory pressure
     */
    override fun onTrimMemory(level: Int) {

        trimInactiveData(threshold = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> TIME_THRESHOLD_LAX_NANOS
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> TIME_THRESHOLD_TIGHT_NANOS
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> TIME_THRESHOLD_ALL_NANOS
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> TIME_THRESHOLD_LAX_NANOS
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> TIME_THRESHOLD_TIGHT_NANOS
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> TIME_THRESHOLD_ALL_NANOS
            else -> return
        })
    }

    override fun onLowMemory() {
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Do nothing, but required to override by interface
    }

    private fun trimInactiveData(threshold: Long) {
        data.entries.removeAll { (_, value) ->
            value.timeInactive?.let { it >= threshold } ?: false
        }
    }

    /**
     * Interface which describes an object which can track how long it has been inactive, and if
     * it has any observers.
     */
    interface InactiveTimekeeper {

        /**
         * Long value representing the time this object went inactive, which is read only on the
         * main thread, so does not cause race conditions.
         */
        var timeWentInactive: Long?

        /**
         * Calculates the time since this object went inactive.
         *
         * @return The time since this object went inactive, or null if it is not inactive
         */
        val timeInactive: Long?
            get() {
                if (hasObservers()) {
                    return null
                }
                val time = timeWentInactive ?: return null
                return System.nanoTime() - time
            }

        /**
         * Whether or not this object has any objects observing it (observers need not be active).
         *
         * @return Whether or not this object has any Observers
         */
        fun hasObservers(): Boolean
    }
}