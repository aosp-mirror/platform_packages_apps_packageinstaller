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

import static android.content.pm.PackageItemInfo.SAFE_LABEL_FLAG_FIRST_LINE;
import static android.content.pm.PackageItemInfo.SAFE_LABEL_FLAG_TRIM;

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * All {@link PermissionGroup permission groups} defined by any app.
 */
public final class PermissionGroups implements LoaderCallbacks<List<PermissionGroup>> {
    private final ArrayList<PermissionGroup> mGroups = new ArrayList<>();
    private final Context mContext;
    private final PermissionsGroupsChangeCallback mCallback;

    public interface PermissionsGroupsChangeCallback {
        public void onPermissionGroupsChanged();
    }

    public PermissionGroups(Context context, LoaderManager loaderManager,
            PermissionsGroupsChangeCallback callback) {
        mContext = context;
        mCallback = callback;

        // Don't update immediately as otherwise we can get a callback before this object is
        // initialized.
        (new Handler()).post(() -> loaderManager.initLoader(0, null, this));
    }

    @Override
    public Loader<List<PermissionGroup>> onCreateLoader(int id, Bundle args) {
        return new PermissionsLoader(mContext);
    }

    @Override
    public void onLoadFinished(Loader<List<PermissionGroup>> loader,
            List<PermissionGroup> groups) {
        if (mGroups.equals(groups)) {
            return;
        }
        mGroups.clear();
        mGroups.addAll(groups);
        mCallback.onPermissionGroupsChanged();
    }

    @Override
    public void onLoaderReset(Loader<List<PermissionGroup>> loader) {
        mGroups.clear();
        mCallback.onPermissionGroupsChanged();
    }

    public List<PermissionGroup> getGroups() {
        return mGroups;
    }

    public PermissionGroup getGroup(String name) {
        for (PermissionGroup group : mGroups) {
            if (group.getName().equals(name)) {
                return group;
            }
        }
        return null;
    }

    private static @NonNull CharSequence loadItemInfoLabel(@NonNull Context context,
            @NonNull PackageItemInfo itemInfo) {
        CharSequence label = itemInfo.loadSafeLabel(context.getPackageManager(), 0,
                SAFE_LABEL_FLAG_FIRST_LINE | SAFE_LABEL_FLAG_TRIM);
        if (label == null) {
            label = itemInfo.name;
        }
        return label;
    }

    private static @NonNull Drawable loadItemInfoIcon(@NonNull Context context,
            @NonNull PackageItemInfo itemInfo) {
        Drawable icon = null;
        if (itemInfo.icon > 0) {
            icon = Utils.loadDrawable(context.getPackageManager(),
                    itemInfo.packageName, itemInfo.icon);
        }
        if (icon == null) {
            icon = context.getDrawable(R.drawable.ic_perm_device_info);
        }
        return icon;
    }

