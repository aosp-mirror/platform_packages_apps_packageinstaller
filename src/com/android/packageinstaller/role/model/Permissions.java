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

package com.android.packageinstaller.role.model;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.utils.ArrayUtils;
import com.android.packageinstaller.permission.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Permissions to be granted or revoke by a {@link Role}.
 */
public class Permissions {

    private static final String LOG_TAG = Permissions.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static ArrayMap<String, String> sForegroundToBackgroundPermission;
    private static ArrayMap<String, List<String>> sBackgroundToForegroundPermissions;
    private static final Object sForegroundBackgroundPermissionMappingsLock = new Object();

    /**
     * Grant permissions and associated app ops to an application.
     *
     * @param packageName the package name of the application to be granted permissions to
     * @param permissions the list of permissions to be granted
     * @param overrideDisabledSystemPackageAndUser whether to ignore the permissions of a disabled
     *                                             system package (if this package is an updated
     *                                             system package), and whether to override user
     *                                             set and fixed flags on the permission.
     * @param setSystemFixed whether the permissions will be granted as system-fixed
     * @param context the {@code Context} to retrieve system services.
     *
     * @return whether any app mode has changed
     *
     * @see com.android.server.pm.permission.DefaultPermissionGrantPolicy#grantRuntimePermissions(
     *      PackageInfo, java.util.Set, boolean, boolean, int)
     */
    public static boolean grant(@NonNull String packageName, @NonNull List<String> permissions,
            boolean overrideDisabledSystemPackageAndUser, boolean setSystemFixed,
            @NonNull Context context) {
        PackageInfo packageInfo = getPackageInfo(packageName, context);
        if (packageInfo == null) {
            return false;
        }

        if (ArrayUtils.isEmpty(packageInfo.requestedPermissions)) {
            return false;
        }

        // Automatically attempt to grant split permissions to older APKs
        PermissionManager permissionManager = context.getSystemService(PermissionManager.class);
        List<PermissionManager.SplitPermissionInfo> splitPermissions =
                permissionManager.getSplitPermissions();
        ArraySet<String> permissionsWithoutSplits = new ArraySet<>(permissions);
        ArraySet<String> permissionsToGrant = new ArraySet<>(permissionsWithoutSplits);
        int splitPermissionsSize = splitPermissions.size();
        for (int i = 0; i < splitPermissionsSize; i++) {
            PermissionManager.SplitPermissionInfo splitPermission = splitPermissions.get(i);

            if (packageInfo.applicationInfo.targetSdkVersion < splitPermission.getTargetSdk()
                    && permissionsWithoutSplits.contains(splitPermission.getSplitPermission())) {
                permissionsToGrant.addAll(splitPermission.getNewPermissions());
            }
        }

        CollectionUtils.retainAll(permissionsToGrant, packageInfo.requestedPermissions);
        if (permissionsToGrant.isEmpty()) {
            return false;
        }

        // In some cases, like for the Phone or SMS app, we grant permissions regardless
        // of if the version on the system image declares the permission as used since
        // selecting the app as the default for that function the user makes a deliberate
        // choice to grant this app the permissions needed to function. For all other
        // apps, (default grants on first boot and user creation) we don't grant default
        // permissions if the version on the system image does not declare them.
        if (!overrideDisabledSystemPackageAndUser && isUpdatedSystemApp(packageInfo)) {
            PackageInfo disabledSystemPackageInfo = getFactoryPackageInfo(packageName, context);
            if (disabledSystemPackageInfo != null) {
                if (ArrayUtils.isEmpty(disabledSystemPackageInfo.requestedPermissions)) {
                    return false;
                }
                CollectionUtils.retainAll(permissionsToGrant,
                        disabledSystemPackageInfo.requestedPermissions);
                if (permissionsToGrant.isEmpty()) {
                    return false;
                }
            }
        }

        // Sort foreground permissions first so that we can grant a background permission based on
        // whether any of its foreground permissions are granted.
        int permissionsToGrantSize = permissionsToGrant.size();
        String[] sortedPermissionsToGrant = new String[permissionsToGrantSize];
        int foregroundPermissionCount = 0;
        int nonForegroundPermissionCount = 0;
        for (int i = 0; i < permissionsToGrantSize; i++) {
            String permission = permissionsToGrant.valueAt(i);

            if (isForegroundPermission(permission, context)) {
                sortedPermissionsToGrant[foregroundPermissionCount] = permission;
                foregroundPermissionCount++;
            } else {
                int index = permissionsToGrantSize - 1 - nonForegroundPermissionCount;
                sortedPermissionsToGrant[index] = permission;
                nonForegroundPermissionCount++;
            }
        }

        boolean appOpModeChanged = false;
        int sortedPermissionsToGrantLength = sortedPermissionsToGrant.length;
        for (int i = 0; i < sortedPermissionsToGrantLength; i++) {
            String permission = sortedPermissionsToGrant[i];

            appOpModeChanged |= grantSingle(packageName, permission,
                    overrideDisabledSystemPackageAndUser, setSystemFixed, context);
        }

        return appOpModeChanged;
    }

