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
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.List;

/**
 * {@code Activity} for a role request.
 */
public class RequestRoleActivity extends FragmentActivity {

    private static final String LOG_TAG = RequestRoleActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        String roleName = getIntent().getStringExtra(RoleManager.EXTRA_REQUEST_ROLE_NAME);
        if (TextUtils.isEmpty(roleName)) {
            Log.w(LOG_TAG, "Role name cannot be null or empty: " + roleName);
            finish();
            return;
        }

        // TODO: Allow proxy package?
        String packageName = getCallingPackage();
        if (TextUtils.isEmpty(packageName)) {
            Log.w(LOG_TAG, "Package name cannot be null or empty: " + packageName);
            finish();
            return;
        }

        // Perform checks here so that we have a chance to finish without being visible to user.
        Role role = Roles.getRoles(this).get(roleName);
        if (role == null) {
            Log.w(LOG_TAG, "Unknown role: " + roleName);
            finish();
            return;
        }

        if (PackageUtils.getApplicationInfo(packageName, this) == null) {
            Log.w(LOG_TAG, "Unknown application: " + packageName);
            finish();
            return;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        List<String> currentPackageNames = roleManager.getRoleHolders(roleName);
        if (currentPackageNames.contains(packageName)) {
            Log.i(LOG_TAG, "Application is already a role holder, role: " + roleName + ", package: "
                    + packageName);
            setResult(RESULT_OK);
            finish();
            return;
        }

        if (!role.isPackageQualified(packageName, this)) {
            Log.w(LOG_TAG, "Application doesn't qualify for role, role: " + roleName + ", package: "
                    + packageName);
            finish();
            return;
        }

        // TODO: STOPSHIP: Handle other form factors.
        if (savedInstanceState == null) {
            RequestRoleFragment fragment = RequestRoleFragment.newInstance(
                    roleName, packageName);
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        // Don't allow back.
        // TODO: STOPSHIP: Or do we allow?
    }
}
