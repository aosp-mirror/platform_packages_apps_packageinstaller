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
 * {@link LiveData} for the state of managing a role holder.
 */
public class ManageRoleHolderStateLiveData extends LiveData<Integer> {

    private static final String LOG_TAG = ManageRoleHolderStateLiveData.class.getSimpleName();

    private static final boolean DEBUG = false;

    public static final int STATE_IDLE = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_SUCCESS = 2;
    public static final int STATE_FAILURE = 3;

    public ManageRoleHolderStateLiveData() {
        setValue(STATE_IDLE);
    }

    /**
     * Set whether an application is a holder of a role, and update the state accordingly. Will
     * be no-op if the current state is not {@link #STATE_IDLE}.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     * @param add whether to add or remove the application as a role holder
     * @param user the user to manage the role holder for
     * @param context the {@code Context} to retrieve system services
     */
    public void setRoleHolderAsUser(@NonNull String roleName, @NonNull String packageName,
            boolean add, @NonNull UserHandle user, @NonNull Context context) {
        if (getValue() != STATE_IDLE) {
            Log.e(LOG_TAG, "Already (tried) managing role holders, requested role: " + roleName
                    + ", requested package: " + packageName);
            return;
        }
        if (DEBUG) {
            Log.i(LOG_TAG, (add ? "Adding" : "Removing") + " package as role holder, role: "
                    + roleName + ", package: " + packageName);
        }
        setValue(STATE_WORKING);

        RoleManager roleManager = context.getSystemService(RoleManager.class);
        Executor executor = context.getMainExecutor();
        RoleManagerCallback callback = new RoleManagerCallback() {
            @Override
            public void onSuccess() {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Package " + (add ? "added" : "removed")
                            + " as role holder, role: " + roleName + ", package: " + packageName);
                }
                setValue(STATE_SUCCESS);
            }
            @Override
            public void onFailure() {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Failed to " + (add ? "add" : "remove")
                            + " package as role holder, role: " + roleName + ", package: "
                            + packageName);
                }
                setValue(STATE_FAILURE);
            }
        };
        if (add) {
            roleManager.addRoleHolderAsUser(roleName, packageName, user, executor, callback);
        } else {
            roleManager.removeRoleHolderAsUser(roleName, packageName, user, executor, callback);
        }
    }

    /**
     * Clear the holders of a role, and update the state accordingly. Will be no-op if the current
     * state is not {@link #STATE_IDLE}.
     *
     * @param roleName the name of the role
     * @param user the user to manage the role holder for
     * @param context the {@code Context} to retrieve system services
     */
    public void clearRoleHoldersAsUser(@NonNull String roleName, @NonNull UserHandle user,
            @NonNull Context context) {
        if (getValue() != STATE_IDLE) {
            Log.e(LOG_TAG, "Already (tried) managing role holders, requested role: " + roleName);
            return;
        }
        if (DEBUG) {
            Log.i(LOG_TAG, "Clearing role holders, role: " + roleName);
        }
        setValue(STATE_WORKING);

        RoleManager roleManager = context.getSystemService(RoleManager.class);
        Executor executor = context.getMainExecutor();
        RoleManagerCallback callback = new RoleManagerCallback() {
            @Override
            public void onSuccess() {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Cleared role holders, role: " + roleName);
                }
                setValue(STATE_SUCCESS);
            }
            @Override
            public void onFailure() {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Failed to clear role holders, role: " + roleName);
                }
                setValue(STATE_FAILURE);
            }
        };
        roleManager.clearRoleHoldersAsUser(roleName, user, executor, callback);
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