    private static boolean grantSingle(@NonNull String packageName, @NonNull String permission,
            boolean overrideUserSetAndFixed, boolean setSystemFixed, @NonNull Context context) {
        boolean wasPermissionOrAppOpGranted = isPermissionOrAppOpGranted(packageName, permission,
                context);
        if (isPermissionFixed(packageName, permission, false, overrideUserSetAndFixed, context)
                && !wasPermissionOrAppOpGranted) {
            // Stop granting if this permission is fixed to revoked.
            return false;
        }

        if (isBackgroundPermission(permission, context)) {
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            boolean isAnyForegroundPermissionGranted = false;
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                if (isPermissionOrAppOpGranted(packageName, foregroundPermission, context)) {
                    isAnyForegroundPermissionGranted = true;
                    break;
                }
            }

            if (!isAnyForegroundPermissionGranted) {
                // Stop granting if this background permission doesn't have a granted foreground
                // permission.
                return false;
            }
        }

        boolean appOpModeChanged = grantPermissionAndAppOp(packageName, permission, context);

        // Update permission flags.
        int newFlags = PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
        if (setSystemFixed) {
            newFlags |= PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        }
        int newMask = newFlags;
        if (!wasPermissionOrAppOpGranted) {
            // If we've granted a permission which wasn't granted, it's no longer user set or fixed.
            newMask |= PackageManager.FLAG_PERMISSION_USER_FIXED
                    | PackageManager.FLAG_PERMISSION_USER_SET;
        }
        // TODO: Why this?
        // If a component gets a permission for being the default handler A
        // and also default handler B, we grant the weaker grant form.
        if (!setSystemFixed) {
            int oldFlags = getPermissionFlags(permission, packageName, context);
            if ((oldFlags & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0
                    && (oldFlags & PackageManager.FLAG_PERMISSION_SYSTEM_FIXED) != 0) {
                if (DEBUG) {
                    Log.i(LOG_TAG, "Granted not fixed " + permission + " to default handler "
                            + packageName);
                }
                newMask |= PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
            }
        }
        setPermissionFlags(permission, packageName, newFlags, newMask, context);

        return appOpModeChanged;
    }

