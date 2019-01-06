/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.content.pm.PackageManager.GET_PERMISSIONS;

import static com.android.packageinstaller.permission.utils.Utils.getLauncherPackages;
import static com.android.packageinstaller.permission.utils.Utils.isSystem;
import static com.android.packageinstaller.permission.utils.Utils.shouldShowPermission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.permission.PermissionControllerService;
import android.permission.RuntimePermissionPresentationInfo;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calls from the system into the permission controller
 */
public final class PermissionControllerServiceImpl extends PermissionControllerService {
    private static final String LOG_TAG = PermissionControllerServiceImpl.class.getSimpleName();

    @Override
    public @NonNull List<RuntimePermissionPresentationInfo> onGetAppPermissions(
            @NonNull String packageName) {
        return onGetAppPermissions(this, packageName);
    }

    /**
     * Implementation of {@link PermissionControllerService#onGetAppPermissions(String)}}.
     * Called by this class and the legacy implementation.
     */
    static @NonNull List<RuntimePermissionPresentationInfo> onGetAppPermissions(
            @NonNull Context context, @NonNull String packageName) {
        final PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packageName, GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Error getting package:" + packageName, e);
            return Collections.emptyList();
        }

        List<RuntimePermissionPresentationInfo> permissions = new ArrayList<>();

        AppPermissions appPermissions = new AppPermissions(context, packageInfo, false, null);
        for (AppPermissionGroup group : appPermissions.getPermissionGroups()) {
            if (shouldShowPermission(context, group)) {
                final boolean granted = group.areRuntimePermissionsGranted();
                final boolean standard = Utils.OS_PKG.equals(group.getDeclaringPackage());
                RuntimePermissionPresentationInfo permission =
                        new RuntimePermissionPresentationInfo(group.getLabel(),
                                granted, standard);
                permissions.add(permission);
            }
        }

        return permissions;
    }

    @Override
    public void onRevokeRuntimePermission(@NonNull String packageName,
            @NonNull String permissionName) {
        onRevokeRuntimePermission(this, packageName, permissionName);
    }

    /**
     * Implementation of
     * {@link PermissionControllerService#onRevokeRuntimePermission(String, String)}}. Called
     * by this class and the legacy implementation.
     */
    static void onRevokeRuntimePermission(@NonNull Context context,
            @NonNull String packageName, @NonNull String permissionName) {
        try {
            final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName,
                    GET_PERMISSIONS);
            final AppPermissions appPermissions = new AppPermissions(context, packageInfo, false,
                    null);

            final AppPermissionGroup appPermissionGroup = appPermissions.getGroupForPermission(
                    permissionName);

            if (appPermissionGroup != null) {
                appPermissionGroup.revokeRuntimePermissions(false);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Error getting package:" + packageName, e);
        }
    }

    @Override
    public int onCountPermissionApps(@NonNull List<String> permissionNames,
            boolean countOnlyGranted, boolean countSystem) {
        final List<PackageInfo> pkgs = getPackageManager().getInstalledPackages(GET_PERMISSIONS);
        final ArraySet<String> launcherPkgs = getLauncherPackages(this);

        int numApps = 0;

        final int numPkgs = pkgs.size();
        for (int pkgNum = 0; pkgNum < numPkgs; pkgNum++) {
            final PackageInfo pkg = pkgs.get(pkgNum);

            if (!countSystem && isSystem(pkg.applicationInfo, launcherPkgs)) {
                continue;
            }

            final int numPerms = permissionNames.size();
            for (int permNum = 0; permNum < numPerms; permNum++) {
                final String perm = permissionNames.get(permNum);

                final AppPermissionGroup group = AppPermissionGroup.create(this, pkg,
                        permissionNames.get(permNum), true);
                if (group == null || !shouldShowPermission(this, group)) {
                    continue;
                }

                AppPermissionGroup subGroup = null;
                if (group.hasPermission(perm)) {
                    subGroup = group;
                } else {
                    AppPermissionGroup bgGroup = group.getBackgroundPermissions();
                    if (bgGroup != null && bgGroup.hasPermission(perm)) {
                        subGroup = group;
                    }
                }

                if (subGroup != null) {
                    if (!countOnlyGranted || subGroup.areRuntimePermissionsGranted()) {
                        // The permission might not be granted, but some permissions of the group
                        // are granted. In this case the permission is granted silently when the app
                        // asks for it.
                        // Hence this is as-good-as-granted and we count it.
                        numApps++;
                        break;
                    }
                }
            }
        }

        return numApps;
    }
}
