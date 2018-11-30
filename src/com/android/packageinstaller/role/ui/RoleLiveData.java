/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller.role.ui;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link LiveData} for a role.
 */
public class RoleLiveData extends AsyncTaskLiveData<RoleInfo> {

    private static final String LOG_TAG = RoleLiveData.class.getSimpleName();

    private final Role mRole;
    private final Context mContext;

    public RoleLiveData(@NonNull Role role, @NonNull Context context) {
        mRole = role;
        mContext = context;

        loadValue();
    }

    @Override
    @WorkerThread
    protected RoleInfo loadValueInBackground() {
        List<String> qualifyingPackageNames = mRole.getQualifyingPackages(mContext);
        List<ApplicationInfo> qualifyingApplicationInfos = new ArrayList<>();
        int qualifyingPackageNamesSize = qualifyingPackageNames.size();
        for (int i = 0; i < qualifyingPackageNamesSize; i++) {
            String qualifyingPackageName = qualifyingPackageNames.get(i);

            ApplicationInfo qualifyingApplicationInfo = PackageUtils.getApplicationInfo(
                    qualifyingPackageName, mContext);
            if (qualifyingApplicationInfo == null) {
                Log.w(LOG_TAG, "Cannot get ApplicationInfo for application, skipping: "
                        + qualifyingPackageName);
                continue;
            }
            qualifyingApplicationInfos.add(qualifyingApplicationInfo);
        }

        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        List<String> holderPackageNames = roleManager.getRoleHolders(mRole.getName());

        return new RoleInfo(qualifyingApplicationInfos, holderPackageNames);
    }
}