    private static boolean isPermissionOrAppOpGranted(@NonNull String packageName,
            @NonNull String permission, @NonNull Context context) {
        if (!isPermissionGrantedWithoutCheckingAppOp(packageName, permission, context)) {
            return false;
        }

        if (isRuntimePermissionsSupported(packageName, context)) {
            return true;
        }

        // Check app op mode for pre-M apps.
        String appOp = getPermissionAppOp(permission);
        if (appOp == null) {
            return false;
        }
        Integer appOpMode = getAppOpMode(packageName, appOp, context);
        if (appOpMode == null) {
            return false;
        }

        if (!isBackgroundPermission(permission, context)) {
            if (!isForegroundPermission(permission, context)) {
                // This permission is an ordinary permission, return true if its app op mode is
                // MODE_ALLOWED.
                return appOpMode == AppOpsManager.MODE_ALLOWED;
            } else {
                // This permission is a foreground permission, return true if its app op mode is
                // MODE_FOREGROUND or MODE_ALLOWED.
                return appOpMode == AppOpsManager.MODE_FOREGROUND
                        || appOpMode == AppOpsManager.MODE_ALLOWED;
            }
        } else {
            // This permission is a background permission, return true if any of its foreground
            // permissions' app op modes are MODE_ALLOWED.
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                String foregroundAppOp = getPermissionAppOp(foregroundPermission);
                if (foregroundAppOp == null) {
                    continue;
                }
                Integer foregroundAppOpMode = getAppOpMode(packageName, foregroundAppOp, context);
                if (foregroundAppOpMode == null) {
                    continue;
                }
                if (foregroundAppOpMode == AppOpsManager.MODE_ALLOWED) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean grantPermissionAndAppOp(@NonNull String packageName,
            @NonNull String permission, @NonNull Context context) {
        // Grant the permission.
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = UserHandle.of(UserHandle.myUserId());
        packageManager.grantRuntimePermission(packageName, permission, user);

        // Grant the app op.
        if (!isBackgroundPermission(permission, context)) {
            String appOp = getPermissionAppOp(permission);
            if (appOp == null) {
                return false;
            }

            int appOpMode;
            if (!isForegroundPermission(permission, context)) {
                // This permission is an ordinary permission, set its app op mode to MODE_ALLOWED.
                appOpMode = AppOpsManager.MODE_ALLOWED;
            } else {
                // This permission is a foreground permission, set its app op mode according to
                // whether its background permission is granted.
                String backgroundPermission = getBackgroundPermission(permission, context);
                if (!isPermissionOrAppOpGranted(packageName, backgroundPermission, context)) {
                    appOpMode = AppOpsManager.MODE_FOREGROUND;
                } else {
                    appOpMode = AppOpsManager.MODE_ALLOWED;
                }
            }
            return setAppOpMode(packageName, appOp, appOpMode, context);
        } else {
            // This permission is a background permission, set all its foreground permissions' app
            // op modes to MODE_ALLOWED.
            boolean appOpModeChanged = false;
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                String foregroundAppOp = getPermissionAppOp(foregroundPermission);
                if (foregroundAppOp == null) {
                    continue;
                }
                appOpModeChanged |= setAppOpMode(packageName, foregroundAppOp,
                        AppOpsManager.MODE_ALLOWED, context);
            }
            return appOpModeChanged;
        }
    }

    /**
     * Revoke permissions and associated app ops from an application.
     *
     * @param packageName the package name of the application to be revoke permissions from
     * @param permissions the list of permissions to be revoked
     * @param overrideSystemFixed whether system-fixed permissions will be revoked
     * @param context the {@code Context} to retrieve system services.
     *
     * @see com.android.server.pm.permission.DefaultPermissionGrantPolicy#revokeRuntimePermissions(
     *      String, java.util.Set, boolean, int)
     */
    public boolean revoke(@NonNull String packageName, @NonNull List<String> permissions,
            boolean overrideSystemFixed, @NonNull Context context) {
        PackageInfo packageInfo = getPackageInfo(packageName, context);
        if (packageInfo == null) {
            return false;
        }

        if (ArrayUtils.isEmpty(packageInfo.requestedPermissions)) {
            return false;
        }

        ArraySet<String> permissionsToRevoke = new ArraySet<>(permissions);
        CollectionUtils.retainAll(permissionsToRevoke, packageInfo.requestedPermissions);
        if (permissionsToRevoke.isEmpty()) {
            return false;
        }

        // Sort background permissions first so that we can revoke a foreground permission based on
        // whether its background permission is revoked.
        int permissionsToRevokeSize = permissionsToRevoke.size();
        String[] sortedPermissionsToRevoke = new String[permissionsToRevokeSize];
        int backgroundPermissionCount = 0;
        int nonBackgroundPermissionCount = 0;
        for (int i = 0; i < permissionsToRevokeSize; i++) {
            String permission = permissionsToRevoke.valueAt(i);

            if (isBackgroundPermission(permission, context)) {
                sortedPermissionsToRevoke[backgroundPermissionCount] = permission;
                backgroundPermissionCount++;
            } else {
                int index = permissionsToRevokeSize - 1 - nonBackgroundPermissionCount;
                sortedPermissionsToRevoke[index] = permission;
                nonBackgroundPermissionCount++;
            }
        }

        boolean appOpModeChanged = false;
        int sortedPermissionsToRevokeLength = sortedPermissionsToRevoke.length;
        for (int i = 0; i < sortedPermissionsToRevokeLength; i++) {
            String permission = sortedPermissionsToRevoke[i];

            appOpModeChanged |= revokeSingle(packageName, permission, overrideSystemFixed, context);
        }

        return appOpModeChanged;
    }

    private static boolean revokeSingle(@NonNull String packageName, @NonNull String permission,
            boolean overrideSystemFixed, @NonNull Context context) {
        if (!isPermissionGrantedByDefault(packageName, permission, context)) {
            return false;
        }

        // Remove the granted-by-default permission flag.
        setPermissionFlags(permission, packageName, 0,
                PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT, context);
        // TODO: Why?
        // Note that we do not revoke FLAG_PERMISSION_SYSTEM_FIXED. That bit remains sticky once
        // set.

        if (isPermissionFixed(packageName, permission, overrideSystemFixed, false, context)
                && isPermissionOrAppOpGranted(packageName, permission, context)) {
            // Stop revoking if this permission is fixed to granted.
            return false;
        }

        if (isForegroundPermission(permission, context)) {
            String backgroundPermission = getBackgroundPermission(permission, context);
            if (isPermissionOrAppOpGranted(packageName, backgroundPermission, context)) {
                // Stop revoking if this foreground permission has a granted background permission.
                return false;
            }
        }

        return revokePermissionAndAppOp(packageName, permission, context);
    }

    private static boolean revokePermissionAndAppOp(@NonNull String packageName,
            @NonNull String permission, @NonNull Context context) {
        boolean runtimePermissionsSupported = isRuntimePermissionsSupported(packageName, context);
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = UserHandle.of(UserHandle.myUserId());
        if (runtimePermissionsSupported) {
            // Revoke the permission.
            packageManager.revokeRuntimePermission(packageName, permission, user);
        }

        // Revoke the app op.
        if (!isBackgroundPermission(permission, context)) {
            String appOp = getPermissionAppOp(permission);
            if (appOp == null) {
                return false;
            }

            // This permission is an ordinary or foreground permission, reset its app op mode to
            // default.
            int appOpMode = getDefaultAppOpMode(appOp);
            boolean appOpModeChanged = setAppOpMode(packageName, appOp, appOpMode, context);
            if (appOpModeChanged) {
                if (!runtimePermissionsSupported && (appOpMode == AppOpsManager.MODE_FOREGROUND
                        || appOpMode == AppOpsManager.MODE_ALLOWED)) {
                    // We've reset this permission's app op mode to be permissive, so we'll need the
                    // user to review it again.
                    packageManager.updatePermissionFlags(permission, packageName,
                            PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED,
                            PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED, user);
                }
            }
            return appOpModeChanged;
        } else {
            // This permission is a background permission, set all its granted foreground
            // permissions' app op modes to MODE_FOREGROUND.
            boolean appOpModeChanged = false;
            List<String> foregroundPermissions = getForegroundPermissions(permission, context);
            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int i = 0; i < foregroundPermissionsSize; i++) {
                String foregroundPermission = foregroundPermissions.get(i);

                if (!isPermissionOrAppOpGranted(packageName, foregroundPermission, context)) {
                    continue;
                }

                String foregroundAppOp = getPermissionAppOp(foregroundPermission);
                if (foregroundAppOp == null) {
                    continue;
                }
                appOpModeChanged |= setAppOpMode(packageName, foregroundAppOp,
                        AppOpsManager.MODE_FOREGROUND, context);
            }
            return appOpModeChanged;
        }
    }

