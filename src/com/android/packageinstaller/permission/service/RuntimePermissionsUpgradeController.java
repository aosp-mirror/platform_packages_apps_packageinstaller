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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.List;

/**
 * This class handles upgrading the runtime permissions database
 */
class RuntimePermissionsUpgradeController {
    private static final String LOG_TAG = RuntimePermissionsUpgradeController.class.getSimpleName();

    // The latest version of the runtime permissions database
    private static final int LATEST_VERSION = 5;

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
        final List<PackageInfo> apps = context.getPackageManager()
                .getInstalledPackages(PackageManager.MATCH_ALL
                        | PackageManager.GET_PERMISSIONS);
        final int appCount = apps.size();

        final boolean sdkUpgradedFromP;
        if (currentVersion <= -1) {
            Log.i(LOG_TAG, "Upgrading from Android P");

            sdkUpgradedFromP = true;

            currentVersion = 0;
        } else {
            sdkUpgradedFromP = false;
        }

        if (currentVersion == 0) {
            Log.i(LOG_TAG, "Grandfathering SMS and CallLog permissions");

            final List<String> smsPermissions = Utils.getPlatformPermissionNamesOfGroup(
                    android.Manifest.permission_group.SMS);
            final List<String> callLogPermissions = Utils.getPlatformPermissionNamesOfGroup(
                    Manifest.permission_group.CALL_LOG);

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

        if (currentVersion == 1) {
            // moved to step 4->5 as it has to be after the grandfathering of loc bg perms
            currentVersion = 2;
        }

        if (currentVersion == 2) {
            Log.i(LOG_TAG, "Grandfathering Storage permissions");

            final List<String> storagePermissions = Utils.getPlatformPermissionNamesOfGroup(
                    Manifest.permission_group.STORAGE);

            for (int i = 0; i < appCount; i++) {
                final PackageInfo app = apps.get(i);
                if (app.requestedPermissions == null) {
                    continue;
                }

                // We don't want to allow modification of storage post install, so put it
                // on the internal system whitelist to prevent the installer changing it.
                for (String requestedPermission : app.requestedPermissions) {
                    if (storagePermissions.contains(requestedPermission)) {
                        context.getPackageManager().addWhitelistedRestrictedPermission(
                                app.packageName, requestedPermission,
                                PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);
                    }
                }
            }
            currentVersion = 3;
        }

        if (currentVersion == 3) {
            Log.i(LOG_TAG, "Grandfathering location background permissions");

            for (int i = 0; i < appCount; i++) {
                final PackageInfo app = apps.get(i);
                if (app.requestedPermissions == null) {
                    continue;
                }

                for (String requestedPermission : app.requestedPermissions) {
                    if (requestedPermission.equals(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        context.getPackageManager().addWhitelistedRestrictedPermission(
                                app.packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);
                        break;
                    }
                }
            }
            currentVersion = 4;
        }

        if (currentVersion == 4) {
            if (sdkUpgradedFromP) {
                Log.i(LOG_TAG, "Expanding location permissions");

                for (int i = 0; i < appCount; i++) {
                    final PackageInfo app = apps.get(i);
                    if (app.requestedPermissions == null) {
                        continue;
                    }

                    for (String perm : app.requestedPermissions) {
                        String groupName = Utils.getGroupOfPlatformPermission(perm);

                        if (!TextUtils.equals(groupName, Manifest.permission_group.LOCATION)) {
                            continue;
                        }

                        final AppPermissionGroup group = AppPermissionGroup.create(context, app,
                                perm, false);
                        final AppPermissionGroup bgGroup = group.getBackgroundPermissions();

                        if (group.areRuntimePermissionsGranted()
                                && bgGroup != null
                                && !bgGroup.isUserSet() && !bgGroup.isSystemFixed()
                                && !bgGroup.isPolicyFixed()) {
                            bgGroup.grantRuntimePermissions(group.isUserFixed());
                        }

                        break;
                    }
                }
            } else {
                Log.i(LOG_TAG, "Not expanding location permissions as this is not an upgrade "
                        + "from Android P");
            }

            currentVersion = 5;
        }

        // XXX: Add new upgrade steps above this point.

        return currentVersion;
    }
}
