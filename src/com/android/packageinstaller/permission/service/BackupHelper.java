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

import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_POLICY_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.util.Xml.newSerializer;

import static com.android.packageinstaller.Constants.DELAYED_RESTORE_PERMISSIONS_FILE;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.Constants;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for creating and restoring permission backups.
 */
public class BackupHelper {
    private static final String LOG_TAG = BackupHelper.class.getSimpleName();

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

    /** Make sure only one user can change the delayed permissions at a time */
    private static final Object sLock = new Object();

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
     * Forward parser and skip everything up to the end of the current tag.
     *
     * @param parser The parser to forward
     */
    private static void skipToEndOfTag(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int numOpenTags = 1;
        while (numOpenTags > 0) {
            switch (parser.next()) {
                case START_TAG:
                    numOpenTags++;
                    break;
                case END_TAG:
                    numOpenTags--;
                    break;
                default:
                    // ignore
            }
        }
    }

    /**
     * Forward parser to a given direct sub-tag.
     *
     * @param parser The parser to forward
     * @param tag The tag to search for
     */
    private void skipToTag(@NonNull XmlPullParser parser, @NonNull String tag)
            throws IOException, XmlPullParserException {
        int type;
        do {
            type = parser.next();

            switch (type) {
                case START_TAG:
                    if (!parser.getName().equals(tag)) {
                        skipToEndOfTag(parser);
                    }

                    return;
            }
        } while (type != END_DOCUMENT);
    }

    /**
     * Read a XML file and return the packages stored in it.
     *
     * @param parser The file to read
     *
     * @return The packages in this file
     */
    private @NonNull ArrayList<BackupPackageState> parseFromXml(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        ArrayList<BackupPackageState> pkgStates = new ArrayList<>();

        skipToTag(parser, TAG_PERMISSION_BACKUP);
        skipToTag(parser, TAG_ALL_GRANTS);

        if (parser.getEventType() != START_TAG && !parser.getName().equals(TAG_ALL_GRANTS)) {
            throw new XmlPullParserException("Could not find " + TAG_PERMISSION_BACKUP + " > "
                    + TAG_ALL_GRANTS);
        }

        // Read packages to restore from xml
        int type;
        do {
            type = parser.next();

            switch (type) {
                case START_TAG:
                    switch (parser.getName()) {
                        case TAG_GRANT:
                            try {
                                pkgStates.add(BackupPackageState.parseFromXml(parser));
                            } catch (XmlPullParserException e) {
                                Log.e(LOG_TAG, "Could not parse permissions ", e);
                                skipToEndOfTag(parser);
                            }
                            break;
                        default:
                            // ignore tag
                            Log.w(LOG_TAG, "Found unexpected tag " + parser.getName()
                                    + " during restore");
                            skipToEndOfTag(parser);
                    }
            }
        } while (type != END_TAG);

        return pkgStates;
    }

