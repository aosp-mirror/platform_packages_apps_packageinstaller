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

package com.android.packageinstaller.permission.data;

import static com.android.packageinstaller.permission.utils.Utils.FLAGS_ALWAYS_USER_SENSITIVE;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.android.packageinstaller.AsyncTaskLiveData;
import com.android.packageinstaller.permission.utils.ArrayUtils;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.List;
import java.util.Set;

/**
 * Live data of the user sensitivity of all uids that belong to a given user
 *
 * <p>Data source: system server
 */
public class PerUserUidToSensitivityLiveData extends
        AsyncTaskLiveData<SparseArray<ArrayMap<String, Integer>>> {
    private static final SparseArray<PerUserUidToSensitivityLiveData> sInstances =
            new SparseArray<>();

    private final Context mContext;
    private final UserHandle mUser;

    /** Monitors changes to the packages for a user */
    private final BroadcastReceiver mPackageMonitor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadValue();
        }
    };

    /**
     * Get a (potentially shared) live data.
     *
     * @param user The user to get the data for
     * @param application The application context
     *
     * @return The live data
     */
    @MainThread
    public static PerUserUidToSensitivityLiveData get(@NonNull UserHandle user,
            @NonNull Application application) {
        PerUserUidToSensitivityLiveData instance = sInstances.get(user.getIdentifier());
        if (instance == null) {
            instance = new PerUserUidToSensitivityLiveData(user, application);
            sInstances.put(user.getIdentifier(), instance);
        }

        return instance;
    }

    private PerUserUidToSensitivityLiveData(@NonNull UserHandle user,
            @NonNull Application application) {
        mUser = user;

        try {
            mContext = application.createPackageContextAsUser(application.getPackageName(), 0,
                    user);
        } catch (PackageManager.NameNotFoundException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }

    @Override
    protected void onActive() {
        loadValue();
        mContext.registerReceiver(mPackageMonitor, new IntentFilter(Intent.ACTION_PACKAGE_CHANGED));
    }

    @Override
    protected void onInactive() {
        mContext.unregisterReceiver(mPackageMonitor);
    }

    @Override
    public SparseArray<ArrayMap<String, Integer>> loadValueInBackground() {
        PackageManager pm = mContext.getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        Set<String> platformPerms = Utils.getPlatformPermissions();
        ArraySet<String> pkgsWithLauncherIcon = Utils.getLauncherPackages(mContext);

        // uid -> permission -> flags
        SparseArray<ArrayMap<String, Integer>> uidsPermissions = new SparseArray<>();

        // Collect the flags and store it in 'uidsPermissions'
        int numPkgs = pkgs.size();
        for (int pkgNum = 0; pkgNum < numPkgs; pkgNum++) {
            PackageInfo pkg = pkgs.get(pkgNum);
            boolean pkgHasLauncherIcon = pkgsWithLauncherIcon.contains(pkg.packageName);
            boolean pkgIsSystemApp = (pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            // permission -> flags
            ArrayMap<String, Integer> uidPermissions = uidsPermissions.get(pkg.applicationInfo.uid);
            if (uidPermissions == null) {
                uidPermissions = new ArrayMap<>();
                uidsPermissions.put(pkg.applicationInfo.uid, uidPermissions);
            }

            for (String perm : platformPerms) {
                if (!ArrayUtils.contains(pkg.requestedPermissions, perm)) {
                    continue;
                }

                /*
                 * Permissions are considered user sensitive for a package, when
                 * - the package has a launcher icon, or
                 * - the permission is not pre-granted, or
                 * - the package is not a system app (i.e. not preinstalled)
                 *
                 * If two packages share a UID there can be two cases:
                 * - for well known UIDs: if the permission for any package is non-user sensitive,
                 *                        it is non-sensitive. I.e. prefer to hide
                 * - for non system UIDs: if the permission for any package is user sensitive, it is
                 *                        user sensitive. I.e. prefer to show
                 */
                Integer previousFlagsInt = uidPermissions.get(perm);
                int previousFlags;
                if (pkg.applicationInfo.uid < Process.FIRST_APPLICATION_UID) {
                    previousFlags = previousFlagsInt == null
                            ? FLAGS_ALWAYS_USER_SENSITIVE
                            : previousFlagsInt;
                } else {
                    previousFlags = previousFlagsInt == null ? 0 : previousFlagsInt;
                }

                int flags;
                if (pkgIsSystemApp && !pkgHasLauncherIcon) {
                    boolean permGrantedByDefault = (pm.getPermissionFlags(perm, pkg.packageName,
                            mUser) & PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT) != 0;

                    if (permGrantedByDefault) {
                        flags = 0;
                    } else {
                        flags = PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED;
                    }
                } else {
                    flags = FLAGS_ALWAYS_USER_SENSITIVE;
                }

                if (pkg.applicationInfo.uid < Process.FIRST_APPLICATION_UID) {
                    flags &= previousFlags;
                } else {
                    flags |= previousFlags;
                }

                uidPermissions.put(perm, flags);
            }
        }

        return uidsPermissions;
    }
}
