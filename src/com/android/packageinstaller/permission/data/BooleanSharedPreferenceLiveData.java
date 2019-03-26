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

package com.android.packageinstaller.permission.data;

import static android.content.Context.MODE_PRIVATE;

import static com.android.packageinstaller.Constants.PREFERENCES_FILE;
import static com.android.packageinstaller.permission.utils.Utils.getParentUserContext;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.ArrayMap;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

/**
 * Get a live data for a boolean shared preference.
 *
 * <p>Data source: shared preferences
 */
public class BooleanSharedPreferenceLiveData extends LiveData<Boolean> implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static ArrayMap<String, BooleanSharedPreferenceLiveData> sInstances = new ArrayMap<>();

    private final @NonNull SharedPreferences mPrefs;
    private final @NonNull String mKey;

    /**
     * Get a (potentially shared) live data.
     *
     * @param key The key of the shared preference to listen for
     * @param application The application context
     *
     * @return The live data
     */
    @MainThread
    public static BooleanSharedPreferenceLiveData get(@NonNull String key,
            @NonNull Application application) {
        if (sInstances.get(key) == null) {
            sInstances.put(key, new BooleanSharedPreferenceLiveData(key, application));
        }

        return sInstances.get(key);
    }

    private BooleanSharedPreferenceLiveData(@NonNull String key, @NonNull Application application) {
        mPrefs = getParentUserContext(application).getSharedPreferences(PREFERENCES_FILE,
                MODE_PRIVATE);
        mKey = key;
    }

    @Override
    protected void onActive() {
        onSharedPreferenceChanged(mPrefs, mKey);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onInactive() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mKey.equals(key)) {
            setValue(sharedPreferences.getBoolean(mKey, false));
        }
    }
}