    /**
     * Try to restore the permission state from XML.
     *
     * <p>If some apps could not be restored, the leftover apps are written to
     * {@link Constants#DELAYED_RESTORE_PERMISSIONS_FILE}.
     *
     * @param parser The xml to read
     */
    void restoreState(@NonNull XmlPullParser parser) throws IOException, XmlPullParserException {
        ArrayList<BackupPackageState> pkgStates = parseFromXml(parser);

        ArrayList<BackupPackageState> packagesToRestoreLater = new ArrayList<>();
        int numPkgStates = pkgStates.size();
        if (numPkgStates > 0) {
            // Try to restore packages
            for (int i = 0; i < numPkgStates; i++) {
                BackupPackageState pkgState = pkgStates.get(i);

                PackageInfo pkgInfo;
                try {
                    pkgInfo = mContext.getPackageManager().getPackageInfo(pkgState.mPackageName,
                            GET_PERMISSIONS);
                } catch (PackageManager.NameNotFoundException ignored) {
                    packagesToRestoreLater.add(pkgState);
                    continue;
                }

                pkgState.restore(mContext, pkgInfo);
            }
        }

        synchronized (sLock) {
            writeDelayedStorePkgsLocked(packagesToRestoreLater);
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
     * Update the {@link Constants#DELAYED_RESTORE_PERMISSIONS_FILE} to contain the
     * {@code packagesToRestoreLater}.
     *
     * @param packagesToRestoreLater The new pkgs in the delayed restore file
     */
    private void writeDelayedStorePkgsLocked(
            @NonNull ArrayList<BackupPackageState> packagesToRestoreLater) {
        try (OutputStream delayedRestoreData = mContext.openFileOutput(
                DELAYED_RESTORE_PERMISSIONS_FILE, MODE_PRIVATE)) {
            XmlSerializer serializer = newSerializer();
            serializer.setOutput(delayedRestoreData, UTF_8.name());

            writePkgsAsXml(serializer, packagesToRestoreLater);
            serializer.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not remember which packages still need to be restored", e);
        }
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
     * Restore delayed permission state for a package (if delayed during {@link #restoreState}).
     *
     * @param packageName The package to be restored
     *
     * @return {@code true} if there is still delayed backup left
     */
    boolean restoreDelayedState(@NonNull String packageName) {
        synchronized (sLock) {
            ArrayList<BackupPackageState> packagesToRestoreLater;

            try (FileInputStream delayedRestoreData =
                         mContext.openFileInput(DELAYED_RESTORE_PERMISSIONS_FILE)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(delayedRestoreData, UTF_8.name());

                packagesToRestoreLater = parseFromXml(parser);
            } catch (IOException | XmlPullParserException e) {
                Log.e(LOG_TAG, "Could not parse delayed permissions", e);
                return false;
            }

            PackageInfo pkgInfo = null;
            try {
                pkgInfo = mContext.getPackageManager().getPackageInfo(packageName, GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Could not restore delayed permissions for " + packageName, e);
            }

            if (pkgInfo != null) {
                int numPkgs = packagesToRestoreLater.size();
                for (int i = 0; i < numPkgs; i++) {
                    BackupPackageState pkgState = packagesToRestoreLater.get(i);

                    if (pkgState.mPackageName.equals(packageName)) {
                        pkgState.restore(mContext, pkgInfo);
                        packagesToRestoreLater.remove(i);

                        writeDelayedStorePkgsLocked(packagesToRestoreLater);

                        break;
                    }
                }
            }

            return packagesToRestoreLater.size() > 0;
        }
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
         * Parse a package state from XML.
         *
         * @param parser The data to read
         *
         * @return The state
         */
        static @NonNull BackupPermissionState parseFromXml(@NonNull XmlPullParser parser)
                throws XmlPullParserException {
            String permName = parser.getAttributeValue(null, ATTR_PERMISSION_NAME);
            if (permName == null) {
                throw new XmlPullParserException("Found " + TAG_PERMISSION + " without "
                        + ATTR_PERMISSION_NAME);
            }

            return new BackupPermissionState(permName,
                    "true".equals(parser.getAttributeValue(null, ATTR_IS_GRANTED)),
                    "true".equals(parser.getAttributeValue(null, ATTR_USER_SET)),
                    "true".equals(parser.getAttributeValue(null, ATTR_USER_FIXED)),
                    "true".equals(parser.getAttributeValue(null, ATTR_REVOKE_ON_UPGRADE)));
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

        /**
         * Restore this permission state.
         *
         * @param appPerms The {@link AppPermissions} to restore the state to
         */
        void restore(@NonNull AppPermissions appPerms) {
            AppPermissionGroup group = appPerms.getGroupForPermission(mPermissionName);
            if (group == null) {
                Log.w(LOG_TAG, "Could not find group for " + mPermissionName + " in "
                        + appPerms.getPackageInfo().packageName);
                return;
            }

            if (mIsGranted) {
                group.grantRuntimePermissions(/* is overridden below */false,
                        new String[]{mPermissionName});
            }

            Permission perm = group.getPermission(mPermissionName);
            perm.setUserSet(mIsUserSet);
            perm.setUserFixed(mIsUserFixed);
            perm.setRevokeOnUpgrade(mShouldRevokeOnUpgrade);
        }
    }

    /**
     * State that needs to be backed up for a package.
     */
    private static class BackupPackageState {
        final @NonNull String mPackageName;
        private final @NonNull ArrayList<BackupPermissionState> mPermissionsToRestore;

        private BackupPackageState(@NonNull String packageName,
                @NonNull ArrayList<BackupPermissionState> permissionsToRestore) {
            mPackageName = packageName;
            mPermissionsToRestore = permissionsToRestore;
        }

        /**
         * Parse a package state from XML.
         *
         * @param parser The data to read
         *
         * @return The state
         */
        static @NonNull BackupPackageState parseFromXml(@NonNull XmlPullParser parser)
                throws IOException, XmlPullParserException {
            String packageName = parser.getAttributeValue(null, ATTR_PACKAGE_NAME);
            if (packageName == null) {
                throw new XmlPullParserException("Found " + TAG_GRANT + " without "
                        + ATTR_PACKAGE_NAME);
            }

            ArrayList<BackupPermissionState> permissionsToRestore = new ArrayList<>();

            while (true) {
                switch (parser.next()) {
                    case START_TAG:
                        switch (parser.getName()) {
                            case TAG_PERMISSION:
                                try {
                                    permissionsToRestore.add(
                                            BackupPermissionState.parseFromXml(parser));
                                } catch (XmlPullParserException e) {
                                    Log.e(LOG_TAG, "Could not parse permission for "
                                            + packageName, e);
                                    skipToEndOfTag(parser);
                                }
                                break;
                            default:
                                // ignore tag
                                Log.w(LOG_TAG, "Found unexpected tag " + parser.getName()
                                        + " while restoring " + packageName);
                                skipToEndOfTag(parser);
                        }

                        break;
                    case END_TAG:
                        return new BackupPackageState(packageName, permissionsToRestore);
                    case END_DOCUMENT:
                        throw new XmlPullParserException("Could not parse state for "
                                + packageName);
                }
            }
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

        /**
         * Restore this package state.
         *
         * @param context A context to use
         * @param pkgInfo The package to restore.
         */
        void restore(@NonNull Context context, @NonNull PackageInfo pkgInfo) {
            AppPermissions appPerms = new AppPermissions(context, pkgInfo, false, true, null);

            int numPerms = mPermissionsToRestore.size();
            for (int i = 0; i < numPerms; i++) {
                mPermissionsToRestore.get(i).restore(appPerms);
            }

            appPerms.persistChanges();
        }
    }
}
