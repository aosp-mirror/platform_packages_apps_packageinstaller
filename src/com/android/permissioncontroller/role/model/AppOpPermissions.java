/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.role.model;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.permissioncontroller.permission.utils.ArrayUtils;
import com.android.permissioncontroller.role.utils.PackageUtils;

/**
 * App op permissions to be granted or revoke by a {@link Role}.
 */
public class AppOpPermissions {

    private AppOpPermissions() {}

    /**
     * Grant the app op of an app op permission to an application.
     *
     * @param packageName the package name of the application
     * @param appOpPermission the name of the app op permission
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app op mode has changed
     */
    public static boolean grant(@NonNull String packageName, @NonNull String appOpPermission,
            @NonNull Context context) {
        PackageInfo packageInfo = PackageUtils.getPackageInfo(packageName,
                PackageManager.GET_PERMISSIONS, context);
        if (packageInfo == null) {
            return false;
        }
        if (!ArrayUtils.contains(packageInfo.requestedPermissions, appOpPermission)) {
            return false;
        }
        String appOp = AppOpsManager.permissionToOp(appOpPermission);
        return setAppOpMode(packageName, appOp, AppOpsManager.MODE_ALLOWED, context);
    }

    /**
     * Revoke the app op of an app op permission from an application.
     *
     * @param packageName the package name of the application
     * @param appOpPermission the name of the app op permission
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app op mode has changed
     */
    public static boolean revoke(@NonNull String packageName, @NonNull String appOpPermission,
            @NonNull Context context) {
        String appOp = AppOpsManager.permissionToOp(appOpPermission);
        int defaultMode = Permissions.getDefaultAppOpMode(appOp);
        return setAppOpMode(packageName, appOp, defaultMode, context);
    }

    private static boolean setAppOpMode(@NonNull String packageName, @NonNull String appOp,
            int mode, @NonNull Context context) {
        switch (appOp) {
            case AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS:
            case AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW:
            case AppOpsManager.OPSTR_WRITE_SETTINGS:
            case AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES:
            case AppOpsManager.OPSTR_START_FOREGROUND:
            // This isn't an API but we are deprecating it soon anyway.
            //case AppOpsManager.OPSTR_SMS_FINANCIAL_TRANSACTIONS:
            case AppOpsManager.OPSTR_MANAGE_IPSEC_TUNNELS:
            case AppOpsManager.OPSTR_INSTANT_APP_START_FOREGROUND:
            case AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES:
            case AppOpsManager.OPSTR_LOADER_USAGE_STATS:
                return Permissions.setAppOpPackageMode(packageName, appOp, mode, context);
            default:
                return Permissions.setAppOpUidMode(packageName, appOp, mode, context);
        }
    }
}
