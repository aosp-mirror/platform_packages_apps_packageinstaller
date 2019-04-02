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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.model.UserDeniedManager;
import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.List;
import java.util.Objects;

/**
 * {@code Activity} for a role request.
 */
public class RequestRoleActivity extends FragmentActivity {

    private static final String LOG_TAG = RequestRoleActivity.class.getSimpleName();

    private String mRoleName;
    private String mPackageName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        mRoleName = getIntent().getStringExtra(Intent.EXTRA_ROLE_NAME);
        mPackageName = getCallingPackage();

        ensureSmsDefaultDialogCompatibility();

        if (TextUtils.isEmpty(mRoleName)) {
            Log.w(LOG_TAG, "Role name cannot be null or empty: " + mRoleName);
            finish();
            return;
        }
        if (TextUtils.isEmpty(mPackageName)) {
            Log.w(LOG_TAG, "Package name cannot be null or empty: " + mPackageName);
            finish();
            return;
        }

        // Perform checks here so that we have a chance to finish without being visible to user.
        Role role = Roles.get(this).get(mRoleName);
        if (role == null) {
            Log.w(LOG_TAG, "Unknown role: " + mRoleName);
            finish();
            return;
        }

        if (!role.isAvailable(this)) {
            Log.e(LOG_TAG, "Role is unavailable: " + mRoleName);
            finish();
            return;
        }

        if (!role.isVisible(this)) {
            Log.e(LOG_TAG, "Role is invisible: " + mRoleName);
            finish();
            return;
        }

        if (!role.isExclusive()) {
            Log.e(LOG_TAG, "Role is not exclusive: " + mRoleName);
            finish();
            return;
        }

        if (PackageUtils.getApplicationInfo(mPackageName, this) == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            finish();
            return;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        List<String> currentPackageNames = roleManager.getRoleHolders(mRoleName);
        if (currentPackageNames.contains(mPackageName)) {
            Log.i(LOG_TAG, "Application is already a role holder, role: " + mRoleName
                    + ", package: " + mPackageName);
            setResult(RESULT_OK);
            finish();
            return;
        }

        if (!role.isPackageQualified(mPackageName, this)) {
            Log.w(LOG_TAG, "Application doesn't qualify for role, role: " + mRoleName
                    + ", package: " + mPackageName);
            finish();
            return;
        }

        if (UserDeniedManager.getInstance(this).isDeniedAlways(mRoleName, mPackageName)) {
            Log.w(LOG_TAG, "Application is denied always for role, role: " + mRoleName
                    + ", package: " + mPackageName);
            finish();
            return;
        }

        // TODO: STOPSHIP: Handle other form factors.
        if (savedInstanceState == null) {
            RequestRoleFragment fragment = RequestRoleFragment.newInstance(mRoleName, mPackageName);
            getSupportFragmentManager().beginTransaction()
                    .add(fragment, null)
                    .commit();
        }
    }

    /**
     * @see com.android.settings.SmsDefaultDialog
     */
    private void ensureSmsDefaultDialogCompatibility() {
        Intent intent = getIntent();
        if (!Objects.equals(intent.getAction(), Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)) {
            return;
        }
        if (intent.hasExtra(Intent.EXTRA_ROLE_NAME)) {
            // Don't allow calling legacy interface with a role name.
            return;
        }

        Log.w(LOG_TAG, "Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT is deprecated; please use"
                + " RoleManager.createRequestRoleIntent() and Activity.startActivityForResult()"
                + " instead");
        if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
            Log.w(LOG_TAG, "Intent.EXTRA_PACKAGE_NAME is deprecated, and will be ignored in most"
                    + " cases. For SMS backup, please use the new backup role instead.");
        }

        mRoleName = null;
        mPackageName = null;

        String packageName = getCallingPackage();
        if (packageName == null) {
            return;
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName, this);
        if (applicationInfo == null || applicationInfo.targetSdkVersion >= Build.VERSION_CODES.Q) {
            return;
        }

        mRoleName = RoleManager.ROLE_SMS;
        mPackageName = packageName;

        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager.getRoleHolders(RoleManager.ROLE_SMS).contains(mPackageName)) {
            if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
                mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            }
        }
    }
}
