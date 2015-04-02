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

package com.android.packageinstaller.permission;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

public final class AppPermissions {
    private static final String LOG_TAG = "AppPermissions";

    private final ArrayMap<String, PermissionGroup> mGroups = new ArrayMap<>();

    private final Context mContext;

    private final PackageInfo mPackageInfo;

    private final String[] mFilterPermissions;

    private final CharSequence mAppLabel;

    public AppPermissions(Context context, PackageInfo packageInfo, String[] permissions) {
        mContext = context;
        mPackageInfo = packageInfo;
        mFilterPermissions = permissions;
        mAppLabel = packageInfo.applicationInfo.loadLabel(context.getPackageManager());
        loadPermissionGroups();
    }

    public void refresh() {
        loadPermissionGroups();
    }

    public CharSequence getAppLabel() {
        return mAppLabel;
    }

    public PermissionGroup getPermissionGroup(String name) {
        return mGroups.get(name);
    }

    public List<PermissionGroup> getPermissionGroups() {
        return new ArrayList<>(mGroups.values());
    }

    private void loadPermissionGroups() {
        mGroups.clear();
        if (mPackageInfo.requestedPermissions == null) {
            return;
        }

        final boolean appSupportsRuntimePermissions = mPackageInfo.applicationInfo.targetSdkVersion
                > Build.VERSION_CODES.LOLLIPOP_MR1;

        for (int i = 0; i < mPackageInfo.requestedPermissions.length; i++) {
            String requestedPerm = mPackageInfo.requestedPermissions[i];

            final PermissionInfo permInfo;
            try {
                permInfo = mContext.getPackageManager().getPermissionInfo(requestedPerm, 0);
            } catch (NameNotFoundException e) {
                Log.w(LOG_TAG, "Unknown permission: " + requestedPerm);
                continue;
            }

            String permName = permInfo.name;
            String groupName = permInfo.group != null ? permInfo.group : permName;

            PermissionGroup group = mGroups.get(groupName);
            if (group == null) {
                PermissionGroupInfo groupInfo = null;
                if (permInfo.group != null) {
                    try {
                        groupInfo = mContext.getPackageManager().getPermissionGroupInfo(
                                permInfo.group, 0);
                    } catch (NameNotFoundException e) {
                        Log.w(LOG_TAG, "Unknown group: " + permInfo.group);
                    }
                }

                CharSequence groupLabel = (groupInfo != null)
                        ? groupInfo.loadLabel(mContext.getPackageManager())
                        : permInfo.loadLabel(mContext.getPackageManager());

                if (groupLabel == null) {
                    Log.w(LOG_TAG, "Neither permission nor group have name."
                            + " Ignoring permission: " + permInfo.name);
                    continue;
                }

                final String iconPkg = (groupInfo != null)
                        ? groupInfo.packageName : permInfo.packageName;
                final int iconResId = (groupInfo != null) ? groupInfo.icon : permInfo.icon;

                group = new PermissionGroup(mContext, mPackageInfo.packageName,
                        groupName, groupLabel, iconPkg, iconResId);
                mGroups.put(groupName, group);
            }

            final boolean runtime = appSupportsRuntimePermissions
                    && permInfo.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS;
            final boolean granted = (mPackageInfo.requestedPermissionsFlags[i]
                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;

            Permission permission = new Permission(permName, runtime, granted);
            group.addPermission(permission);
        }

        if (ArrayUtils.isEmpty(mFilterPermissions)) {
            return;
        }

        final int groupCount = mGroups.size();
        for (int i = groupCount - 1; i >= 0; i--) {
            PermissionGroup group = mGroups.valueAt(i);
            boolean groupHasPermission = false;
            for (String filterPerm : mFilterPermissions) {
                if (group.mPermissions.containsKey(filterPerm)) {
                    groupHasPermission = true;
                    break;
                }
            }
            if (!groupHasPermission) {
                mGroups.removeAt(i);
            }
        }
    }

    public static final class PermissionGroup {
        private final Context mContext;
        private final String mPackageName;

        private final String mName;
        private final CharSequence mLabel;
        private final ArrayMap<String, Permission> mPermissions = new ArrayMap<>();
        private final String mIconPkg;
        private final int mIconResId;

        private boolean mHasRuntimePermissions;

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

        public PermissionGroup(Context context, String packageName,
                String name, CharSequence label, String iconPkg, int iconResId) {
            mPackageName = packageName;
            mContext = context;
            mName = name;
            mLabel = label;
            mIconPkg = iconPkg;
            mIconResId = iconResId;
        }

        public boolean hasRuntimePermissions() {
            return mHasRuntimePermissions;
        }

        public boolean areRuntimePermissionsGranted() {
            final int permissionCount = mPermissions.size();
            for (int i = 0; i < permissionCount; i++) {
                Permission permission = mPermissions.valueAt(i);
                if (permission.mRuntime && !permission.mGranted) {
                    return false;
                }
            }
            return true;
        }

        public boolean grantRuntimePermissions() {
            for (Permission permission : mPermissions.values()) {
                if (permission.mRuntime && !permission.mGranted) {
                    mContext.getPackageManager().grantPermission(mPackageName,
                            permission.mName, new UserHandle(mContext.getUserId()));
                    permission.mGranted = true;
                }
            }
            return true;
        }

        public boolean revokeRuntimePermissions() {
            for (Permission permission : mPermissions.values()) {
                if (permission.mRuntime && permission.mGranted) {
                    mContext.getPackageManager().revokePermission(mPackageName,
                            permission.mName, new UserHandle(mContext.getUserId()));
                    permission.mGranted = false;
                }
            }
            return true;
        }

        public List<Permission> getPermissions() {
            return new ArrayList<>(mPermissions.values());
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
            mPermissions.put(permission.mName, permission);
            if (permission.mRuntime) {
                mHasRuntimePermissions = true;
            }
        }
    }

    public static final class Permission {
        private final String mName;
        private final boolean mRuntime;
        private boolean mGranted;

        public Permission(String name, boolean runtime, boolean granted) {
            mName = name;
            mRuntime = runtime;
            mGranted = granted;
        }

        public String getName() {
            return mName;
        }

        public boolean isGranted() {
            return mGranted;
        }

        public void setGranted(boolean granted) {
            mGranted = granted;
        }
    }

    public static Drawable loadDrawable(PackageManager pm, String pkg, int resId) {
        try {
            return pm.getResourcesForApplication(pkg).getDrawable(resId, null);
        } catch (NotFoundException | NameNotFoundException e) {
            Log.d(LOG_TAG, "Couldn't get resource", e);
            return null;
        }
    }
}
