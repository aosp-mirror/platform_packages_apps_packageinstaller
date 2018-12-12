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
 * {@link LiveData} for the state of adding a role holder.
 */
public class AddRoleHolderStateLiveData extends LiveData<Integer> {

    private static final String LOG_TAG = AddRoleHolderStateLiveData.class.getSimpleName();

    public static final int STATE_IDLE = 0;
    public static final int STATE_ADDING = 1;
    public static final int STATE_SUCCESS = 2;
    public static final int STATE_FAILURE = 3;

    public AddRoleHolderStateLiveData() {
        setValue(STATE_IDLE);
    }

    /**
     * Add an application to the holders of a role, and update the state accordingly. Will be no-op
     * if the current state is not {@link #STATE_IDLE}.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     * @param user the user to add the role holder for
     * @param context the {@code Context} to retrieve system services
     */
    public void addRoleHolderAsUser(@NonNull String roleName, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (getValue() != STATE_IDLE) {
            Log.e(LOG_TAG, "Already (tried) adding package as role holder, requested role: "
                    + roleName + ", requested package: " + packageName);
            return;
        }
        Log.i(LOG_TAG, "Adding package as role holder, role: " + roleName + ", package: "
                + packageName);
        setValue(STATE_ADDING);

        RoleManager roleManager = context.getSystemService(RoleManager.class);
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

    /**
     * Reset the state of this live data to {@link #STATE_IDLE}. Will be no-op if the current state
     * is not {@link #STATE_SUCCESS} or {@link #STATE_FAILURE}.
     */
    public void resetState() {
        int state = getValue();
        if (!(state == STATE_SUCCESS || state == STATE_FAILURE)) {
            Log.e(LOG_TAG, "Trying to reset state when the current state is not STATE_SUCCESS or"
                    + " STATE_FAILURE");
            return;
        }
        setValue(STATE_IDLE);
    }
}
