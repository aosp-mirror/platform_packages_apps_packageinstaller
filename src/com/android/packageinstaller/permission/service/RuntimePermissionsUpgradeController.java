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

import static com.android.packageinstaller.PermissionControllerStatsLog.RUNTIME_PERMISSIONS_UPGRADE_RESULT;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.permission.PermissionManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.utils.ArrayUtils;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class handles upgrading the runtime permissions database
 */
class RuntimePermissionsUpgradeController {
    private static final String LOG_TAG = RuntimePermissionsUpgradeController.class.getSimpleName();

    // The latest version of the runtime permissions database
    private static final int LATEST_VERSION = 8;

    private RuntimePermissionsUpgradeController() {
        /* do nothing - hide constructor */
    }

    static void upgradeIfNeeded(@NonNull Context context) {
        final PermissionManager permissionManager = context.getSystemService(
                PermissionManager.class);
        final int currentVersion = permissionManager.getRuntimePermissionsVersion();

        whitelistAllSystemAppPermissions(context);

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
     * Whitelist permissions of system-apps.
     *
     * <p>Apps that are updated via OTAs are never installed. Hence their permission are never
     * whitelisted. This code replaces that by always whitelisting them.
     *
     * @param context A context to talk to the platform
     */
    private static void whitelistAllSystemAppPermissions(@NonNull Context context) {
        // Only whitelist permissions that are in the OTA. For non-OTA updates the installer should
        // do the white-listing
        final List<PackageInfo> apps = context.getPackageManager()
                .getInstalledPackages(PackageManager.GET_PERMISSIONS
                        | PackageManager.MATCH_UNINSTALLED_PACKAGES
                        | PackageManager.MATCH_FACTORY_ONLY);

        // Cache permissionInfos
        final ArrayMap<String, PermissionInfo> permissionInfos = new ArrayMap<>();

        final int appCount = apps.size();
        for (int i = 0; i < appCount; i++) {
            final PackageInfo app = apps.get(i);

            if (app.requestedPermissions == null) {
                continue;
            }

            for (String requestedPermission : app.requestedPermissions) {
                PermissionInfo permInfo = permissionInfos.get(requestedPermission);
                if (permInfo == null) {
                    try {
                        permInfo = context.getPackageManager().getPermissionInfo(
                                requestedPermission, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        continue;
                    }

                    permissionInfos.put(requestedPermission, permInfo);
                }

                if ((permInfo.flags & (PermissionInfo.FLAG_HARD_RESTRICTED
                        | PermissionInfo.FLAG_SOFT_RESTRICTED)) == 0) {
                    continue;
                }

                context.getPackageManager().addWhitelistedRestrictedPermission(
                        app.packageName, requestedPermission,
                        PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE);
            }
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
            // moved to step 5->6 to clean up broken permission state during dogfooding
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
            // moved to step 5->6 to clean up broken permission state during beta 4->5 upgrade
            currentVersion = 5;
        }

        if (currentVersion == 5) {
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
            currentVersion = 6;
        }

        if (currentVersion == 6) {
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

                            logRuntimePermissionUpgradeResult(bgGroup,
                                    app.applicationInfo.uid, app.packageName);
                        }

                        break;
                    }
                }
            } else {
                Log.i(LOG_TAG, "Not expanding location permissions as this is not an upgrade "
                        + "from Android P");
            }

            currentVersion = 7;
        }

        if (currentVersion == 7) {
            Log.i(LOG_TAG, "Expanding read storage to access media location");

            for (int i = 0; i < appCount; i++) {
                final PackageInfo pkgInfo = apps.get(i);

                if (!ArrayUtils.contains(pkgInfo.requestedPermissions,
                        Manifest.permission.ACCESS_MEDIA_LOCATION)) {
                    continue;
                }

                if (context.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, 0,
                        pkgInfo.applicationInfo.uid) != PackageManager.PERMISSION_GRANTED) {
                    continue;
                }

                final AppPermissionGroup group = AppPermissionGroup.create(context, pkgInfo,
                        Manifest.permission.ACCESS_MEDIA_LOCATION, false);
                final Permission perm = group.getPermission(
                        Manifest.permission.ACCESS_MEDIA_LOCATION);

                if (!perm.isUserSet() && !perm.isSystemFixed() && !perm.isPolicyFixed()
                        && !perm.isGrantedIncludingAppOp()) {
                    group.grantRuntimePermissions(false,
                            new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION});

                    logRuntimePermissionUpgradeResult(group,
                            pkgInfo.applicationInfo.uid, pkgInfo.packageName,
                            Manifest.permission.ACCESS_MEDIA_LOCATION);
                }
            }

            currentVersion = 8;
        }

        // XXX: Add new upgrade steps above this point.

        return currentVersion;
    }

    private static void logRuntimePermissionUpgradeResult(AppPermissionGroup permissionGroup,
            int uid, String packageName, String... filterPermissions) {
        ArrayList<Permission> permissions = permissionGroup.getPermissions();
        int numPermissions = permissions.size();
        for (int i = 0; i < numPermissions; i++) {
            if (filterPermissions != null && !ArrayUtils.contains(filterPermissions, permissions)) {
                continue;
            }

            Permission permission = permissions.get(i);
            PermissionControllerStatsLog.write(RUNTIME_PERMISSIONS_UPGRADE_RESULT,
                    permission.getName(), uid, packageName);
            Log.v(LOG_TAG, "Runtime permission upgrade logged for permissionName="
                    + permission.getName() + " uid=" + uid + " packageName=" + packageName);
        }
    }
}
