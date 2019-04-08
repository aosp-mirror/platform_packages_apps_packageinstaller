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

package com.android.packageinstaller.permission.service;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.permission.PermissionManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.packageinstaller.permission.utils.Utils;

import java.util.List;

/**
 * This class handles upgrading the runtime permissions database
 */
class RuntimePermissionsUpgradeController {

    // The latest version of the runtime permissions database
    private static final int LATEST_VERSION = 1;

    private RuntimePermissionsUpgradeController() {
        /* do nothing - hide constructor */
    }

    static void upgradeIfNeeded(@NonNull Context context) {
        final PermissionManager permissionManager = context.getSystemService(
                PermissionManager.class);
        final int currentVersion = permissionManager.getRuntimePermissionsVersion();

        final int upgradedVersion = onUpgradeLocked(context, currentVersion);

        if (upgradedVersion != LATEST_VERSION) {
            Log.wtf("PermissionControllerService", "warning: upgrading permission database"
                            + " to version " + LATEST_VERSION + " left it at " + currentVersion
                            + " instead; this is probably a bug. Did you update LATEST_VERSION?",
                    new Throwable());
            throw new RuntimeException("db upgrade error");
        }

        if (currentVersion != upgradedVersion) {
            permissionManager.setRuntimePermissionsVersion(LATEST_VERSION);
        }
    }

    /**
     * You must perform all necessary mutations to bring the runtime permissions
     * database from the old to the new version. When you add a new upgrade step
     * you *must* update LATEST_VERSION.
     *
     * @param context Context to access APIs.
     * @param currentVersion The current db version.
     */
    private static int onUpgradeLocked(@NonNull Context context, int currentVersion) {
        // Grandfather SMS and CallLog permissions.
        if (currentVersion <= 0) {
            final List<String> smsPermissions = Utils.getPlatformPermissionNamesOfGroup(
                    android.Manifest.permission_group.SMS);
            final List<String> callLogPermissions = Utils.getPlatformPermissionNamesOfGroup(
                    Manifest.permission_group.CALL_LOG);

            final List<PackageInfo> apps = context.getPackageManager()
                    .getInstalledPackages(PackageManager.MATCH_ALL
                            | PackageManager.GET_PERMISSIONS);

            final int appCount = apps.size();
            for (int i = 0; i < appCount; i++) {
                final PackageInfo app = apps.get(i);
                if (app.requestedPermissions == null) {
                    continue;
                }

                for (String requestedPermission : app.requestedPermissions) {
                    if (smsPermissions.contains(requestedPermission)
                            || callLogPermissions.contains(requestedPermission)) {
                        context.getPackageManager().addWhitelistedRestrictedPermission(
                                app.packageName, requestedPermission,
                                PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);
                    }
                }
            }
            currentVersion = 1;
        }

        // XXX: Add new upgrade steps above this point.

        return currentVersion;
    }
}
