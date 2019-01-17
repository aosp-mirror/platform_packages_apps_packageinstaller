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

import static android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_POLICY_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.permission.PermissionControllerManager.REASON_INSTALLER_POLICY_VIOLATION;
import static android.permission.PermissionControllerManager.REASON_MALWARE;
import static android.util.Xml.newSerializer;

import static com.android.packageinstaller.permission.utils.Utils.getLauncherPackages;
import static com.android.packageinstaller.permission.utils.Utils.isSystem;
import static com.android.packageinstaller.permission.utils.Utils.shouldShowPermission;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.permission.PermissionControllerService;
import android.permission.PermissionManager;
import android.permission.RuntimePermissionPresentationInfo;
import android.permission.RuntimePermissionUsageInfo;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.utils.Utils;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls from the system into the permission controller
 */
public final class PermissionControllerServiceImpl extends PermissionControllerService {
    private static final String LOG_TAG = PermissionControllerServiceImpl.class.getSimpleName();

    private static final String TAG_PERMISSION_BACKUP = "perm-grant-backup";

    private static final String TAG_ALL_GRANTS = "rt-grants";

    private static final String TAG_GRANT = "grant";
    private static final String ATTR_PACKAGE_NAME = "pkg";

    private static final String TAG_PERMISSION = "perm";
    private static final String ATTR_PERMISSION_NAME = "name";
    private static final String ATTR_IS_GRANTED = "g";
    private static final String ATTR_USER_SET = "set";
    private static final String ATTR_USER_FIXED = "fixed";
    private static final String ATTR_REVOKE_ON_UPGRADE = "rou";

    /** Flags of permissions to <u>not</u> back up */
    private static final int SYSTEM_RUNTIME_GRANT_MASK = FLAG_PERMISSION_POLICY_FIXED
            | FLAG_PERMISSION_SYSTEM_FIXED
            | FLAG_PERMISSION_GRANTED_BY_DEFAULT;

    /** Flags that need to be backed up even if permission is revoked */
    private static final int USER_RUNTIME_GRANT_MASK = FLAG_PERMISSION_USER_SET
            | FLAG_PERMISSION_USER_FIXED
            | FLAG_PERMISSION_REVOKE_ON_UPGRADE;

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

    /**
     * Backup state of a permission if needed.
     *
     * @param serializer The serializer to back up to
     * @param perm The permission to back up
     * @param beforeBackup code to run before adding to the backup
     *
     * @throws IOException if there was an issue while backing up
     */
    private void backupPermissionState(@NonNull XmlSerializer serializer,
            @NonNull Permission perm, @NonNull Supplier<IOException> beforeBackup)
            throws IOException {
        int grantFlags = perm.getFlags();

        // only look at grants that are not system/policy fixed
        if ((grantFlags & SYSTEM_RUNTIME_GRANT_MASK) == 0) {
            // And only back up the user-twiddled state bits
            if (perm.isGranted() || (grantFlags & USER_RUNTIME_GRANT_MASK) != 0) {
                IOException errorWhileStartingBackup = beforeBackup.get();
                if (errorWhileStartingBackup != null) {
                    throw errorWhileStartingBackup;
                }

                serializer.startTag(null, TAG_PERMISSION);
                serializer.attribute(null, ATTR_PERMISSION_NAME, perm.getName());

                if (perm.isGranted()) {
                    serializer.attribute(null, ATTR_IS_GRANTED, "true");
                }

                if (perm.isUserSet()) {
                    serializer.attribute(null, ATTR_USER_SET, "true");
                }

                if (perm.isUserFixed()) {
                    serializer.attribute(null, ATTR_USER_FIXED, "true");
                }

                if ((grantFlags & FLAG_PERMISSION_REVOKE_ON_UPGRADE) != 0) {
                    serializer.attribute(null, ATTR_REVOKE_ON_UPGRADE, "true");
                }

                serializer.endTag(null, TAG_PERMISSION);
            }
        }
    }

    /**
     * Backup state of all permission in this group.
     *
     * @param serializer The serializer to back up to
     * @param group The group to back up
     * @param beforeBackup code to run before adding to the backup
     *
     * @throws IOException if there was an issue while backing up
     */
    private void backupGroupState(@NonNull XmlSerializer serializer,
            @NonNull AppPermissionGroup group, @NonNull Supplier<IOException> beforeBackup)
            throws IOException {
        List<Permission> perms = group.getPermissions();

        int numPerms = perms.size();
        for (int i = 0; i < numPerms; i++) {
            backupPermissionState(serializer, perms.get(i), beforeBackup);
        }

        // Background permissions are in a subgroup that is not part of
        // {@link AppPermission#getPermissionGroups}. Hence add it explicitly here.
        if (group.getBackgroundPermissions() != null) {
            backupGroupState(serializer, group.getBackgroundPermissions(), beforeBackup);
        }
    }

