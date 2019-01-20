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

import static android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_POLICY_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.GET_PERMISSIONS;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for creating and restoring permission backups.
 */
public class BackupHelper {
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

    private final Context mContext;

    /**
     * Create a new backup utils for a user.
     *
     * @param context A context to use
     * @param user The user that is backed up / restored
     */
    public BackupHelper(@NonNull Context context, @NonNull UserHandle user) {
        try {
            mContext = context.createPackageContextAsUser(context.getPackageName(), 0, user);
        } catch (PackageManager.NameNotFoundException doesNotHappen) {
            throw new IllegalStateException();
        }
    }

    /**
     * Write a xml file for the given packages.
     *
     * @param serializer The file to write to
     * @param pkgs The packages to write
     */
    private static void writePkgsAsXml(@NonNull XmlSerializer serializer,
            @NonNull ArrayList<BackupPackageState> pkgs) throws IOException {
        serializer.startDocument(null, true);

        serializer.startTag(null, TAG_PERMISSION_BACKUP);
        serializer.startTag(null, TAG_ALL_GRANTS);

        int numPkgs = pkgs.size();
        for (int i = 0; i < numPkgs; i++) {
            BackupPackageState packageState = pkgs.get(i);

            if (packageState != null) {
                packageState.writeAsXml(serializer);
            }
        }

        serializer.endTag(null, TAG_ALL_GRANTS);
        serializer.endTag(null, TAG_PERMISSION_BACKUP);

        serializer.endDocument();
    }

    /**
     * Write the state of all packages as XML.
     *
     * @param serializer The xml to write to
     */
    void writeState(@NonNull XmlSerializer serializer) throws IOException {
        List<PackageInfo> pkgs = mContext.getPackageManager().getInstalledPackages(
                GET_PERMISSIONS);
        ArrayList<BackupPackageState> backupPkgs = new ArrayList<>();

        int numPkgs = pkgs.size();
        for (int i = 0; i < numPkgs; i++) {
            BackupPackageState packageState = BackupPackageState.fromAppPermissions(mContext,
                    pkgs.get(i));

            if (packageState != null) {
                backupPkgs.add(packageState);
            }
        }

        writePkgsAsXml(serializer, backupPkgs);
    }

    /**
     * State that needs to be backed up for a permission.
     */
    private static class BackupPermissionState {
        private final @NonNull String mPermissionName;
        private final boolean mIsGranted;
        private final boolean mIsUserSet;
        private final boolean mIsUserFixed;
        private final boolean mShouldRevokeOnUpgrade;

        private BackupPermissionState(@NonNull String permissionName, boolean isGranted,
                boolean isUserSet, boolean isUserFixed, boolean isRevokeOnUpgrade) {
            mPermissionName = permissionName;
            mIsGranted = isGranted;
            mIsUserSet = isUserSet;
            mIsUserFixed = isUserFixed;
            mShouldRevokeOnUpgrade = isRevokeOnUpgrade;
        }

        /**
         * Get the state of a permission to back up.
         *
         * @param perm The permission to back up
         *
         * @return The state to back up or {@code null} if the permission does not need to be
         * backed up.
         */
        private static @Nullable BackupPermissionState fromPermission(@NonNull Permission perm) {
            int grantFlags = perm.getFlags();

            if ((grantFlags & SYSTEM_RUNTIME_GRANT_MASK) == 0
                    && (perm.isGranted() || (grantFlags & USER_RUNTIME_GRANT_MASK) != 0)) {
                return new BackupPermissionState(perm.getName(), perm.isGranted(),
                        perm.isUserSet(), perm.isUserFixed(), perm.shouldRevokeOnUpgrade());
            } else {
                return null;
            }
        }

        /**
         * Get the states of all permissions of a group to back up.
         *
         * @param group The group of the permissions to back up
         *
         * @return The state to back up. Empty list if no permissions in the group need to be backed
         * up
         */
        static @NonNull ArrayList<BackupPermissionState> fromPermissionGroup(
                @NonNull AppPermissionGroup group) {
            ArrayList<BackupPermissionState> permissionsToRestore = new ArrayList<>();
            List<Permission> perms = group.getPermissions();

            int numPerms = perms.size();
            for (int i = 0; i < numPerms; i++) {
                BackupPermissionState permState = fromPermission(perms.get(i));
                if (permState != null) {
                    permissionsToRestore.add(permState);
                }
            }

            return permissionsToRestore;
        }

        /**
         * Write this state as XML.
         *
         * @param serializer The file to write to
         */
        void writeAsXml(@NonNull XmlSerializer serializer) throws IOException {
            serializer.startTag(null, TAG_PERMISSION);

            serializer.attribute(null, ATTR_PERMISSION_NAME, mPermissionName);

            if (mIsGranted) {
                serializer.attribute(null, ATTR_IS_GRANTED, "true");
            }

            if (mIsUserSet) {
                serializer.attribute(null, ATTR_USER_SET, "true");
            }

            if (mIsUserFixed) {
                serializer.attribute(null, ATTR_USER_FIXED, "true");
            }

            if (mShouldRevokeOnUpgrade) {
                serializer.attribute(null, ATTR_REVOKE_ON_UPGRADE, "true");
            }

            serializer.endTag(null, TAG_PERMISSION);
        }
    }

    /**
     * State that needs to be backed up for a package.
     */
    private static class BackupPackageState {
        private final @NonNull String mPackageName;
        private final @NonNull ArrayList<BackupPermissionState> mPermissionsToRestore;

        private BackupPackageState(@NonNull String packageName,
                @NonNull ArrayList<BackupPermissionState> permissionsToRestore) {
            mPackageName = packageName;
            mPermissionsToRestore = permissionsToRestore;
        }

        /**
         * Get the state of a package to back up.
         *
         * @param context A context to use
         * @param pkgInfo The package to back up.
         *
         * @return The state to back up or {@code null} if no permission of the package need to be
         * backed up.
         */
        static @Nullable BackupPackageState fromAppPermissions(@NonNull Context context,
                @NonNull PackageInfo pkgInfo) {
            AppPermissions appPerms = new AppPermissions(context, pkgInfo, false, null);

            ArrayList<BackupPermissionState> permissionsToRestore = new ArrayList<>();
            List<AppPermissionGroup> groups = appPerms.getPermissionGroups();

            int numGroups = groups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionGroup group = groups.get(groupNum);

                permissionsToRestore.addAll(BackupPermissionState.fromPermissionGroup(group));

                // Background permissions are in a subgroup that is not part of
                // {@link AppPermission#getPermissionGroups}. Hence add it explicitly here.
                if (group.getBackgroundPermissions() != null) {
                    permissionsToRestore.addAll(BackupPermissionState.fromPermissionGroup(
                            group.getBackgroundPermissions()));
                }
            }

            if (permissionsToRestore.size() == 0) {
                return null;
            }

            return new BackupPackageState(pkgInfo.packageName, permissionsToRestore);
        }

        /**
         * Write this state as XML.
         *
         * @param serializer The file to write to
         */
        void writeAsXml(@NonNull XmlSerializer serializer) throws IOException {
            if (mPermissionsToRestore.size() == 0) {
                return;
            }

            serializer.startTag(null, TAG_GRANT);
            serializer.attribute(null, ATTR_PACKAGE_NAME, mPackageName);

            int numPerms = mPermissionsToRestore.size();
            for (int i = 0; i < numPerms; i++) {
                mPermissionsToRestore.get(i).writeAsXml(serializer);
            }

            serializer.endTag(null, TAG_GRANT);
        }
    }
}
