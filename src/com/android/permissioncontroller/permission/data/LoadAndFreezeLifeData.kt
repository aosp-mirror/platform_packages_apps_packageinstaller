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

import androidx.lifecycle.SavedStateHandle

/**
 * LiveData that loads wrapped value once. Once the wrapped data becomes non-stale it holds onto the
 * value forever.
 *
 * This even extends over live-cycle events as the data is stored in the {@link SaveStateHandle}.
 * This means that the data has to be writable to {@link SavedStateHandle} though, i.e.
 * Serialzable, Parcelable, list, set, map, or a literal
 */
class LoadAndFreezeLifeData<T>(
    private val state: SavedStateHandle,
    private val key: String,
    private val wrapped: SmartUpdateMediatorLiveData<T>
) : SmartUpdateMediatorLiveData<T>() {
    init {
        if (state.get<T>(key) == null) {
            addSource(wrapped) { v ->
                if (wrapped.isInitialized) {
                    value = v

                    if (!wrapped.isStale) {
                        state.set(key, v)

                        removeSource(wrapped)
                    }
                }
            }
        } else {
            value = state[key]
        }
    }

    override fun onUpdate() {
        // do nothing
    }
}