    /**
     * Backup per-app runtime permission state.
     *
     * @param serializer The serializer to back up to
     * @param app The app to back up
     *
     * @throws IOException if there was an issue while backing up
     */
    private void backupAppState(@NonNull XmlSerializer serializer, @NonNull AppPermissions app)
            throws IOException {
        List<AppPermissionGroup> groups = app.getPermissionGroups();

        // We want to delay adding the per-package tag (TAG_GRANT) until we find a permission that
        // is needs to be backed up. This let's us avoid a lot of empty TAG_GRANT tags.
        final boolean[] wasAnyPermissionBackedUp = {false};

        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            backupGroupState(serializer, groups.get(i), () -> {
                if (!wasAnyPermissionBackedUp[0]) {
                    try {
                        serializer.startTag(null, TAG_GRANT);
                        serializer.attribute(null, ATTR_PACKAGE_NAME,
                                app.getPackageInfo().packageName);
                    } catch (IOException e) {
                        return e;
                    }

                    wasAnyPermissionBackedUp[0] = true;
                }

                return null;
            });
        }

        if (wasAnyPermissionBackedUp[0]) {
            serializer.endTag(null, TAG_GRANT);
        }
    }

    @Override
    public void onGetRuntimePermissionsBackup(@NonNull UserHandle user, @NonNull OutputStream out) {
        try {
            XmlSerializer serializer = newSerializer();
            serializer.setOutput(out, UTF_8.name());
            serializer.startDocument(null, true);

            serializer.startTag(null, TAG_PERMISSION_BACKUP);
            serializer.startTag(null, TAG_ALL_GRANTS);

            List<PackageInfo> pkgs = getPackageManager().getInstalledPackagesAsUser(GET_PERMISSIONS,
                    user.getIdentifier());

            int numPkgs = pkgs.size();
            for (int i = 0; i < numPkgs; i++) {
                backupAppState(serializer, new AppPermissions(this, pkgs.get(i), false, null));
            }

            serializer.endTag(null, TAG_ALL_GRANTS);
            serializer.endTag(null, TAG_PERMISSION_BACKUP);

            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to write default apps for backup", e);
        }
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

    @Override public @NonNull List<RuntimePermissionUsageInfo> onPermissionUsageResult(
            boolean countSystem, long numMillis) {
        ArraySet<String> launcherPkgs = getLauncherPackages(this);

        ArrayMap<CharSequence, Integer> groupUsers = new ArrayMap<>();

        long curTime = System.currentTimeMillis();
        PermissionUsages usages = new PermissionUsages(this);
        long filterTimeBeginMillis = Math.max(System.currentTimeMillis() - numMillis, 0);
        usages.load(null, null, filterTimeBeginMillis, Long.MAX_VALUE,
                PermissionUsages.USAGE_FLAG_LAST | PermissionUsages.USAGE_FLAG_HISTORICAL, null,
                false, null, true);

        List<AppPermissionUsage> appPermissionUsages = usages.getUsages();
        int numApps = appPermissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appPermissionUsage = appPermissionUsages.get(appNum);

            if (appPermissionUsage.getAccessCount() <= 0) {
                continue;
            }
            if (!countSystem && isSystem(appPermissionUsage.getApp(), launcherPkgs)) {
                continue;
            }

            List<GroupUsage> appGroups = appPermissionUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                GroupUsage groupUsage = appGroups.get(groupNum);

                if (groupUsage.getAccessCount() <= 0) {
                    continue;
                }
                if (!shouldShowPermission(this, groupUsage.getGroup())) {
                    continue;
                }

                CharSequence groupLabel = groupUsage.getGroup().getName();
                Integer numUsers = groupUsers.get(groupLabel);
                if (numUsers == null) {
                    groupUsers.put(groupLabel, 1);
                } else {
                    groupUsers.put(groupLabel, numUsers + 1);
                }
            }
        }

        List<RuntimePermissionUsageInfo> users = new ArrayList<>();
        int numGroups = groupUsers.size();
        for (int groupNum = 0; groupNum < numGroups; groupNum++) {
            users.add(new RuntimePermissionUsageInfo(groupUsers.keyAt(groupNum),
                    groupUsers.valueAt(groupNum)));
        }
        return users;
    }
}
