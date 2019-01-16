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
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Provides access to all the default role holders.
 */
public class DefaultRoleHolders {

    private static final String LOG_TAG = DefaultRoleHolders.class.getSimpleName();

    @NonNull
    private static final Object sLock = new Object();

    private static ArrayMap<String, List<String>> sDefaultRoleHolders;

    /**
     * Get the default role holders.
     *
     * @param context the {@code Context} used to read the system resource
     *
     * @return a map from role name to a list of package names of the default holders
     */
    @NonNull
    public static ArrayMap<String, List<String>> get(@NonNull Context context) {
        synchronized (sLock) {
            if (sDefaultRoleHolders == null) {
                sDefaultRoleHolders = load(context);
            }
            return sDefaultRoleHolders;
        }
    }

    @NonNull
    private static ArrayMap<String, List<String>> load(@NonNull Context context) {
        ArrayMap<String, List<String>> defaultRoleHolders = new ArrayMap<>();
        String[] items = context.getResources().getStringArray(
                android.R.array.config_defaultRoleHolders);
        int itemsLength = items.length;
        for (int i = 0; i < itemsLength; i++) {
            String item = items[i];

            item = item.trim();
            String[] roleNameAndPackageNames = item.split("\\s*:\\s*", 2);
            if (roleNameAndPackageNames.length != 2) {
                Log.e(LOG_TAG, "Invalid item: " + item);
                continue;
            }
            String roleName = roleNameAndPackageNames[0];
            if (roleName.isEmpty()) {
                Log.e(LOG_TAG, "Empty role name: " + item);
                continue;
            }
            if (defaultRoleHolders.containsKey(roleName)) {
                Log.e(LOG_TAG, "Duplicate role name: " + roleName);
                continue;
            }
            String packageNamesString = roleNameAndPackageNames[1];
            if (packageNamesString.isEmpty()) {
                Log.e(LOG_TAG, "Empty package names: " + item);
                continue;
            }
            List<String> packageNames = Arrays.asList(packageNamesString.split("\\s*,\\s*"));
            ArraySet<String> uniquePackageNames = new ArraySet<>(packageNames);
            if (packageNames.size() != uniquePackageNames.size()) {
                Log.e(LOG_TAG, "Duplicate package names: " + packageNamesString);
                packageNames.clear();
                packageNames.addAll(uniquePackageNames);
            }
            defaultRoleHolders.put(roleName, packageNames);
        }
        return defaultRoleHolders;
    }
}