    /**
     * Return all permission groups in the system.
     *
     * @param context Context to use
     * @param isCanceled callback checked if the group resolution should be aborted
     *
     * @return the list of all groups int the system
     */
    public static @NonNull List<PermissionGroup> getAllPermissionGroups(@NonNull Context context,
            @Nullable Supplier<Boolean> isCanceled) {
        ArraySet<String> launcherPkgs = Utils.getLauncherPackages(context);
        PermissionApps.PmCache pmCache = new PermissionApps.PmCache(
                context.getPackageManager());

        List<PermissionGroup> groups = new ArrayList<>();
        Set<String> seenPermissions = new ArraySet<>();

        PackageManager packageManager = context.getPackageManager();
        List<PermissionGroupInfo> groupInfos = packageManager.getAllPermissionGroups(0);

        for (PermissionGroupInfo groupInfo : groupInfos) {
            // Mare sure we respond to cancellation.
            if (isCanceled != null && isCanceled.get()) {
                return Collections.emptyList();
            }

            // Get the permissions in this group.
            final List<PermissionInfo> groupPermissions;
            try {
                groupPermissions = packageManager.queryPermissionsByGroup(groupInfo.name, 0);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }

            boolean hasRuntimePermissions = false;

            // Cache seen permissions and see if group has runtime permissions.
            for (PermissionInfo groupPermission : groupPermissions) {
                seenPermissions.add(groupPermission.name);
                if ((groupPermission.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                        == PermissionInfo.PROTECTION_DANGEROUS
                        && (groupPermission.flags & PermissionInfo.FLAG_INSTALLED) != 0
                        && (groupPermission.flags & PermissionInfo.FLAG_REMOVED) == 0) {
                    hasRuntimePermissions = true;
                }
            }

            // No runtime permissions - not interesting for us.
            if (!hasRuntimePermissions) {
                continue;
            }

            CharSequence label = loadItemInfoLabel(context, groupInfo);
            Drawable icon = loadItemInfoIcon(context, groupInfo);

            PermissionApps permApps = new PermissionApps(context, groupInfo.name, null,
                    pmCache);
            permApps.refreshSync();

            // Create the group and add to the list.
            PermissionGroup group = new PermissionGroup(groupInfo.name,
                    groupInfo.packageName, label, icon, permApps.getTotalCount(launcherPkgs),
                    permApps.getGrantedCount(launcherPkgs));
            groups.add(group);
        }


        // Make sure we add groups for lone runtime permissions.
        List<PackageInfo> installedPackages = context.getPackageManager()
                .getInstalledPackages(PackageManager.GET_PERMISSIONS);


        // We will filter out permissions that no package requests.
        Set<String> requestedPermissions = new ArraySet<>();
        for (PackageInfo installedPackage : installedPackages) {
            if (installedPackage.requestedPermissions == null) {
                continue;
            }
            for (String requestedPermission : installedPackage.requestedPermissions) {
                requestedPermissions.add(requestedPermission);
            }
        }

        for (PackageInfo installedPackage : installedPackages) {
            if (installedPackage.permissions == null) {
                continue;
            }

            for (PermissionInfo permissionInfo : installedPackage.permissions) {
                // If we have handled this permission, no more work to do.
                if (!seenPermissions.add(permissionInfo.name)) {
                    continue;
                }

                // We care only about installed runtime permissions.
                if ((permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                        != PermissionInfo.PROTECTION_DANGEROUS
                        || (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) == 0) {
                    continue;
                }

                // If no app uses this permission,
                if (!requestedPermissions.contains(permissionInfo.name)) {
                    continue;
                }

                CharSequence label = loadItemInfoLabel(context, permissionInfo);
                Drawable icon = loadItemInfoIcon(context, permissionInfo);

                PermissionApps permApps = new PermissionApps(context, permissionInfo.name,
                        null, pmCache);
                permApps.refreshSync();

                // Create the group and add to the list.
                PermissionGroup group = new PermissionGroup(permissionInfo.name,
                        permissionInfo.packageName, label, icon,
                        permApps.getTotalCount(launcherPkgs),
                        permApps.getGrantedCount(launcherPkgs));
                groups.add(group);
            }
        }

        Collections.sort(groups);
        return groups;
    }

    private static final class PermissionsLoader extends AsyncTaskLoader<List<PermissionGroup>>
            implements PackageManager.OnPermissionsChangedListener {

        public PermissionsLoader(Context context) {
            super(context);
        }

        @Override
        protected void onStartLoading() {
            getContext().getPackageManager().addOnPermissionsChangeListener(this);
            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            getContext().getPackageManager().removeOnPermissionsChangeListener(this);
        }

        @Override
        public List<PermissionGroup> loadInBackground() {
            return getAllPermissionGroups(getContext(), this::isLoadInBackgroundCanceled);
        }

        @Override
        public void onPermissionsChanged(int uid) {
            forceLoad();
        }
    }
}
