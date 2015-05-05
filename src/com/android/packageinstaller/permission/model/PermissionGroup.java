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

import java.util.ArrayList;
import java.util.List;

public final class PermissionGroup implements Comparable<PermissionGroup> {
    private static final String PLATFORM_PACKAGE_NAME = "android";

    private static final String KILL_REASON_APP_OP_CHANGE = "Permission related app op changed";

    private final Context mContext;
    private final AppOpsManager mAppOps;
    private final ActivityManager mActivityManager;

    private final PackageInfo mPackageInfo;
    private final String mName;
    private final CharSequence mLabel;
    private final ArrayMap<String, Permission> mPermissions = new ArrayMap<>();
    private final String mIconPkg;
    private final int mIconResId;

    private final boolean mAppSupportsRuntimePermissions;

    public static PermissionGroup create(Context context, PackageInfo packageInfo,
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

    public static PermissionGroup create(Context context, PackageInfo packageInfo,
            PackageItemInfo groupInfo, List<PermissionInfo> permissionInfos) {

        PermissionGroup group = new PermissionGroup(context, packageInfo, groupInfo.name,
                groupInfo.loadLabel(context.getPackageManager()), groupInfo.packageName,
                groupInfo.icon);

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

            final int appOp;
            final boolean appOpAllowed;

            if (group.mAppSupportsRuntimePermissions) {
                appOp = AppOpsManager.OP_NONE;
                appOpAllowed = false;
            } else {
                appOp = PLATFORM_PACKAGE_NAME.equals(requestedPermissionInfo.packageName)
                        ? AppOpsManager.permissionToOpCode(requestedPermissionInfo.name)
                        : AppOpsManager.OP_NONE;
                appOpAllowed = appOp != AppOpsManager.OP_NONE
                        && context.getSystemService(AppOpsManager.class).checkOp(appOp,
                        packageInfo.applicationInfo.uid, packageInfo.packageName)
                        == AppOpsManager.MODE_ALLOWED;
            }

            Permission permission = new Permission(requestedPermission, granted,
                    appOp, appOpAllowed);
            group.addPermission(permission);
        }

        return group;
    }

    private PermissionGroup(Context context, PackageInfo packageInfo, String name,
            CharSequence label, String iconPkg, int iconResId) {
        mContext = context;
        mPackageInfo = packageInfo;
        mAppSupportsRuntimePermissions = packageInfo.applicationInfo
                .targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1;
        mAppOps = context.getSystemService(AppOpsManager.class);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mName = name;
        mLabel = label;
        mIconPkg = iconPkg;
        mIconResId = iconResId;
    }

    public PackageInfo getApp() {
        return mPackageInfo;
    }

    public String getName() {
        return mName;
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

    public boolean grantRuntimePermissions() {
        final boolean isSharedUser = mPackageInfo.sharedUserId != null;
        final int uid = mPackageInfo.applicationInfo.uid;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        for (Permission permission : mPermissions.values()) {
            if (mAppSupportsRuntimePermissions) {
                if (!permission.isGranted()) {
                    mContext.getPackageManager().grantPermission(mPackageInfo.packageName,
                            permission.getName(), new UserHandle(mContext.getUserId()));
                    permission.setGranted(true);
                }
            } else {
                // Legacy apps cannot have a not granted permission but just in case.
                // Also if the permissions has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.
                if (!permission.isGranted() || permission.getAppOp() == AppOpsManager.OP_NONE) {
                    continue;
                }

                if (!permission.isAppOpAllowed()) {
                    // It this is a shared user we want to enable the app op for all
                    // packages in the shared user to match the behavior of this
                    // shared user having a runtime permission.
                    if (isSharedUser) {
                        String[] packageNames = mContext.getPackageManager().getPackagesForUid(uid);
                        for (String packageName : packageNames) {
                            mAppOps.setMode(permission.getAppOp(), uid, packageName,
                                    AppOpsManager.MODE_ALLOWED);
                        }
                    } else {
                        mAppOps.setMode(permission.getAppOp(), uid, mPackageInfo.packageName,
                                AppOpsManager.MODE_ALLOWED);
                    }

                    // Legacy apps do not know that they have to retry access to a
                    // resource due to changes in runtime permissions (app ops in this
                    // case). Therefore, we restart them on app op change, so they
                    // can pick up the change.
                    mActivityManager.killUid(uid, KILL_REASON_APP_OP_CHANGE);

                    permission.setAppOpAllowed(true);
                }
            }
        }

        return true;
    }

    public boolean revokeRuntimePermissions() {
        final boolean isSharedUser = mPackageInfo.sharedUserId != null;
        final int uid = mPackageInfo.applicationInfo.uid;

        // We toggle permissions only to apps that support runtime
        // permissions, otherwise we toggle the app op corresponding
        // to the permission if the permission is granted to the app.
        for (Permission permission : mPermissions.values()) {
            if (mAppSupportsRuntimePermissions) {
                if (permission.isGranted()) {
                    mContext.getPackageManager().revokePermission(mPackageInfo.packageName,
                            permission.getName(), new UserHandle(mContext.getUserId()));
                    permission.setGranted(false);
                }
            } else {
                // Legacy apps cannot have a non-granted permission but just in case.
                // Also if the permission has no corresponding app op, then it is a
                // third-party one and we do not offer toggling of such permissions.
                if (!permission.isGranted() || permission.getAppOp() == AppOpsManager.OP_NONE) {
                    continue;
                }

                if (permission.isAppOpAllowed()) {
                    // It this is a shared user we want to enable the app op for all
                    // packages the the shared user to match the behavior of this
                    // shared user having a runtime permission.
                    if (isSharedUser) {
                        String[] packageNames = mContext.getPackageManager().getPackagesForUid(uid);
                        for (String packageName : packageNames) {
                            mAppOps.setMode(permission.getAppOp(), uid,
                                    packageName, AppOpsManager.MODE_IGNORED);
                        }
                    } else {
                        mAppOps.setMode(permission.getAppOp(), uid,
                                mPackageInfo.packageName, AppOpsManager.MODE_IGNORED);
                    }

                    // Disabling an app op may put the app in a situation in which it
                    // has a handle to state it shouldn't have, so we have to kill the
                    // app. This matches the revoke runtime permission behavior.
                    mActivityManager.killUid(uid, KILL_REASON_APP_OP_CHANGE);

                    permission.setAppOpAllowed(false);
                }
            }
        }

        return true;
    }

    public List<Permission> getPermissions() {
        return new ArrayList<>(mPermissions.values());
    }

    @Override
    public int compareTo(PermissionGroup another) {
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

        PermissionGroup other = (PermissionGroup) obj;

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

    void addPermission(Permission permission) {
        mPermissions.put(permission.getName(), permission);
    }
}

