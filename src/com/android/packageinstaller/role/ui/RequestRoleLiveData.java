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

import android.app.role.RoleManager;
import android.app.role.RoleManagerCallback;
import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.concurrent.Executor;

/**
 * {@link LiveData} for the state of a role request.
 */
public class RequestRoleLiveData extends LiveData<Integer> {

    private static final String LOG_TAG = RequestRoleLiveData.class.getSimpleName();

    public static final int STATE_IDLE = 0;
    public static final int STATE_ADDING = 1;
    public static final int STATE_SUCCESS = 2;
    public static final int STATE_FAILURE = 3;

    public RequestRoleLiveData() {
        setValue(STATE_IDLE);
    }

    /**
     * Add an application to the holders of a role, and update the state accordingly. Will be no-op
     * if already called once.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     */
    public void addRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @NonNull Context context) {
        if (getValue() != STATE_IDLE) {
            Log.w(LOG_TAG, "Already (tried) adding package as role holder, requested role: "
                    + roleName + ", requested package: " + packageName);
            return;
        }
        Log.i(LOG_TAG, "Adding package as role holder, role: " + roleName + ", package: "
                + packageName);
        setValue(STATE_ADDING);

        RoleManager roleManager = context.getSystemService(RoleManager.class);
        UserHandle user = UserHandle.of(UserHandle.myUserId());
        Executor executor = context.getMainExecutor();
        roleManager.addRoleHolderAsUser(roleName, packageName, user, executor,
                new RoleManagerCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(LOG_TAG, "Package added as role holder, role: " + roleName
                                + ", package: " + packageName);
                        setValue(STATE_SUCCESS);
                    }
                    @Override
                    public void onFailure() {
                        Log.i(LOG_TAG, "Failed to add package as role holder, role: " + roleName
                                + ", package: " + packageName);
                        setValue(STATE_FAILURE);
                    }
                });
    }
}
