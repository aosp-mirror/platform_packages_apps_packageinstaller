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

import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.android.packageinstaller.AsyncTaskLiveData;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link LiveData} for a role.
 */
public class RoleLiveData extends AsyncTaskLiveData<List<Pair<ApplicationInfo, Boolean>>>
        implements OnRoleHoldersChangedListener {

    private static final String LOG_TAG = RoleLiveData.class.getSimpleName();

    @NonNull
    private final Role mRole;
    @NonNull
    private final UserHandle mUser;
    @NonNull
    private final Context mContext;

    public RoleLiveData(@NonNull Role role, @NonNull UserHandle user, @NonNull Context context) {
        mRole = role;
        mUser = user;
        mContext = context;
    }

    @Override
    protected void onActive() {
        loadValue();

        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        roleManager.addOnRoleHoldersChangedListenerAsUser(mContext.getMainExecutor(), this, mUser);
    }

    @Override
    protected void onInactive() {
        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        roleManager.removeOnRoleHoldersChangedListenerAsUser(this, mUser);
    }

    @Override
    public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
        loadValue();
    }

    @Override
    @WorkerThread
    protected List<Pair<ApplicationInfo, Boolean>> loadValueInBackground() {
        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        List<String> holderPackageNames = roleManager.getRoleHoldersAsUser(mRole.getName(), mUser);

        List<String> qualifyingPackageNames = mRole.getQualifyingPackagesAsUser(mUser, mContext);
        List<Pair<ApplicationInfo, Boolean>> qualifyingApplications = new ArrayList<>();
        int qualifyingPackageNamesSize = qualifyingPackageNames.size();
        for (int i = 0; i < qualifyingPackageNamesSize; i++) {
            String qualifyingPackageName = qualifyingPackageNames.get(i);

            ApplicationInfo qualifyingApplicationInfo = PackageUtils.getApplicationInfoAsUser(
                    qualifyingPackageName, mUser, mContext);
            if (qualifyingApplicationInfo == null) {
                Log.w(LOG_TAG, "Cannot get ApplicationInfo for application, skipping: "
                        + qualifyingPackageName);
                continue;
            }
            if (!mRole.isApplicationVisibleAsUser(qualifyingApplicationInfo, mUser, mContext)) {
                continue;
            }
            boolean isHolderApplication = holderPackageNames.contains(qualifyingPackageName);
            qualifyingApplications.add(new Pair<>(qualifyingApplicationInfo, isHolderApplication));
        }

        return qualifyingApplications;
    }
}
