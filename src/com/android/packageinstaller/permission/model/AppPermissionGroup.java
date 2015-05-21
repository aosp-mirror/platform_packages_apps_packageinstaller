/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.packageinstaller.permission.model;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArrayMap;

import com.android.packageinstaller.R;

import java.util.ArrayList;
import java.util.List;

public final class AppPermissionGroup implements Comparable<AppPermissionGroup> {
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final String KILL_REASON_APP_OP_CHANGE = "Permission related app op changed";

    private final Context mContext;
    private final UserHandle mUserHandle;
    private final PackageManager mPackageManager;
    private final AppOpsManager mAppOps;
    private final ActivityManager mActivityManager;

    private final PackageInfo mPackageInfo;
    private final String mName;
    private final String mDeclaringPackage;
    private final CharSequence mLabel;
    private final CharSequence mDescription;
    private final ArrayMap<String, Permission> mPermissions = new ArrayMap<>();
    private final String mIconPkg;
    private final int mIconResId;

    private final boolean mAppSupportsRuntimePermissions;

    public static AppPermissionGroup create(Context context, PackageInfo packageInfo,
            String permissionName) {
        PermissionInfo permissionInfo;
        try {
            permissionInfo = context.getPackageManager().getPermissionInfo(permissionName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        if (permissionInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS) {
            return null;
        }

        PackageItemInfo groupInfo = permissionInfo;
        if (permissionInfo.group != null) {
            try {
                groupInfo = context.getPackageManager().getPermissionGroupInfo(
                        permissionInfo.group, 0);
            } catch (PackageManager.NameNotFoundException e) {
                /* ignore */
            }
        }

        List<PermissionInfo> permissionInfos = null;
        if (groupInfo instanceof PermissionGroupInfo) {
            try {
                permissionInfos = context.getPackageManager().queryPermissionsByGroup(
                        groupInfo.name, 0);
            } catch (PackageManager.NameNotFoundException e) {
                /* ignore */
            }
        }

        return create(context, packageInfo, groupInfo, permissionInfos);
    }

    public static AppPermissionGroup create(Context context, PackageInfo packageInfo,
            PackageItemInfo groupInfo, List<PermissionInfo> permissionInfos) {

        AppPermissionGroup group = new AppPermissionGroup(context, packageInfo, groupInfo.name,
                groupInfo.packageName, groupInfo.loadLabel(context.getPackageManager()),
                loadGroupDescription(context, groupInfo), groupInfo.packageName, groupInfo.icon);

        if (groupInfo instanceof PermissionInfo) {
            permissionInfos = new ArrayList<>();
            permissionInfos.add((PermissionInfo) groupInfo);
        }

        if (permissionInfos == null || permissionInfos.isEmpty()) {
            return null;
        }

        final int permissionCount = packageInfo.requestedPermissions.length;
        for (int i = 0; i < permissionCount; i++) {
            String requestedPermission = packageInfo.requestedPermissions[i];

            PermissionInfo requestedPermissionInfo = null;

            for (PermissionInfo permissionInfo : permissionInfos) {
                if (requestedPermission.equals(permissionInfo.name)) {
                    requestedPermissionInfo = permissionInfo;
                    break;
                }
            }

            if (requestedPermissionInfo == null) {
                continue;
            }

            // Collect only runtime permissions.
            if (requestedPermissionInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS) {
                continue;
            }

            // Don't allow toggle of non platform defined permissions for legacy apps via app ops.
            if (packageInfo.applicationInfo.targetSdkVersion <= Build.VERSION_CODES.LOLLIPOP_MR1
                    && !PLATFORM_PACKAGE_NAME.equals(requestedPermissionInfo.packageName)) {
                continue;
            }

            final boolean granted = (packageInfo.requestedPermissionsFlags[i]
                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;

            final int appOp = PLATFORM_PACKAGE_NAME.equals(requestedPermissionInfo.packageName)
                    ? AppOpsManager.permissionToOpCode(requestedPermissionInfo.name)
                    : AppOpsManager.OP_NONE;

            final boolean appOpAllowed = appOp != AppOpsManager.OP_NONE
                    && context.getSystemService(AppOpsManager.class).checkOp(appOp,
                    packageInfo.applicationInfo.uid, packageInfo.packageName)
                    == AppOpsManager.MODE_ALLOWED;

            final int flags = context.getPackageManager().getPermissionFlags(
                    requestedPermission, packageInfo.packageName,
                    new UserHandle(context.getUserId()));

            Permission permission = new Permission(requestedPermission, granted,
                    appOp, appOpAllowed, flags);
            group.addPermission(permission);
        }

        return group;
    }

    private static CharSequence loadGroupDescription(Context context, PackageItemInfo group) {
        CharSequence description = null;
        if (group instanceof PermissionGroupInfo) {
            description = ((PermissionGroupInfo) group).loadDescription(
                    context.getPackageManager());
        } else if (group instanceof PermissionInfo) {
            description = ((PermissionInfo) group).loadDescription(
                    context.getPackageManager());
        }

        if (description == null || description.length() <= 0) {
            description = context.getString(R.string.default_permission_description);
        }

        return description;
    }

    private AppPermissionGroup(Context context, PackageInfo packageInfo, String name,
            String declaringPackage, CharSequence label, CharSequence description,
            String iconPkg, int iconResId) {
        mContext = context;
        mUserHandle = new UserHandle(mContext.getUserId());
        mPackageManager = mContext.getPackageManager();
        mPackageInfo = packageInfo;
        mAppSupportsRuntimePermissions = packageInfo.applicationInfo
                .targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
        mAppOps = context.getSystemService(AppOpsManager.class);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mDeclaringPackage = declaringPackage;
        mName = name;
        mLabel = label;
        mDescription = description;
        if (iconResId != 0) {
            mIconPkg = iconPkg;
            mIconResId = iconResId;
        } else {
            mIconPkg = context.getPackageName();
            mIconResId = R.drawable.ic_perm_device_info;
        }
    }

    public boolean hasRuntimePermission() {
        return mAppSupportsRuntimePermissions;
    }

    public boolean hasAppOpPermission() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.getAppOp() != AppOpsManager.OP_NONE) {
                return true;
            }
        }
        return false;
    }

