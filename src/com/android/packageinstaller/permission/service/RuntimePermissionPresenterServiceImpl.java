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

import static com.android.packageinstaller.permission.utils.Utils.shouldShowPermission;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.permission.RuntimePermissionPresentationInfo;
import android.permission.RuntimePermissionPresenterService;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service that provides presentation information for runtime permissions.
 */
public final class RuntimePermissionPresenterServiceImpl extends RuntimePermissionPresenterService {
    private static final String LOG_TAG = "PermissionPresenter";

    @Override
    public @NonNull List<RuntimePermissionPresentationInfo> onGetAppPermissions(
            @NonNull String packageName) {
        return onGetAppPermissions(this, packageName);
    }

    /**
     * Implementation of {@link RuntimePermissionPresenterService#onGetAppPermissions(String)}}.
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
     * {@link RuntimePermissionPresenterService#onRevokeRuntimePermission(String, String)}}. Called
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
}
