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

package com.android.packageinstaller.role.model;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * STOPSHIP: Temporary workaround: Allow any app to access SMS
 */
public class TemporarySmsAccessRoleBehavior implements RoleBehavior {

    /**
     * If the package requesting any SMS permission?
     *
     * @param pkg The package that might request the permission
     *
     * @return {@code true} iff the package requests any SMS permission
     */
    private boolean isRequestingSMSPermission(@NonNull PackageInfo pkg) {
        String[] requestedPermsArr = pkg.requestedPermissions;
        if (requestedPermsArr == null) {
            return false;
        }

        ArraySet<String> requestedPerms = new ArraySet<>(requestedPermsArr.length);
        Collections.addAll(requestedPerms, requestedPermsArr);

        return requestedPerms.contains(Manifest.permission.SEND_SMS)
                || requestedPerms.contains(Manifest.permission.RECEIVE_SMS)
                || requestedPerms.contains(Manifest.permission.READ_SMS)
                || requestedPerms.contains(Manifest.permission.RECEIVE_WAP_PUSH)
                || requestedPerms.contains(Manifest.permission.RECEIVE_MMS)
                || requestedPerms.contains(Manifest.permission.READ_CELL_BROADCASTS);
    }

    @Nullable
    @Override
    public List<String> getQualifyingPackagesAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        List<PackageInfo> pkgs = context.getPackageManager().getInstalledPackagesAsUser(
                PackageManager.GET_PERMISSIONS, user.getIdentifier());

        ArrayList<String> qualifyingPkgs = new ArrayList<>();
        int numPkgs = pkgs.size();
        for (int i = 0; i < numPkgs; i++) {
            if (isRequestingSMSPermission(pkgs.get(i))) {
                qualifyingPkgs.add(pkgs.get(i).packageName);
            }
        }

        return qualifyingPkgs;
    }

    @Nullable
    @Override
    public Boolean isPackageQualified(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        PackageInfo pkg;
        try {
            pkg = context.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }

        return isRequestingSMSPermission(pkg);
    }
}
