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

import androidx.lifecycle.MediatorLiveData

/**
 * A MediatorLiveData which tracks how long it has been inactive, compares new values before setting
 * its value (avoiding unnecessary updates), and can calculate the set difference between a list
 * and a map (used when determining whether or not to add a LiveData as a source).
 */
open class SmartUpdateMediatorLiveData<T> : MediatorLiveData<T>(),
    DataRepository.InactiveTimekeeper {

    /**
     * Boolean, whether or not the value of this uiDataLiveData has been explicitly set yet
     */
    var isInitialized = false
        private set

    override fun setValue(newValue: T?) {
        if (!isInitialized) {
            isInitialized = true
        }

        if (valueNotEqual(super.getValue(), newValue)) {
            super.setValue(newValue)
        }
    }

    override var timeWentInactive: Long? = null

    /**
     * Some LiveDatas have types, like Drawables which do not have a non-default equals method.
     * Those classes can override this method to change when the value is set upon calling setValue.
     *
     * @param valOne: The first T to be compared
     * @param valTwo: The second T to be compared
     *
     * @return True if the two values are different, false otherwise
     */
    protected open fun valueNotEqual(valOne: T?, valTwo: T?): Boolean {
        return valOne != valTwo
    }

    override fun onActive() {
        timeWentInactive = null
        super.onActive()
    }

    override fun onInactive() {
        timeWentInactive = System.nanoTime()
        super.onInactive()
    }
}