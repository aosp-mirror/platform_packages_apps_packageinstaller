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

import android.app.role.RoleManager;
import android.app.role.RoleManagerCallback;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.rolecontrollerservice.RoleControllerService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.packageinstaller.role.model.AppOp;
import com.android.packageinstaller.role.model.PreferredActivity;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;

import java.util.List;

/**
 * Implementation of {@link RoleControllerService}.
 */
public class RoleControllerServiceImpl extends RoleControllerService {

    private static final String LOG_TAG = RoleControllerServiceImpl.class.getSimpleName();

    private RoleManager mRoleManager;

    private HandlerThread mWorkerThread;
    private Handler mWorkerHandler;

    @Override
    public void onCreate() {
        super.onCreate();

        mRoleManager = getSystemService(RoleManager.class);

        mWorkerThread = new HandlerThread(RoleControllerServiceImpl.class.getSimpleName());
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mWorkerThread.quitSafely();
    }

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
        mWorkerHandler.post(() -> addRoleHolder(roleName, packageName, callback));
    }

    @Override
    public void onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
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
        mWorkerHandler.post(() -> removeRoleHolder(roleName, packageName, callback));
    }

    @Override
    public void onClearRoleHolders(@NonNull String roleName,
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
        mWorkerHandler.post(() -> clearRoleHolders(roleName, callback));
    }

    @WorkerThread
    private void addRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @NonNull RoleManagerCallback callback) {
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

        if (role.isExclusive()) {
            List<String> previousPackageNames = mRoleManager.getRoleHolders(roleName);
            int previousPackageNamesSize = previousPackageNames.size();
            for (int i = 0; i < previousPackageNamesSize; i++) {
                String previousPackageName = previousPackageNames.get(i);
                boolean removed = removeRoleHolderInternal(role, previousPackageName);
                if (!removed) {
                    Log.e(LOG_TAG, "Failed to remove previous holder from role holders in"
                            + " RoleManager, package: " + packageName + ", role: " + roleName);
                    // TODO: Clean up?
                    callback.onFailure();
                    return;
                }
            }
        }

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

        List<AppOp> appOps = role.getAppOps();
        int appOpsSize = appOps.size();
        for (int i = 0; i < appOpsSize; i++) {
            AppOp appOp = appOps.get(i);
            // TODO: STOPSHIP: When to pass true?
            appOp.grant(packageName, false, this);
            // TODO: STOPSHIP: Kill apps?
        }

        List<PreferredActivity> preferredActivities = role.getPreferredActivities();
        int preferredActivitiesSize = preferredActivities.size();
        for (int i = 0; i < preferredActivitiesSize; i++) {
            PreferredActivity preferredActivity = preferredActivities.get(i);
            preferredActivity.configure(packageName, this);
        }

        boolean added = mRoleManager.addRoleHolderFromController(roleName, packageName);
        if (!added) {
            Log.e(LOG_TAG, "Failed to add package to role holders in RoleManager, package: "
                    + packageName + ", role: " + roleName);
            callback.onFailure();
            return;
        }

        callback.onSuccess();
    }

    @WorkerThread
    private void removeRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @NonNull RoleManagerCallback callback) {
        Role role = Roles.getRoles(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            callback.onFailure();
            return;
        }

        boolean removed = removeRoleHolderInternal(role, packageName);
        if (!removed) {
            Log.e(LOG_TAG, "Failed to remove package from role holders in RoleManager, package: "
                    + packageName + ", role: " + roleName);
            callback.onFailure();
            return;
        }

        callback.onSuccess();
    }

    @WorkerThread
    private void clearRoleHolders(@NonNull String roleName, @NonNull RoleManagerCallback callback) {
        Role role = Roles.getRoles(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            callback.onFailure();
            return;
        }

        List<String> packageNames = mRoleManager.getRoleHolders(roleName);
        int packageNamesSize = packageNames.size();
        for (int i = 0; i < packageNamesSize; i++) {
            String packageName = packageNames.get(i);
            boolean removed = removeRoleHolderInternal(role, packageName);
            if (!removed) {
                Log.e(LOG_TAG, "Failed to remove package when clearing role holders in RoleManager,"
                        + " package: " + packageName + ", role: " + roleName);
                callback.onFailure();
                return;
            }
        }

        callback.onSuccess();
    }

    @WorkerThread
    private boolean removeRoleHolderInternal(@NonNull Role role, @NonNull String packageName) {
        // TODO: STOPSHIP: Revoke privileges from previous holders.
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName,
                    // TODO: Add PackageManager.MATCH_UNINSTALLED_PACKAGES ?
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(LOG_TAG, "Unknown package: " + packageName, e);
        }

        if (applicationInfo != null) {
            if (applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M) {
                List<String> permissions = role.getPermissions();
                int permissionsSize = permissions.size();
                for (int i = 0; i < permissionsSize; i++) {
                    String permission = permissions.get(i);
                    // TODO: DefaultPermissionGrantPolicy is also checking some other flags.
                    packageManager.revokeRuntimePermission(packageName, permission, UserHandle.of(
                            UserHandle.myUserId()));
                }
            }

            List<AppOp> appOps = role.getAppOps();
            int appOpsSize = appOps.size();
            for (int i = 0; i < appOpsSize; i++) {
                AppOp appOp = appOps.get(i);
                appOp.revoke(packageName, this);
                // TODO: STOPSHIP: Do we kill apps?
            }

            List<PreferredActivity> preferredActivities = role.getPreferredActivities();
            int preferredActivitiesSize = preferredActivities.size();
            for (int i = 0; i < preferredActivitiesSize; i++) {
                PreferredActivity preferredActivity = preferredActivities.get(i);
                preferredActivity.configure(packageName, this);
            }
        }

        return mRoleManager.removeRoleHolderFromController(role.getName(), packageName);
    }
}
