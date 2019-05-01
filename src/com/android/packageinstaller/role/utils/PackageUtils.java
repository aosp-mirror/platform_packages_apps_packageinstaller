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

package com.android.packageinstaller.role.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility methods about application packages.
 */
public final class PackageUtils {

    private PackageUtils() {}

    /**
     * Retrieve the {@link PackageInfo} of an application.
     *
     * @param packageName the package name of the application
     * @param extraFlags the extra flags to pass to {@link PackageManager#getPackageInfo(String,
     *                   int)}
     * @param context the {@code Context} to retrieve system services
     *
     * @return the {@link PackageInfo} of the application, or {@code null} if not found
     */
    @Nullable
    public static PackageInfo getPackageInfo(@NonNull String packageName, int extraFlags,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(packageName, PackageManager.MATCH_DIRECT_BOOT_AWARE
                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE | extraFlags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Retrieve the {@link ApplicationInfo} of an application.
     *
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return the {@link ApplicationInfo} of the application, or {@code null} if not found
     */
    @Nullable
    public static ApplicationInfo getApplicationInfo(@NonNull String packageName,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getApplicationInfo(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Retrieve the {@link ApplicationInfo} of an application.
     *
     * @param packageName the package name of the application
     * @param user the user of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return the {@link ApplicationInfo} of the application, or {@code null} if not found
     */
    @Nullable
    public static ApplicationInfo getApplicationInfoAsUser(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        return getApplicationInfo(packageName, UserUtils.getUserContext(context, user));
    }
}
