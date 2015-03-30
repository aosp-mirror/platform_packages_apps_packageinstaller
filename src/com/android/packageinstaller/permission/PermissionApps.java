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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;

import com.android.packageinstaller.permission.AppPermissions.Permission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PermissionApps {

    private static final String LOG_TAG = "PermissionGroup";

    private final Context mContext;
    private final String mGroupName;
    private final PackageManager mPm;
    private final Callback mCallback;

    private CharSequence mLabel;
    private Drawable mIcon;
    private ArrayList<PermissionApp> mAppPerms;
    // Map (pkg|uid) -> AppPermission
    private ArrayMap<String, PermissionApp> mAppLookup;

    public PermissionApps(Context context, String groupName, Callback callback) {
        mContext = context;
        mPm = mContext.getPackageManager();
        mGroupName = groupName;
        mCallback = callback;
        loadGroupInfo();
        new PermissionAppsLoader().execute();
    }

    public void refresh() {
        new PermissionAppsLoader().execute();
    }

    public Collection<PermissionApp> getApps() {
        return mAppPerms;
    }

    public PermissionApp getApp(String key) {
        return mAppLookup.get(key);
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    private void loadGroupInfo() {
        PackageItemInfo info = null;
        try {
            info = mPm.getPermissionGroupInfo(mGroupName, 0);
        } catch (NameNotFoundException e) {
            try {
                PermissionInfo permInfo = mPm.getPermissionInfo(mGroupName, 0);
                if (permInfo.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS) {
                    Log.w(LOG_TAG, mGroupName + " is not a runtime permission");
                    return;
                }
                info = permInfo;
            } catch (NameNotFoundException reallyNotFound) {
                Log.w(LOG_TAG, "Can't find permission: " + mGroupName, reallyNotFound);
                return;
            }
        }
        mLabel = info.loadLabel(mPm);
        mIcon = info.loadUnbadgedIcon(mPm);
        LightingColorFilter filter = new LightingColorFilter(0, 0xffffffff);
        mIcon.setColorFilter(filter);
    }

    public static class PermissionApp implements Comparable<PermissionApp> {
        private final Context mContext;
        private final String mLabel;
        private final Drawable mIcon;
        private final String mPkg;
        private final int mUid;
        private final List<Permission> mPermissions = new ArrayList<>();
        private boolean mHasPermission;

        public PermissionApp(Context context, String pkg, int uid, String label,
                Drawable icon) {
            mContext = context;
            mPkg = pkg;
            mUid = uid;
            mLabel = label;
            mIcon = icon;
        }

        public String getKey() {
            return Integer.toString(mUid);
        }

        public String getLabel() {
            return mLabel;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public boolean hasRuntimePermissions() {
            return mHasPermission;
        }

        /**
         * Note: This class only expects to have runtime permissions added.
         */
        public void addPermission(Permission permission) {
            mPermissions.add(permission);
            if (permission.isGranted()) {
                mHasPermission = true;
            }
        }

        public boolean grantRuntimePermissions() {
            for (Permission permission : mPermissions) {
                if (!permission.isGranted()) {
                    mContext.getPackageManager().grantPermission(mPkg,
                            permission.getName(), new UserHandle(UserHandle.getUserId(mUid)));
                    permission.setGranted(true);
                }
            }
            return true;
        }

        public boolean revokeRuntimePermissions() {
            for (Permission permission : mPermissions) {
                if (permission.isGranted()) {
                    mContext.getPackageManager().revokePermission(mPkg,
                            permission.getName(), new UserHandle(UserHandle.getUserId(mUid)));
                    permission.setGranted(false);
                }
            }
            return true;
        }

        @Override
        public int compareTo(PermissionApp another) {
            int result = mLabel.compareTo(another.mLabel);
            if (result == 0) {
                // Unbadged before badged.
                return mUid - another.mUid;
            }
            return result;
        }

    }

    private class PermissionAppsLoader extends AsyncTask<Void, Void, ArrayList<PermissionApp>> {

        @Override
        protected ArrayList<PermissionApp> doInBackground(Void... params) {
            ArrayList<String> permStrs = new ArrayList<>();
            try {
                List<PermissionInfo> permissions = mPm.queryPermissionsByGroup(mGroupName, 0);
                for (PermissionInfo info : permissions) {
                    if (info.protectionLevel != PermissionInfo.PROTECTION_DANGEROUS) {
                        continue;
                    }
                    permStrs.add(info.name);
                }
            } catch (NameNotFoundException e) {
                permStrs.add(mGroupName);
            }
            ArrayList<PermissionApp> appPerms = new ArrayList<>();
            for (UserHandle user : UserManager.get(mContext).getUserProfiles()) {
                List<PackageInfo> apps = mPm.getInstalledPackages(
                        PackageManager.GET_PERMISSIONS, user.getIdentifier());
                final int N = apps.size();
                for (int i = 0; i < N; i++) {
                    PackageInfo app = apps.get(i);
                    if (app.applicationInfo.targetSdkVersion
                            <= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        // Only care about apps that support runtime permissions here.
                        continue;
                    }
                    if (app.requestedPermissions == null) {
                        continue;
                    }
                    PermissionApp appPermission = new PermissionApp(mContext,
                            app.packageName, app.applicationInfo.uid,
                            app.applicationInfo.loadLabel(mPm).toString(),
                            getBadgedIcon(app.applicationInfo));
                    for (int j = 0; j < app.requestedPermissions.length; j++) {
                        if (permStrs.contains(app.requestedPermissions[j])) {
                            boolean granted = (app.requestedPermissionsFlags[j]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                            appPermission.addPermission(new Permission(
                                    app.requestedPermissions[j], true, granted));
                        }
                    }
                    if (appPermission.mPermissions.size() != 0) {
                        appPerms.add(appPermission);
                    }
                }
            }
            Collections.sort(appPerms);
            return appPerms;
        }

        private Drawable getBadgedIcon(ApplicationInfo appInfo) {
            Drawable unbadged = appInfo.loadUnbadgedIcon(mPm);
            return mPm.getUserBadgedIcon(unbadged,
                    new UserHandle(UserHandle.getUserId(appInfo.uid)));
        }

        @Override
        protected void onPostExecute(ArrayList<PermissionApp> result) {
            mAppLookup = new ArrayMap<>();
            for (PermissionApp app : result) {
                mAppLookup.put(app.getKey(), app);
            }
            mAppPerms = result;
            mCallback.onPermissionsLoaded();
        }
    }

    public interface Callback {
        void onPermissionsLoaded();
    }

}
