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
import static android.permission.PermissionControllerManager.REASON_INSTALLER_POLICY_VIOLATION;
import static android.permission.PermissionControllerManager.REASON_MALWARE;

import static com.android.packageinstaller.permission.utils.Utils.getLauncherPackages;
import static com.android.packageinstaller.permission.utils.Utils.isSystem;
import static com.android.packageinstaller.permission.utils.Utils.shouldShowPermission;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.permission.PermissionControllerService;
import android.permission.PermissionManager;
import android.permission.RuntimePermissionPresentationInfo;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calls from the system into the permission controller
 */
public final class PermissionControllerServiceImpl extends PermissionControllerService {
    private static final String LOG_TAG = PermissionControllerServiceImpl.class.getSimpleName();

    /**
     * Expand {@code perms} by split permissions for an app with the given targetSDK.
     *
     * @param perms The permissions that should be expanded
     * @param targetSDK The target SDK to expand for
     *
     * @return The expanded permissions
     */
    private @NonNull ArrayList<String> addSplitPermissions(@NonNull List<String> perms,
            int targetSDK) {
        List<PermissionManager.SplitPermissionInfo> splitPerms =
                getSystemService(PermissionManager.class).getSplitPermissions();

        // Add split permissions to the request
        ArrayList<String> expandedPerms = new ArrayList<>(perms);
        int numReqPerms = perms.size();
        for (int reqPermNum = 0; reqPermNum < numReqPerms; reqPermNum++) {
            String reqPerm = perms.get(reqPermNum);

            int numSplitPerms = splitPerms.size();
            for (int splitPermNum = 0; splitPermNum < numSplitPerms; splitPermNum++) {
                PermissionManager.SplitPermissionInfo splitPerm = splitPerms.get(splitPermNum);

                if (targetSDK < splitPerm.getTargetSdk()
                        && splitPerm.getSplitPermission().equals(reqPerm)) {
                    expandedPerms.addAll(splitPerm.getNewPermissions());
                }
            }
        }

        return expandedPerms;
    }

