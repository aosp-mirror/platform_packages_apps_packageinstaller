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

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * {@link ViewModel} for a list of roles.
 */
public class RoleListViewModel extends AndroidViewModel {

    @NonNull
    private final RoleListLiveData mLiveData;

    public RoleListViewModel(boolean exclusive, @NonNull Application application) {
        super(application);

        mLiveData = new RoleListLiveData(exclusive, application);
    }

    @NonNull
    public RoleListLiveData getLiveData() {
        return mLiveData;
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
