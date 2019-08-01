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

/**
 * Repository for AppOpModeChangeListenerMultiplexers.
 * Key value is a String app op name, value is its corresponding Listener.
 */
object AppOpRepository : DataRepository<String, AppOpModeChangeListenerMultiplexer>() {

    /**
     * Gets the AppOpChangeListenerMultiplexer associated with the given op, creating and
     * caching it if needed.
     *
     * @param app: The application this is being called from
     * @param op: The op we wish to get the listener for
     *
     * @return The cached or newly created AppOpModeChangeListenerMultiplexer for the given op
     */
    fun getAppOpChangeListener(app: Application, op: String):
        AppOpModeChangeListenerMultiplexer {
        return getDataObject(app, op)
    }

    override fun newValue(app: Application, op: String): AppOpModeChangeListenerMultiplexer {
        return AppOpModeChangeListenerMultiplexer(app, op)
    }
}