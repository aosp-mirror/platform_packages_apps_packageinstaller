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

package com.android.packageinstaller.role.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.Constants;

import java.util.Collections;
import java.util.Set;

/**
 * Manages user denied status for requesting roles.
 */
public class UserDeniedManager {

    @Nullable
    private static UserDeniedManager sInstance;

    private final SharedPreferences mPreferences;

    /**
     * Get a singleton instance of this class
     *
     * @param context the context for retrieving shared preferences.
     *
     * @return the singleton instance of this class
     */
    @NonNull
    public static UserDeniedManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new UserDeniedManager(context);
        }
        return sInstance;
    }

    private UserDeniedManager(@NonNull Context context) {
        context = context.getApplicationContext();
        mPreferences = context.getSharedPreferences(Constants.REQUEST_ROLE_USER_DENIED_FILE,
                Context.MODE_PRIVATE);
    }

    /**
     * Check whether an application has been denied for a role once.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     *
     * @return whether the application has been denied for the role once
     */
    public boolean isDeniedOnce(@NonNull String roleName, @NonNull String packageName) {
        return isDenied(roleName, packageName, false);
    }

    /**
     * Remember that an application has been denied for a role once.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     */
    public void setDeniedOnce(@NonNull String roleName, @NonNull String packageName) {
        setDenied(roleName, packageName, false, true);
    }

    /**
     * Check whether an application is always denied for a role.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     *
     * @return whether the application is always denied for the role
     */
    public boolean isDeniedAlways(@NonNull String roleName, @NonNull String packageName) {
        return isDenied(roleName, packageName, true);
    }

    /**
     * Remember that an application is always denied for a role.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     */
    public void setDeniedAlways(@NonNull String roleName, @NonNull String packageName) {
        setDenied(roleName, packageName, true, true);
    }

    /**
     * Forget about whether an application is denied for a role, once or always.
     *
     * @param roleName the name of the role
     * @param packageName the package name of the application
     */
    public void clearDenied(@NonNull String roleName, @NonNull String packageName) {
        setDenied(roleName, packageName, false, false);
        setDenied(roleName, packageName, true, false);
    }

    /**
     * Forget about whether an application is denied for any of the roles, once or always.
     *
     * @param packageName the package name of the application
     */
    public void clearPackageDenied(@NonNull String packageName) {
        mPreferences.edit()
                .remove(getKey(packageName, false))
                .remove(getKey(packageName, true))
                .apply();
    }

    @NonNull
    private static String getKey(@NonNull String packageName, boolean always) {
        return (always ? Constants.REQUEST_ROLE_USER_DENIED_ALWAYS_KEY_PREFIX
                : Constants.REQUEST_ROLE_USER_DENIED_ONCE_KEY_PREFIX) + packageName;
    }

    private boolean isDenied(@NonNull String roleName, @NonNull String packageName,
            boolean always) {
        String key = getKey(packageName, always);
        return mPreferences.getStringSet(key, Collections.emptySet()).contains(roleName);
    }

    private void setDenied(@NonNull String roleName, @NonNull String packageName, boolean always,
            boolean denied) {
        String key = getKey(packageName, always);
        Set<String> roleNames = mPreferences.getStringSet(key, Collections.emptySet());
        if (roleNames.contains(roleName) == denied) {
            return;
        }
        roleNames = new ArraySet<>(roleNames);
        if (denied) {
            roleNames.add(roleName);
        } else {
            roleNames.remove(roleName);
        }
        if (roleName.isEmpty()) {
            mPreferences.edit().remove(key).apply();
        } else {
            mPreferences.edit().putStringSet(key, roleNames).apply();
        }
    }
}
