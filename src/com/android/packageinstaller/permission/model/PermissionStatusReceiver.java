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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.utils.Utils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;

public class PermissionStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int[] counts = new int[3];
        ArrayList<CharSequence> grantedGroups = new ArrayList<>();
        boolean succeeded = false;

        boolean isForPackage = intent.hasExtra(Intent.EXTRA_PACKAGE_NAME);

        Intent responseIntent = new Intent(intent.getStringExtra(
                Intent.EXTRA_GET_PERMISSIONS_RESPONSE_INTENT));
        responseIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);


        if (isForPackage) {
            String pkg = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            succeeded = getPermissionsCount(context, pkg, counts, grantedGroups);
        } else {
            succeeded = getAppsWithPermissionsCount(context, counts);
        }
        if (succeeded) {
            responseIntent.putExtra(Intent.EXTRA_GET_PERMISSIONS_COUNT_RESULT, counts);

            if (isForPackage) {
                responseIntent.putExtra(Intent.EXTRA_GET_PERMISSIONS_GROUP_LIST_RESULT,
                        grantedGroups.toArray(new CharSequence[grantedGroups.size()]));
            }
        }

        context.sendBroadcast(responseIntent);
    }

    public boolean getPermissionsCount(Context context, String pkg, int[] counts,
            ArrayList<CharSequence> grantedGroups) {
        try {
            PackageInfo packageInfo =
                    context.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            AppPermissions appPermissions =
                    new AppPermissions(context, packageInfo, null, false, null);
            int grantedCount = 0;
            int totalCount = 0;
            int additionalCount = 0;

            for (AppPermissionGroup group : appPermissions.getPermissionGroups()) {
                if (Utils.shouldShowPermission(group, false)) {
                    totalCount++;
                    if (group.areRuntimePermissionsGranted()) {
                        grantedCount++;

                        if (Utils.OS_PKG.equals(group.getDeclaringPackage())) {
                            grantedGroups.add(group.getLabel());
                        } else {
                            additionalCount++;
                        }
                    }
                }
            }

            // Sort
            Collator coll = Collator.getInstance();
            coll.setStrength(Collator.PRIMARY);
            Collections.sort(grantedGroups, coll);

            // Set results
            counts[0] = grantedCount;
            counts[1] = totalCount;
            counts[2] = additionalCount;

            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public boolean getAppsWithPermissionsCount(Context context, int[] counts) {
        ArraySet<String> launcherPkgs = Utils.getLauncherPackages(context);
        // Indexed by uid.
        SparseArray<Boolean> grantedApps = new SparseArray<>();
        SparseArray<Boolean> allApps = new SparseArray<>();
        for (String group : Utils.MODERN_PERMISSION_GROUPS) {
            PermissionApps permissionApps = new PermissionApps(context,
                    group, null);
            permissionApps.loadNowWithoutUi();
            for (PermissionApp app : permissionApps.getApps()) {
                int uid = app.getUid();
                if (Utils.isSystem(app, launcherPkgs)) {
                    // We default to not showing system apps, so hide them from count.
                    continue;
                }
                if (app.areRuntimePermissionsGranted()) {
                    grantedApps.put(uid, true);
                }
                allApps.put(uid, true);
            }
        }
        counts[0] = grantedApps.size();
        counts[1] = allApps.size();
        return true;
    }
}
