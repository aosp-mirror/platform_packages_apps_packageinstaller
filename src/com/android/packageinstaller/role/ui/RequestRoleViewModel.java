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

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.packageinstaller.role.model.Role;

/**
 * {@link ViewModel} for a role request.
 */
public class RequestRoleViewModel extends DefaultAppViewModel {

    public RequestRoleViewModel(@NonNull Role role, @NonNull Application application) {
        super(role, Process.myUserHandle(), application);
    }

    /**
     * {@link ViewModelProvider.Factory} for {@link RequestRoleViewModel}.
     */
    public static class Factory implements ViewModelProvider.Factory {

        @NonNull
        private Role mRole;

        @NonNull
        private Application mApplication;

        public Factory(@NonNull Role role, @NonNull Application application) {
            mRole = role;
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection unchecked
            return (T) new RequestRoleViewModel(mRole, mApplication);
        }
    }
}