    @Nullable
    private static PackageInfo getPackageInfo(@NonNull String packageName,
            @NonNull Context context) {
        return getPackageInfo(packageName, 0, context);
    }

    @Nullable
    private static PackageInfo getFactoryPackageInfo(@NonNull String packageName,
            @NonNull Context context) {
        return getPackageInfo(packageName, PackageManager.MATCH_FACTORY_ONLY, context);
    }

    @Nullable
    private static PackageInfo getPackageInfo(@NonNull String packageName, int extraFlags,
            @NonNull Context context) {
        try {
            return context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                            // TODO: Why MATCH_UNINSTALLED_PACKAGES?
                            | PackageManager.MATCH_UNINSTALLED_PACKAGES
                            | PackageManager.GET_PERMISSIONS | extraFlags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static boolean isUpdatedSystemApp(@NonNull PackageInfo packageInfo) {
        return packageInfo.applicationInfo != null && (packageInfo.applicationInfo.flags
                & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    @Nullable
    static ApplicationInfo getApplicationInfo(@NonNull String packageName,
            @NonNull Context context) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static boolean isRuntimePermissionsSupported(@NonNull String packageName,
            @NonNull Context context) {
        ApplicationInfo applicationInfo = getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            return false;
        }
        return applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M;
    }

    private static int getPermissionFlags(@NonNull String packageName, @NonNull String permission,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = UserHandle.of(UserHandle.myUserId());
        return packageManager.getPermissionFlags(permission, packageName, user);
    }

    static boolean isPermissionFixed(@NonNull String packageName, @NonNull String permission,
            boolean overrideSystemFixed, boolean overrideUserSetAndFixed,
            @NonNull Context context) {
        int flags = getPermissionFlags(packageName, permission, context);
        int fixedFlags = PackageManager.FLAG_PERMISSION_POLICY_FIXED;
        if (!overrideSystemFixed) {
            fixedFlags |= PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
        }
        if (!overrideUserSetAndFixed) {
            fixedFlags |= PackageManager.FLAG_PERMISSION_USER_FIXED
                    | PackageManager.FLAG_PERMISSION_USER_SET;
        }
        return (flags & fixedFlags) != 0;
    }

    private static boolean isPermissionGrantedByDefault(@NonNull String packageName,
            @NonNull String permission, @NonNull Context context) {
        int flags = getPermissionFlags(packageName, permission, context);
        return (flags & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0;
    }

    private static void setPermissionFlags(@NonNull String packageName, @NonNull String permission,
            int flags, int mask, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = UserHandle.of(UserHandle.myUserId());
        packageManager.updatePermissionFlags(permission, packageName, mask, flags, user);
    }

    /**
     * Most of the time {@link #isPermissionOrAppOpGranted(String, String, Context)} should be used
     * instead.
     */
    static boolean isPermissionGrantedWithoutCheckingAppOp(@NonNull String packageName,
            @NonNull String permission, @NonNull Context context) {
        return context.getPackageManager().checkPermission(permission, packageName)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isForegroundPermission(@NonNull String permission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sForegroundToBackgroundPermission.containsKey(permission);
    }

    @Nullable
    static String getBackgroundPermission(@NonNull String foregroundPermission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sForegroundToBackgroundPermission.get(foregroundPermission);
    }

    private static boolean isBackgroundPermission(@NonNull String permission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sBackgroundToForegroundPermissions.containsKey(permission);
    }

    @Nullable
    private static List<String> getForegroundPermissions(@NonNull String backgroundPermission,
            @NonNull Context context) {
        ensureForegroundBackgroundPermissionMappings(context);
        return sBackgroundToForegroundPermissions.get(backgroundPermission);
    }

    private static void ensureForegroundBackgroundPermissionMappings(@NonNull Context context) {
        synchronized (sForegroundBackgroundPermissionMappingsLock) {
            if (sForegroundToBackgroundPermission == null
                    && sBackgroundToForegroundPermissions == null) {
                createForegroundBackgroundPermissionMappings(context);
            }
        }
    }

    private static void createForegroundBackgroundPermissionMappings(@NonNull Context context) {
        List<String> permissions = new ArrayList<>();
        sBackgroundToForegroundPermissions = new ArrayMap<>();

        PackageManager packageManager = context.getPackageManager();
        List<PermissionGroupInfo> permissionGroupInfos = packageManager.getAllPermissionGroups(0);

        int permissionGroupInfosSize = permissionGroupInfos.size();
        for (int permissionGroupInfosIndex = 0;
                permissionGroupInfosIndex < permissionGroupInfosSize; permissionGroupInfosIndex++) {
            PermissionGroupInfo permissionGroupInfo = permissionGroupInfos.get(
                    permissionGroupInfosIndex);

            List<PermissionInfo> permissionInfos;
            try {
                permissionInfos = packageManager.queryPermissionsByGroup(permissionGroupInfo.name,
                        0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Cannot get permissions for group: " + permissionGroupInfo.name);
                continue;
            }

            int permissionInfosSize = permissionInfos.size();
            for (int permissionInfosIndex = 0; permissionInfosIndex < permissionInfosSize;
                    permissionInfosIndex++) {
                PermissionInfo permissionInfo = permissionInfos.get(permissionInfosIndex);

                String permission = permissionInfo.name;
                permissions.add(permission);

                String backgroundPermission = permissionInfo.backgroundPermission;
                if (backgroundPermission != null) {
                    List<String> foregroundPermissions = sBackgroundToForegroundPermissions.get(
                            backgroundPermission);
                    if (foregroundPermissions == null) {
                        foregroundPermissions = new ArrayList<>();
                        sBackgroundToForegroundPermissions.put(backgroundPermission,
                                foregroundPermissions);
                    }
                    foregroundPermissions.add(permission);
                }
            }
        }

        // Remove background permissions declared by foreground permissions but don't actually
        // exist.
        sBackgroundToForegroundPermissions.retainAll(permissions);

        // Collect foreground permissions that have existent background permissions.
        sForegroundToBackgroundPermission = new ArrayMap<>();

        int backgroundToForegroundPermissionsSize = sBackgroundToForegroundPermissions.size();
        for (int backgroundToForegroundPermissionsIndex = 0;
                backgroundToForegroundPermissionsIndex < backgroundToForegroundPermissionsSize;
                backgroundToForegroundPermissionsIndex++) {
            String backgroundPerimssion = sBackgroundToForegroundPermissions.keyAt(
                    backgroundToForegroundPermissionsIndex);
            List<String> foregroundPermissions = sBackgroundToForegroundPermissions.valueAt(
                    backgroundToForegroundPermissionsIndex);

            int foregroundPermissionsSize = foregroundPermissions.size();
            for (int foregroundPermissionsIndex = 0;
                    foregroundPermissionsIndex < foregroundPermissionsSize;
                    foregroundPermissionsIndex++) {
                String foregroundPermission = foregroundPermissions.get(foregroundPermissionsIndex);

                sForegroundToBackgroundPermission.put(foregroundPermission, backgroundPerimssion);
            }
        }
    }

    @Nullable
    static String getPermissionAppOp(@NonNull String permission) {
        return AppOpsManager.permissionToOp(permission);
    }

    /**
     * Retrieve an app op mode for an application.
     *
     * @param packageName the package name of the application
     * @param appOp the name of the app op to retrieve
     * @param context the {@code Context} to retrieve system services
     *
     * @return the app op mode for the application, or {@code null} if it cannot be retrieved
     */
    @Nullable
    static Integer getAppOpMode(@NonNull String packageName, @NonNull String appOp,
            @NonNull Context context) {
        ApplicationInfo applicationInfo = Permissions.getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            return null;
        }
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        return appOpsManager.unsafeCheckOpRaw(appOp, applicationInfo.uid, packageName);
    }

    /**
     * Retrieve the default mode of an app op
     *
     * @param appOp the name of the app op to retrieve
     *
     * @return the default mode of the app op
     */
    static int getDefaultAppOpMode(@NonNull String appOp) {
        return AppOpsManager.opToDefaultMode(appOp);
    }

    /**
     * Set an app op mode for an application.
     *
     * @param packageName the package name of the application
     * @param appOp the name of the app op to set
     * @param mode the mode of the app op to set
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether app op mode is changed
     */
    static boolean setAppOpMode(@NonNull String packageName, @NonNull String appOp, int mode,
            @NonNull Context context) {
        Integer currentMode = getAppOpMode(packageName, appOp, context);
        if (currentMode != null && currentMode == mode) {
            return false;
        }
        ApplicationInfo applicationInfo = Permissions.getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            Log.e(LOG_TAG, "Cannot get ApplicationInfo for package to set app op mode: "
                    + packageName);
            return false;
        }
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        appOpsManager.setUidMode(appOp, applicationInfo.uid, mode);
        return true;
    }
}
