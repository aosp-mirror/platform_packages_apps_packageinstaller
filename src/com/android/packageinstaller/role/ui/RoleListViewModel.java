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
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.Objects;

/**
 * {@link ViewModel} for a list of roles.
 */
public class RoleListViewModel extends AndroidViewModel {

    @NonNull
    private final UserHandle mUser;
    @NonNull
    private final RoleListLiveData mUserLiveData;
    @Nullable
    private final UserHandle mWorkProfile;
    @Nullable
    private final RoleListLiveData mWorkLiveData;

    public RoleListViewModel(boolean exclusive, @NonNull Application application) {
        super(application);

        mUser = Process.myUserHandle();
        mUserLiveData = new RoleListLiveData(exclusive, mUser, application);
        mWorkProfile = getWorkProfile(application);
        mWorkLiveData = mWorkProfile != null ? new RoleListLiveData(exclusive, mWorkProfile,
                application) : null;
    }

    @Nullable
    private static UserHandle getWorkProfile(@NonNull Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        List<UserHandle> profiles = userManager.getUserProfiles();
        UserHandle user = Process.myUserHandle();

        int profilesSize = profiles.size();
        for (int i = 0; i < profilesSize; i++) {
            UserHandle profile = profiles.get(i);

            if (Objects.equals(profile, user)) {
                continue;
            }
            if (!userManager.isManagedProfile(profile.getIdentifier())) {
                continue;
            }
            return profile;
        }
        return null;
    }

    @NonNull
    public UserHandle getUser() {
        return mUser;
    }

    @NonNull
    public RoleListLiveData getLiveData() {
        return mUserLiveData;
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

    /**
     * {@link ViewModelProvider.Factory} for {@link RoleListViewModel}.
     */
    public static class Factory implements ViewModelProvider.Factory {

        private boolean mExclusive;

        @NonNull
        private Application mApplication;

        public Factory(boolean exclusive, @NonNull Application application) {
            mExclusive = exclusive;
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection unchecked
            return (T) new RoleListViewModel(mExclusive, mApplication);
        }
    }
}
