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

import android.app.role.RoleControllerService;
import android.app.role.RoleManager;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.packageinstaller.permission.utils.CollectionUtils;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link RoleControllerService}.
 */
public class RoleControllerServiceImpl extends RoleControllerService {

    private static final String LOG_TAG = RoleControllerServiceImpl.class.getSimpleName();

    private static final boolean DEBUG = false;

    private RoleManager mRoleManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mRoleManager = getSystemService(RoleManager.class);
    }

    @Override
    @WorkerThread
    public boolean onGrantDefaultRoles() {
        if (DEBUG) {
            Log.i(LOG_TAG, "Granting default roles, user: " + UserHandle.myUserId());
        }

        // Gather the available roles for current user.
        ArrayMap<String, Role> roleMap = Roles.get(this);
        List<Role> roles = new ArrayList<>();
        List<String> roleNames = new ArrayList<>();
        ArraySet<String> addedRoleNames = new ArraySet<>();
        int roleMapSize = roleMap.size();
        for (int i = 0; i < roleMapSize; i++) {
            Role role = roleMap.valueAt(i);

            if (!role.isAvailable(this)) {
                continue;
            }
            roles.add(role);
            String roleName = role.getName();
            roleNames.add(roleName);
            if (!mRoleManager.isRoleAvailable(roleName)) {
                addedRoleNames.add(roleName);
            }
        }

        // TODO: Clean up holders of roles that will be removed.

        // Set the available role names in RoleManager.
        mRoleManager.setRoleNamesFromController(roleNames);

        int addedRoleNamesSize = addedRoleNames.size();
        for (int i = 0; i < addedRoleNamesSize; i++) {
            String roleName = addedRoleNames.valueAt(i);

            Role role = roleMap.get(roleName);
            role.onRoleAdded(this);
        }

        // Go through the holders of all roles.
        int rolesSize = roles.size();
        for (int rolesIndex = 0; rolesIndex < rolesSize; rolesIndex++) {
            Role role = roles.get(rolesIndex);

            String roleName = role.getName();

            // For each of the current holders, check if it is still qualified, redo grant if so, or
            // remove it otherwise.
            List<String> currentPackageNames = mRoleManager.getRoleHolders(roleName);
            int currentPackageNamesSize = currentPackageNames.size();
            for (int currentPackageNamesIndex = 0;
                    currentPackageNamesIndex < currentPackageNamesSize;
                    currentPackageNamesIndex++) {
                String packageName = currentPackageNames.get(currentPackageNamesIndex);

                if (role.isPackageQualified(packageName, this)) {
                    // We should not override user set or fixed permissions because we are only
                    // redoing the grant here. Otherwise, user won't be able to revoke permissions
                    // granted by role.
                    addRoleHolderInternal(role, packageName, false, false, true);
                } else {
                    Log.i(LOG_TAG, "Removing package that no longer qualifies for the role,"
                            + " package: " + packageName + ", role: " + roleName);
                    removeRoleHolderInternal(role, packageName, false);
                }
            }

            // If there is no holder for a role now, we need to add default or fallback holders, if
            // any.
            currentPackageNames = mRoleManager.getRoleHolders(roleName);
            currentPackageNamesSize = currentPackageNames.size();
            if (currentPackageNamesSize == 0) {
                List<String> packageNamesToAdd = null;
                if (addedRoleNames.contains(roleName)) {
                    packageNamesToAdd = role.getDefaultHolders(this);
                }
                if (packageNamesToAdd == null || packageNamesToAdd.isEmpty()) {
                    packageNamesToAdd = CollectionUtils.singletonOrEmpty(role.getFallbackHolder(
                            this));
                }

                int packageNamesToAddSize = packageNamesToAdd.size();
                for (int packageNamesToAddIndex = 0; packageNamesToAddIndex < packageNamesToAddSize;
                        packageNamesToAddIndex++) {
                    String packageName = packageNamesToAdd.get(packageNamesToAddIndex);

                    if (!role.isPackageQualified(packageName, this)) {
                        Log.e(LOG_TAG, "Default/fallback role holder package doesn't qualify for"
                                + " the role, package: " + packageName + ", role: " + roleName);
                        continue;
                    }
                    Log.i(LOG_TAG, "Adding package as default/fallback role holder, package: "
                            + packageName + ", role: " + roleName);
                    // TODO: If we don't override user here, user might end up missing incoming
                    // phone calls or SMS, so we just keep the old behavior. But overriding user
                    // choice about permission without explicit user action is bad, so maybe we
                    // should at least show a notification?
                    addRoleHolderInternal(role, packageName, true);
                }
            }

            // Ensure that an exclusive role has at most one holder.
            currentPackageNames = mRoleManager.getRoleHolders(roleName);
            currentPackageNamesSize = currentPackageNames.size();
            if (role.isExclusive() && currentPackageNamesSize > 1) {
                Log.w(LOG_TAG, "Multiple packages holding an exclusive role, role: "
                        + roleName);
                // No good way to determine who should be the only one, just keep the first one.
                for (int currentPackageNamesIndex = 1;
                        currentPackageNamesIndex < currentPackageNamesSize;
                        currentPackageNamesIndex++) {
                    String packageName = currentPackageNames.get(currentPackageNamesIndex);

                    Log.i(LOG_TAG, "Removing extraneous package for an exclusive role, package: "
                            + packageName + ", role: " + roleName);
                    removeRoleHolderInternal(role, packageName, false);
                }
            }
        }

        AsyncTask.execute(
                () -> Utils.updateUserSensitive(getApplication(), Process.myUserHandle()));

        return true;
    }

    @Override
    @WorkerThread
    public boolean onAddRoleHolder(@NonNull String roleName, @NonNull String packageName,
            int flags) {
        if (!checkFlags(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP)) {
            return false;
        }

        Role role = Roles.get(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            return false;
        }
        if (!role.isAvailable(this)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            return false;
        }

        if (!role.isPackageQualified(packageName, this)) {
            Log.e(LOG_TAG, "Package does not qualify for the role, package: " + packageName
                    + ", role: " + roleName);
            return false;
        }

        boolean added = false;
        if (role.isExclusive()) {
            List<String> currentPackageNames = mRoleManager.getRoleHolders(roleName);
            int currentPackageNamesSize = currentPackageNames.size();
            for (int i = 0; i < currentPackageNamesSize; i++) {
                String currentPackageName = currentPackageNames.get(i);

                if (Objects.equals(currentPackageName, packageName)) {
                    Log.i(LOG_TAG, "Package is already a role holder, package: " + packageName
                            + ", role: " + roleName);
                    added = true;
                    continue;
                }

                boolean removed = removeRoleHolderInternal(role, currentPackageName, false);
                if (!removed) {
                    // TODO: Clean up?
                    return false;
                }
            }
        }

        boolean dontKillApp = hasFlag(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP);
        added = addRoleHolderInternal(role, packageName, dontKillApp, true, added);
        if (!added) {
            return false;
        }

        role.onHolderAddedAsUser(packageName, Process.myUserHandle(), this);
        role.onHolderChangedAsUser(Process.myUserHandle(), this);

        return true;
    }

    @Override
    @WorkerThread
    public boolean onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
            int flags) {
        if (!checkFlags(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP)) {
            return false;
        }

        Role role = Roles.get(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            return false;
        }
        if (!role.isAvailable(this)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            return false;
        }

        boolean dontKillApp = hasFlag(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP);
        boolean removed = removeRoleHolderInternal(role, packageName, dontKillApp);
        if (!removed) {
            return false;
        }

        // TODO: Should we consider this successful regardless?
        boolean fallbackSuccessful = addFallbackRoleHolderMaybe(role);
        if (!fallbackSuccessful) {
            return false;
        }

        role.onHolderChangedAsUser(Process.myUserHandle(), this);

        return true;
    }

    @Override
    @WorkerThread
    public boolean onClearRoleHolders(@NonNull String roleName, int flags) {
        if (!checkFlags(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP)) {
            return false;
        }

        Role role = Roles.get(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            return false;
        }
        if (!role.isAvailable(this)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            return false;
        }

        boolean dontKillApp = hasFlag(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP);
        boolean cleared = clearRoleHoldersInternal(role, dontKillApp);
        if (!cleared) {
            return false;
        }

        // TODO: Should we consider this successful regardless?
        boolean fallbackSuccessful = addFallbackRoleHolderMaybe(role);
        if (!fallbackSuccessful) {
            return false;
        }

        role.onHolderChangedAsUser(Process.myUserHandle(), this);

        return true;
    }

    @WorkerThread
    private boolean addRoleHolderInternal(@NonNull Role role, @NonNull String packageName,
            boolean overrideUserSetAndFixedPermissions) {
        return addRoleHolderInternal(role, packageName, false, overrideUserSetAndFixedPermissions,
                false);
    }

    @WorkerThread
    private boolean addRoleHolderInternal(@NonNull Role role, @NonNull String packageName,
            boolean dontKillApp, boolean overrideUserSetAndFixedPermissions, boolean added) {
        role.grant(packageName, dontKillApp, overrideUserSetAndFixedPermissions, this);

        String roleName = role.getName();
        if (!added) {
            added = mRoleManager.addRoleHolderFromController(roleName, packageName);
        }
        if (!added) {
            Log.e(LOG_TAG, "Failed to add role holder in RoleManager, package: " + packageName
                    + ", role: " + roleName);
        }
        return added;
    }

    @WorkerThread
    private boolean removeRoleHolderInternal(@NonNull Role role, @NonNull String packageName,
            boolean dontKillApp) {
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName, this);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Cannot get ApplicationInfo for package: " + packageName);
        }

        if (applicationInfo != null) {
            role.revoke(packageName, dontKillApp, false, this);
        }

        String roleName = role.getName();
        boolean removed = mRoleManager.removeRoleHolderFromController(roleName, packageName);
        if (!removed) {
            Log.e(LOG_TAG, "Failed to remove role holder in RoleManager," + " package: "
                    + packageName + ", role: " + roleName);
        }
        return removed;
    }

    @WorkerThread
    private boolean clearRoleHoldersInternal(@NonNull Role role, boolean dontKillApp) {
        String roleName = role.getName();
        List<String> packageNames = mRoleManager.getRoleHolders(roleName);
        boolean cleared = true;

        int packageNamesSize = packageNames.size();
        for (int i = 0; i < packageNamesSize; i++) {
            String packageName = packageNames.get(i);
            boolean removed = removeRoleHolderInternal(role, packageName, dontKillApp);
            if (!removed) {
                cleared = false;
            }
        }

        if (!cleared) {
            Log.e(LOG_TAG, "Failed to clear role holders, role: " + roleName);
        }
        return cleared;
    }

    @WorkerThread
    private boolean addFallbackRoleHolderMaybe(@NonNull Role role) {
        String roleName = role.getName();
        List<String> currentPackageNames = mRoleManager.getRoleHolders(roleName);
        if (!currentPackageNames.isEmpty()) {
            return true;
        }

        String fallbackPackageName = role.getFallbackHolder(this);
        if (fallbackPackageName == null) {
            return true;
        }

        if (!role.isPackageQualified(fallbackPackageName, this)) {
            Log.e(LOG_TAG, "Fallback role holder package doesn't qualify for the role, package: "
                    + fallbackPackageName + ", role: " + roleName);
            return false;
        }

        Log.i(LOG_TAG, "Adding package as fallback role holder, package: " + fallbackPackageName
                + ", role: " + roleName);
        // TODO: If we don't override user here, user might end up missing incoming
        // phone calls or SMS, so we just keep the old behavior. But overriding user
        // choice about permission without explicit user action is bad, so maybe we
        // should at least show a notification?
        return addRoleHolderInternal(role, fallbackPackageName, true);
    }

    @Override
    public boolean onIsApplicationQualifiedForRole(@NonNull String roleName,
            @NonNull String packageName) {
        Role role = Roles.get(this).get(roleName);
        if (role == null) {
            return false;
        }
        if (!role.isAvailable(this)) {
            return false;
        }
        return role.isPackageQualified(packageName, this);
    }

    @Override
    public boolean onIsRoleVisible(@NonNull String roleName) {
        Role role = Roles.get(this).get(roleName);
        if (role == null) {
            return false;
        }
        if (!role.isAvailable(this)) {
            return false;
        }
        return role.isVisibleAsUser(Process.myUserHandle(), this);
    }

    private static boolean checkFlags(int flags, int allowedFlags) {
        if ((flags & allowedFlags) != flags) {
            Log.e(LOG_TAG, "flags is invalid, flags: 0x" + Integer.toHexString(flags)
                    + ", allowed flags: 0x" + Integer.toHexString(allowedFlags));
            return false;
        }
        return true;
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) == flag;
    }
}
