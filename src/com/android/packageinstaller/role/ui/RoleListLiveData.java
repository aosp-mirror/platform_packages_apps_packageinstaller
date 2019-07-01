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
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.android.packageinstaller.AsyncTaskLiveData;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link LiveData} for a list of roles.
 */
public class RoleListLiveData extends AsyncTaskLiveData<List<RoleItem>>
        implements OnRoleHoldersChangedListener {

    private static final String LOG_TAG = RoleListLiveData.class.getSimpleName();

    private final boolean mExclusive;
    @NonNull
    private final UserHandle mUser;
    @NonNull
    private final Context mContext;

    public RoleListLiveData(boolean exclusive, @NonNull UserHandle user, @NonNull Context context) {
        mExclusive = exclusive;
        mUser = user;
        mContext = context;
    }

    @Override
    protected void onActive() {
        loadValue();

        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        roleManager.addOnRoleHoldersChangedListenerAsUser(mContext.getMainExecutor(), this,
                mUser);
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

    @NonNull
    @Override
    @WorkerThread
    protected List<RoleItem> loadValueInBackground() {
        ArrayMap<String, Role> roles = Roles.get(mContext);

        List<RoleItem> roleItems = new ArrayList<>();
        RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        int rolesSize = roles.size();
        for (int rolesIndex = 0; rolesIndex < rolesSize; rolesIndex++) {
            Role role = roles.valueAt(rolesIndex);

            if (role.isExclusive() != mExclusive) {
                continue;
            }

            if (!role.isAvailableAsUser(mUser, mContext)) {
                continue;
            }

            if (!role.isVisibleAsUser(mUser, mContext)) {
                continue;
            }

            if (mExclusive && role.getQualifyingPackagesAsUser(mUser, mContext).isEmpty()) {
                continue;
            }

            List<ApplicationInfo> holderApplicationInfos = new ArrayList<>();
            List<String> holderPackageNames = roleManager.getRoleHoldersAsUser(role.getName(),
                    mUser);
            int holderPackageNamesSize = holderPackageNames.size();
            for (int holderPackageNamesIndex = 0; holderPackageNamesIndex < holderPackageNamesSize;
                    holderPackageNamesIndex++) {
                String holderPackageName = holderPackageNames.get(holderPackageNamesIndex);

                ApplicationInfo holderApplicationInfo = PackageUtils.getApplicationInfoAsUser(
                        holderPackageName, mUser, mContext);
                if (holderApplicationInfo == null) {
                    Log.w(LOG_TAG, "Cannot get ApplicationInfo for application, package name: "
                            + holderPackageName + ", user id: " + mUser.getIdentifier());
                    continue;
                }
                holderApplicationInfos.add(holderApplicationInfo);
            }

            roleItems.add(new RoleItem(role, holderApplicationInfos));
        }

        return roleItems;
    }
}
