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

package com.android.packageinstaller.role.service;

import android.app.role.RoleManagerCallback;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;
import android.rolecontrollerservice.RoleControllerService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.packageinstaller.role.model.AppOp;
import com.android.packageinstaller.role.model.PreferredActivity;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;

import java.util.List;

/**
 * Implementation of {@link RoleControllerService}.
 */
// STOPSHIP: TODO: Make single thread or add locking.
public class RoleControllerServiceImpl extends RoleControllerService {

    private static final String LOG_TAG = RoleControllerServiceImpl.class.getSimpleName();

    @Override
    public void onAddRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @NonNull RoleManagerCallback callback) {
        if (callback == null) {
            Log.e(LOG_TAG, "callback cannot be null");
            return;
        }
        if (TextUtils.isEmpty(roleName)) {
            Log.e(LOG_TAG, "roleName cannot be null or empty: " + roleName);
            callback.onFailure();
            return;
        }
        if (TextUtils.isEmpty(packageName)) {
            Log.e(LOG_TAG, "packageName cannot be null or empty: " + roleName);
            callback.onFailure();
            return;
        }

        Role role = Roles.getRoles(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            callback.onFailure();
            return;
        }

        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Unknown package: " + packageName, e);
            callback.onFailure();
            return;
        }
        // TODO: STOPSHIP: Check for disabled packages?
        // TODO: STOPSHIP: Add and check PackageManager.getSharedLibraryInfo().
        if (applicationInfo.isInstantApp()) {
            Log.e(LOG_TAG, "Cannot set Instant App as role holder, package: " + packageName);
            callback.onFailure();
            return;
        }
        if (!role.isPackageQualified(packageName, this)) {
            Log.e(LOG_TAG, "Package does not qualify for the role, package: " + packageName
                    + ", role: " + roleName);
            callback.onFailure();
            return;
        }

        // TODO: Revoke privileges from previous holder if exclusive.

        if (applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M) {
            List<String> permissions = role.getPermissions();
            int permissionsSize = permissions.size();
            for (int i = 0; i < permissionsSize; i++) {
                String permission = permissions.get(i);
                // TODO: STOPSHIP: DefaultPermissionGrantPolicy is also checking some other flags.
                packageManager.grantRuntimePermission(packageName, permission, UserHandle.of(
                        UserHandle.myUserId()));
            }
        }

        // TODO: Flip other app ops together with permissions.
        List<AppOp> appOps = role.getAppOps();
        int appOpsSize = appOps.size();
        for (int i = 0; i < appOpsSize; i++) {
            AppOp appOp = appOps.get(i);
            appOp.grant(applicationInfo, this);
            // TODO: STOPSHIP: Kill apps?
        }

        List<PreferredActivity> preferredActivities = role.getPreferredActivities();
        int preferredActivitiesSize = preferredActivities.size();
        for (int i = 0; i < preferredActivitiesSize; i++) {
            PreferredActivity preferredActivity = preferredActivities.get(i);
            preferredActivity.configure(packageName, this);
        }

        // TODO: Call RoleManager.addRoleHolderByController() or something.

        callback.onSuccess();
    }

    @Override
    public void onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @NonNull RoleManagerCallback callback) {
        // TODO

        // TODO: Call RoleManager.removeRoleHolderByController() or something.

        callback.onSuccess();
    }

    @Override
    public void onClearRoleHolders(@NonNull String roleName,
            @NonNull RoleManagerCallback callback) {
        // TODO

        // TODO: Call RoleManager.clearRoleHolderByController() or something.

        callback.onSuccess();
    }
}