    public PackageInfo getApp() {
        return mPackageInfo;
    }

    public String getName() {
        return mName;
    }

    public String getDeclaringPackage() {
        return mDeclaringPackage;
    }

    public String getIconPkg() {
        return mIconPkg;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public CharSequence getDescription() {
        return mDescription;
    }

    public boolean hasPermission(String permission) {
        return mPermissions.get(permission) != null;
    }

    public boolean areRuntimePermissionsGranted() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (mAppSupportsRuntimePermissions) {
                if (!permission.isGranted()) {
                    return false;
                }
            } else if (permission.isGranted() && permission.getAppOp()
                    != AppOpsManager.OP_NONE && !permission.isAppOpAllowed()) {
                return false;
            }
        }
        return true;
    }

    public boolean grantRuntimePermissions(boolean fixedByTheUser) {
        final boolean isSharedUser = mPackageInfo.sharedUserId != null;
        final int uid = mPackageInfo.applicationInfo.uid;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        for (Permission permission : mPermissions.values()) {
            if (mAppSupportsRuntimePermissions) {
                // Do not touch permissions fixed by the system.
                if (permission.isSystemFixed()) {
                    return false;
                }

                // Grant the permission if needed.
                if (!permission.isGranted()) {
                    permission.setGranted(true);
                    mPackageManager.grantRuntimePermission(mPackageInfo.packageName,
                            permission.getName(), new UserHandle(mContext.getUserId()));
                }

                // Update the permission flags.
                if (!fixedByTheUser) {
                    // Now the apps can ask for the permission as the user
                    // no longer has it fixed in a denied state.
                    if (permission.isUserFixed() || permission.isUserSet()) {
                        permission.setUserFixed(false);
                        permission.setUserSet(true);
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_USER_FIXED
                                        | PackageManager.FLAG_PERMISSION_USER_SET,
                                0, mUserHandle);
                    }
                }

                // Enable the permission app op.
                if (permission.hasAppOp() && !permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true);
                    mAppOps.setMode(permission.getAppOp(), android.os.Process.myUid(),
                            mPackageInfo.packageName, AppOpsManager.MODE_ALLOWED);
                }
            } else {
                // Legacy apps cannot have a not granted permission but just in case.
                // Also if the permissions has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.
                if (!permission.isGranted() || !permission.hasAppOp()) {
                    continue;
                }

                if (!permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(true);
                    // It this is a shared user we want to enable the app op for all
                    // packages in the shared user to match the behavior of this
                    // shared user having a runtime permission.
                    if (isSharedUser) {
                        // Enable the app op.
                        String[] packageNames = mPackageManager.getPackagesForUid(uid);
                        for (String packageName : packageNames) {
                            mAppOps.setMode(permission.getAppOp(), uid, packageName,
                                    AppOpsManager.MODE_ALLOWED);
                        }
                    } else {
                        // Enable the app op.
                        mAppOps.setMode(permission.getAppOp(), uid, mPackageInfo.packageName,
                                AppOpsManager.MODE_ALLOWED);
                    }

                    // Mark that the permission should not be be granted on upgrade
                    // when the app begins supporting runtime permissions.
                    if (permission.shouldRevokeOnUpgrade()) {
                        permission.setRevokeOnUpgrade(false);
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE,
                                0, mUserHandle);
                    }

                    // Legacy apps do not know that they have to retry access to a
                    // resource due to changes in runtime permissions (app ops in this
                    // case). Therefore, we restart them on app op change, so they
                    // can pick up the change.
                    mActivityManager.killUid(uid, KILL_REASON_APP_OP_CHANGE);
                }
            }
        }

        return true;
    }

    public boolean revokeRuntimePermissions(boolean fixedByTheUser) {
        final boolean isSharedUser = mPackageInfo.sharedUserId != null;
        final int uid = mPackageInfo.applicationInfo.uid;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        for (Permission permission : mPermissions.values()) {
            if (mAppSupportsRuntimePermissions) {
                // Do not touch permissions fixed by the system.
                if (permission.isSystemFixed()) {
                    return false;
                }

                // Revoke the permission if needed.
                if (permission.isGranted()) {
                    permission.setGranted(false);
                    mPackageManager.revokeRuntimePermission(mPackageInfo.packageName,
                            permission.getName(), mUserHandle);
                }

                // Update the permission flags.
                if (fixedByTheUser) {
                    // Take a note that the user fixed the permission.
                    if (permission.isUserSet() || !permission.isUserFixed()) {
                        permission.setUserSet(false);
                        permission.setUserFixed(true);
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_USER_SET
                                        | PackageManager.FLAG_PERMISSION_USER_FIXED,
                                PackageManager.FLAG_PERMISSION_USER_FIXED,
                                mUserHandle);
                    }
                } else {
                    if (!permission.isUserSet()) {
                        permission.setUserSet(true);
                        // Take a note that the user already chose once.
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_USER_SET,
                                PackageManager.FLAG_PERMISSION_USER_SET,
                                mUserHandle);
                    }
                }

                // Disable the permission app op.
                if (permission.hasAppOp() && permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(false);
                    mAppOps.setMode(permission.getAppOp(), android.os.Process.myUid(),
                            mPackageInfo.packageName, AppOpsManager.MODE_IGNORED);
                }
            } else {
                // Legacy apps cannot have a non-granted permission but just in case.
                // Also if the permission has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.
                if (!permission.isGranted() || !permission.hasAppOp()) {
                    continue;
                }

                if (permission.isAppOpAllowed()) {
                    permission.setAppOpAllowed(false);
                    // It this is a shared user we want to enable the app op for all
                    // packages the the shared user to match the behavior of this
                    // shared user having a runtime permission.
                    if (isSharedUser) {
                        String[] packageNames = mPackageManager.getPackagesForUid(uid);
                        for (String packageName : packageNames) {
                            // Disable the app op.
                            mAppOps.setMode(permission.getAppOp(), uid,
                                    packageName, AppOpsManager.MODE_IGNORED);
                        }
                    } else {
                        // Disable the app op.
                        mAppOps.setMode(permission.getAppOp(), uid,
                                mPackageInfo.packageName, AppOpsManager.MODE_IGNORED);
                    }

                    // Mark that the permission should not be granted on upgrade
                    // when the app begins supporting runtime permissions.
                    if (!permission.shouldRevokeOnUpgrade()) {
                        permission.setRevokeOnUpgrade(true);
                        mPackageManager.updatePermissionFlags(permission.getName(),
                                mPackageInfo.packageName,
                                PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE,
                                PackageManager.FLAG_PERMISSION_REVOKE_ON_UPGRADE,
                                mUserHandle);
                    }

                    // Disabling an app op may put the app in a situation in which it
                    // has a handle to state it shouldn't have, so we have to kill the
                    // app. This matches the revoke runtime permission behavior.
                    mActivityManager.killUid(uid, KILL_REASON_APP_OP_CHANGE);
                }
            }
        }

        return true;
    }

    public List<Permission> getPermissions() {
        return new ArrayList<>(mPermissions.values());
    }

    public int getFlags() {
        int flags = 0;
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            flags |= permission.getFlags();
        }
        return flags;
    }

    public boolean isUserFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (!permission.isUserFixed()) {
                return false;
            }
        }
        return true;
    }

    public boolean isPolicyFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isPolicyFixed()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUserSet() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (!permission.isUserSet()) {
                return false;
            }
        }
        return true;
    }

    public boolean isSystemFixed() {
        final int permissionCount = mPermissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = mPermissions.valueAt(i);
            if (permission.isSystemFixed()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int compareTo(AppPermissionGroup another) {
        final int result = mLabel.toString().compareTo(another.mLabel.toString());
        if (result == 0) {
            // Unbadged before badged.
            return mPackageInfo.applicationInfo.uid
                    - another.mPackageInfo.applicationInfo.uid;
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        AppPermissionGroup other = (AppPermissionGroup) obj;

        if (mName == null) {
            if (other.mName != null) {
                return false;
            }
        } else if (!mName.equals(other.mName)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return mName != null ? mName.hashCode() : 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getClass().getSimpleName());
        builder.append("{name=").append(mName);
        if (!mPermissions.isEmpty()) {
            builder.append(", <has permissions>}");
        } else {
            builder.append('}');
        }
        return builder.toString();
    }

    private void addPermission(Permission permission) {
        mPermissions.put(permission.getName(), permission);
    }
}