    /**
     * Get the package info for a package.
     *
     * @param pkg The package name
     *
     * @return the package info or {@code null} if the package could not be found
     */
    private @Nullable PackageInfo getPkgInfo(@NonNull String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(LOG_TAG, pkg + " not found", e);
            return null;
        }
    }

    /**
     * Given a set of permissions, find all permission groups of an app that can be revoked and that
     * contain any of the permissions.
     *
     * @param permissions The permissions to revoke
     * @param appPerms The {@link AppPermissions} for the app that is currently investigated
     *
     * @return The groups to revoke
     */
    private @NonNull ArrayList<AppPermissionGroup> getRevocableGroupsForPermissions(
            @NonNull ArrayList<String> permissions, @NonNull AppPermissions appPerms) {
        ArrayList<AppPermissionGroup> groupsToRevoke = new ArrayList<>();
        int numGroups = appPerms.getPermissionGroups().size();
        for (int groupNum = 0; groupNum < numGroups; groupNum++) {
            AppPermissionGroup group = appPerms.getPermissionGroups().get(groupNum);

            // Do not override fixed permissions
            if (group.isPolicyFixed() || group.isSystemFixed()) {
                continue;
            }

            int numPerms = permissions.size();
            for (int permNum = 0; permNum < numPerms; permNum++) {
                String reqPerm = permissions.get(permNum);

                if (group.hasPermission(reqPerm)) {
                    groupsToRevoke.add(group);

                    // If fg permissions get revoked also revoke bg permissions as bg
                    // permissions require fg permissions.
                    AppPermissionGroup bgPerms = group.getBackgroundPermissions();
                    if (bgPerms != null) {
                        groupsToRevoke.add(bgPerms);
                    }
                } else {
                    AppPermissionGroup bgPerms = group.getBackgroundPermissions();
                    if (bgPerms != null && bgPerms.hasPermission(reqPerm)) {
                        groupsToRevoke.add(bgPerms);
                    }
                }
            }
        }

        return groupsToRevoke;
    }

    /**
     * Revoke all permissions of some groups.
     *
     * @param groupsToRevoke The groups
     *
     * @return The permissions that were revoked
     */
    private @NonNull ArrayList<String> revokePermissionGroups(
            @NonNull ArrayList<AppPermissionGroup> groupsToRevoke) {
        ArrayList<String> revokedPerms = new ArrayList<>();

        int numGroupsToRevoke = groupsToRevoke.size();
        for (int groupsToRevokeNum = 0; groupsToRevokeNum < numGroupsToRevoke;
                groupsToRevokeNum++) {
            AppPermissionGroup group = groupsToRevoke.get(groupsToRevokeNum);
            ArrayList<Permission> perms = group.getPermissions();

            // Mark the permissions as reviewed as we don't want to use to accidentally grant
            // the permission during review
            group.resetReviewRequired();

            int numPerms = perms.size();
            for (int permNum = 0; permNum < numPerms; permNum++) {
                Permission perm = perms.get(permNum);

                // Only count individual permissions that are actually revoked
                if (perm.isGrantedIncludingAppOp()) {
                    revokedPerms.add(perm.getName());
                }
            }

            group.revokeRuntimePermissions(false);
        }

        return revokedPerms;
    }

    @Override
    public @NonNull Map<String, List<String>> onRevokeRuntimePermissions(
            @NonNull Map<String, List<String>> request, boolean doDryRun,
            int reason, @NonNull String callerPackageName) {
        // The reason parameter is not checked by platform code as this might need to be updated
        // async to platform releases.
        if (reason != REASON_MALWARE && reason != REASON_INSTALLER_POLICY_VIOLATION) {
            Log.e(LOG_TAG, "Invalid reason " + reason);
            return Collections.emptyMap();
        }

        PackageManager pm = getPackageManager();

        PackageInfo callerPkgInfo = getPkgInfo(callerPackageName);
        if (callerPkgInfo == null) {
            return Collections.emptyMap();
        }
        int callerTargetSdk = callerPkgInfo.applicationInfo.targetSdkVersion;

        Map<String, List<String>> actuallyRevokedPerms = new ArrayMap<>();
        ArrayList<AppPermissions> appsWithRevokedPerms = new ArrayList<>();

        for (Map.Entry<String, List<String>> appRequest : request.entrySet()) {
            PackageInfo requestedPkgInfo = getPkgInfo(appRequest.getKey());
            if (requestedPkgInfo == null) {
                continue;
            }

            // Permissions are per UID. Hence permissions will be removed from all apps sharing an
            // UID.
            String[] pkgNames = pm.getPackagesForUid(requestedPkgInfo.applicationInfo.uid);
            if (pkgNames == null) {
                continue;
            }

            int numPkgNames = pkgNames.length;
            for (int pkgNum = 0; pkgNum < numPkgNames; pkgNum++) {
                String pkgName = pkgNames[pkgNum];

                PackageInfo pkgInfo = getPkgInfo(pkgName);
                if (pkgInfo == null) {
                    continue;
                }

                // If the revocation is because of a market policy violation only the installer can
                // revoke the permissions.
                if (reason == REASON_INSTALLER_POLICY_VIOLATION
                        && !callerPackageName.equals(pm.getInstallerPackageName(pkgName))) {
                    Log.i(LOG_TAG, "Ignoring " + pkgName + " as it is not installed by "
                            + callerPackageName);
                    continue;
                }

                // In rare cases the caller does not know about the permissions that have been added
                // due to splits. Hence add them now.
                ArrayList<String> expandedPerms = addSplitPermissions(appRequest.getValue(),
                        callerTargetSdk);

                AppPermissions appPerms = new AppPermissions(this, pkgInfo, false, true, null);

                // First find the groups that should be revoked and then revoke all permissions of
                // these groups. This is needed as soon as a single permission in the group is
                // granted, all other permissions get auto-granted on request.
                ArrayList<AppPermissionGroup> groupsToRevoke = getRevocableGroupsForPermissions(
                        expandedPerms, appPerms);
                ArrayList<String> revokedPerms = revokePermissionGroups(groupsToRevoke);

                // In racy conditions the group might not have had granted permissions anymore
                if (!revokedPerms.isEmpty()) {
                    actuallyRevokedPerms.put(pkgName, revokedPerms);
                    appsWithRevokedPerms.add(appPerms);
                }
            }
        }

        // Persist changes after we computed everything to remove
        // This is necessary as we would otherwise only look at the first app of a shared UID.
        if (!doDryRun) {
            int numChangedApps = appsWithRevokedPerms.size();
            for (int i = 0; i < numChangedApps; i++) {
                appsWithRevokedPerms.get(i).persistChanges();
            }
        }

        return actuallyRevokedPerms;
    }

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
