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

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * Live data of the users of the current profile group.
 *
 * <p>Data source: system server
 */
class UsersLiveData extends LiveData<List<UserHandle>> {
    private static UsersLiveData sInstance;

    private final Application mApplication;

    /** Monitors changes to the users on this device */
    private BroadcastReceiver mUserMonitor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    /**
     * Get a (potentially shared) live data.
     *
     * @param application The application context
     *
     * @return The live data
     */
    @MainThread
    public static UsersLiveData get(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new UsersLiveData(application);
        }

        return sInstance;
    }

    private UsersLiveData(@NonNull Application application) {
        mApplication = application;
    }

    /**
     * Update the encapsulated data with the current list of users.
     */
    private void update() {
        setValue(mApplication.getSystemService(UserManager.class).getUserProfiles());
    }

    @Override
    protected void onActive() {
        update();

        IntentFilter userChangeFilter = new IntentFilter();
        userChangeFilter.addAction(Intent.ACTION_USER_ADDED);
        userChangeFilter.addAction(Intent.ACTION_USER_REMOVED);

        mApplication.registerReceiver(mUserMonitor, userChangeFilter);
    }

    @Override
    protected void onInactive() {
        mApplication.unregisterReceiver(mUserMonitor);
    }
}
