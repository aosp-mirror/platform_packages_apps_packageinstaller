/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller.role.ui;

import android.app.Application;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;

import com.android.packageinstaller.role.utils.UserUtils;

/**
 * {@link ViewModel} for the list of default apps.
 */
public class DefaultAppListViewModel extends AndroidViewModel {

    @NonNull
    private final UserHandle mUser;
    @NonNull
    private final RoleListLiveData mLiveData;
    @Nullable
    private final UserHandle mWorkProfile;
    @Nullable
    private final RoleListLiveData mWorkLiveData;

    public DefaultAppListViewModel(@NonNull Application application) {
        super(application);

        mUser = Process.myUserHandle();
        mLiveData = new RoleListLiveData(true, mUser, application);
        mWorkProfile = UserUtils.getWorkProfile(application);
        mWorkLiveData = mWorkProfile != null ? new RoleListLiveData(true, mWorkProfile, application)
                : null;
    }

    @NonNull
    public UserHandle getUser() {
        return mUser;
    }

    @NonNull
    public RoleListLiveData getLiveData() {
        return mLiveData;
    }

    /**
     * Check whether the user has a work profile.
     *
     * @return whether the user has a work profile.
     */
    public boolean hasWorkProfile() {
        return mWorkProfile != null;
    }

    @Nullable
    public UserHandle getWorkProfile() {
        return mWorkProfile;
    }

    @Nullable
    public RoleListLiveData getWorkLiveData() {
        return mWorkLiveData;
    }
}
