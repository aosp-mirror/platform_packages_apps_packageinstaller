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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppPermissions {
    private final ArrayMap<String, PermissionGroup> mGroups = new ArrayMap<>();

    private final Context mContext;

    private final String[] mFilterPermissions;

    private final CharSequence mAppLabel;

    private final Runnable mOnErrorCallback;

    private PackageInfo mPackageInfo;

    public AppPermissions(Context context, PackageInfo packageInfo, String[] permissions,
            Runnable onErrorCallback) {
        mContext = context;
        mPackageInfo = packageInfo;
        mFilterPermissions = permissions;
        mAppLabel = packageInfo.applicationInfo.loadLabel(context.getPackageManager());
        mOnErrorCallback = onErrorCallback;
        loadPermissionGroups();
    }

    public PackageInfo getPackageInfo() {
        return mPackageInfo;
    }

    public void refresh() {
        loadPackageInfo();
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


    private void loadPackageInfo() {
        try {
            mPackageInfo = mContext.getPackageManager().getPackageInfo(
                    mPackageInfo.packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            if (mOnErrorCallback != null) {
                mOnErrorCallback.run();
            }
        }
    }

    private void loadPermissionGroups() {
        List<PermissionGroup> groups = new ArrayList<>();

        if (mPackageInfo.requestedPermissions == null) {
            return;
        }

        for (int i = 0; i < mPackageInfo.requestedPermissions.length; i++) {
            String requestedPerm = mPackageInfo.requestedPermissions[i];

            if (hasGroupForPermission(requestedPerm, groups)) {
                continue;
            }

            PermissionGroup group = PermissionGroup.create(mContext,
                    mPackageInfo, requestedPerm);
            if (group == null) {
                continue;
            }

            groups.add(group);
        }

        if (!ArrayUtils.isEmpty(mFilterPermissions)) {
            final int groupCount = groups.size();
            for (int i = groupCount - 1; i >= 0; i--) {
                PermissionGroup group = groups.get(i);
                boolean groupHasPermission = false;
                for (String filterPerm : mFilterPermissions) {
                    if (group.hasPermission(filterPerm)) {
                        groupHasPermission = true;
                        break;
                    }
                }
                if (!groupHasPermission) {
                    groups.remove(i);
                }
            }
        }

        Collections.sort(groups);

        mGroups.clear();
        for (PermissionGroup group : groups) {
            mGroups.put(group.getName(), group);
        }
    }

    private static boolean hasGroupForPermission(String permission,
            List<PermissionGroup> groups) {
        for (PermissionGroup group : groups) {
            if (group.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
}
