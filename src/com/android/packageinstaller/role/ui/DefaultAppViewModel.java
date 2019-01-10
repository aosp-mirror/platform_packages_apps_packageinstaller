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
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.packageinstaller.role.model.Role;

/**
 * {@link ViewModel} for a default app.
 */
public class DefaultAppViewModel extends AndroidViewModel {

    @NonNull
    private final RoleLiveData mRoleLiveData;

    @NonNull
    private final ManageRoleHolderStateLiveData mManageRoleHolderStateLiveData =
            new ManageRoleHolderStateLiveData();

    public DefaultAppViewModel(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Application application) {
        super(application);

        mRoleLiveData = new RoleLiveData(role, user, application);
    }

    @NonNull
    public RoleLiveData getRoleLiveData() {
        return mRoleLiveData;
    }

    @NonNull
    public ManageRoleHolderStateLiveData getManageRoleHolderStateLiveData() {
        return mManageRoleHolderStateLiveData;
    }

    /**
     * {@link ViewModelProvider.Factory} for {@link DefaultAppViewModel}.
     */
    public static class Factory implements ViewModelProvider.Factory {

        @NonNull
        private Role mRole;

        @NonNull
        private UserHandle mUser;

        @NonNull
        private Application mApplication;

        public Factory(@NonNull Role role, @NonNull UserHandle user,
                @NonNull Application application) {
            mRole = role;
            mUser = user;
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection unchecked
            return (T) new DefaultAppViewModel(mRole, mUser, mApplication);
        }
    }
}
