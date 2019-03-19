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

import static com.android.packageinstaller.Constants.ALLOW_OVERRIDE_USER_SENSITIVE_KEY;
import static com.android.packageinstaller.Constants.PREFERENCES_FILE;
import static com.android.packageinstaller.permission.utils.Utils.getParentUserContext;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.android.packageinstaller.Constants;

/**
 * Is the user allowed to manually mark apps as user sensitive.
 *
 * <p>Data source: {@link Constants#ALLOW_OVERRIDE_USER_SENSITIVE_KEY} shared preference.
 */
public class AllowOverrideUserSensitiveLiveData extends LiveData<Boolean> implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static AllowOverrideUserSensitiveLiveData sInstance;

    private final SharedPreferences mPrefs;

    /**
     * Get a (potentially shared) live data.
     *
     * @param application The application context
     *
     * @return The live data
     */
    @MainThread
    public static AllowOverrideUserSensitiveLiveData get(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new AllowOverrideUserSensitiveLiveData(application);
        }

        return sInstance;
    }

    private AllowOverrideUserSensitiveLiveData(@NonNull Application application) {
        mPrefs = getParentUserContext(application).getSharedPreferences(PREFERENCES_FILE,
                MODE_PRIVATE);
    }

    @Override
    protected void onActive() {
        onSharedPreferenceChanged(mPrefs, ALLOW_OVERRIDE_USER_SENSITIVE_KEY);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onInactive() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(ALLOW_OVERRIDE_USER_SENSITIVE_KEY)) {
            setValue(sharedPreferences.getBoolean(ALLOW_OVERRIDE_USER_SENSITIVE_KEY, false));
        }
    }
}
