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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.utils.CollectionUtils;
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

        if (!handleChangeDefaultDialerDialogCompatibility()) {
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (!handleSmsDefaultDialogCompatibility()) {
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (TextUtils.isEmpty(mRoleName)) {
            Log.w(LOG_TAG, "Role name cannot be null or empty: " + mRoleName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }
        if (TextUtils.isEmpty(mPackageName)) {
            Log.w(LOG_TAG, "Package name cannot be null or empty: " + mPackageName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        // Perform checks here so that we have a chance to finish without being visible to user.
        Role role = Roles.get(this).get(mRoleName);
        if (role == null) {
            Log.w(LOG_TAG, "Unknown role: " + mRoleName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (!role.isAvailable(this)) {
            Log.e(LOG_TAG, "Role is unavailable: " + mRoleName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (!role.isVisible(this)) {
            Log.e(LOG_TAG, "Role is invisible: " + mRoleName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (!role.isRequestable()) {
            Log.e(LOG_TAG, "Role is not requestable: " + mRoleName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (!role.isExclusive()) {
            Log.e(LOG_TAG, "Role is not exclusive: " + mRoleName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        if (PackageUtils.getApplicationInfo(mPackageName, this) == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            reportRequestResult(
                    PermissionControllerStatsLog.ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            finish();
            return;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        List<String> currentPackageNames = roleManager.getRoleHolders(mRoleName);
        if (currentPackageNames.contains(mPackageName)) {
            Log.i(LOG_TAG, "Application is already a role holder, role: " + mRoleName
                    + ", package: " + mPackageName);
            reportRequestResult(PermissionControllerStatsLog
                    .ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED_ALREADY_GRANTED);
            setResult(RESULT_OK);
            finish();
            return;
        }

        if (!role.isPackageQualified(mPackageName, this)) {
            Log.w(LOG_TAG, "Application doesn't qualify for role, role: " + mRoleName
                    + ", package: " + mPackageName);
            reportRequestResult(PermissionControllerStatsLog
                    .ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED_NOT_QUALIFIED);
            finish();
            return;
        }

        if (UserDeniedManager.getInstance(this).isDeniedAlways(mRoleName, mPackageName)) {
            Log.w(LOG_TAG, "Application is denied always for role, role: " + mRoleName
                    + ", package: " + mPackageName);
            reportRequestResult(PermissionControllerStatsLog
                    .ROLE_REQUEST_RESULT_REPORTED__RESULT__IGNORED_USER_ALWAYS_DENIED);
            finish();
            return;
        }

        if (savedInstanceState == null) {
            RequestRoleFragment fragment = RequestRoleFragment.newInstance(mRoleName, mPackageName);
            getSupportFragmentManager().beginTransaction()
                    .add(fragment, null)
                    .commit();
        }
    }

    /**
     * Handle compatibility with the old
     * {@link com.android.server.telecom.components.ChangeDefaultDialerDialog}.
     *
     * @return whether we should continue requesting the role. The activity should be finished if
     *         {@code false} is returned.
     */
    private boolean handleChangeDefaultDialerDialogCompatibility() {
        Intent intent = getIntent();
        if (!Objects.equals(intent.getAction(), TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)) {
            return true;
        }

        Log.w(LOG_TAG, "TelecomManager.ACTION_CHANGE_DEFAULT_DIALER is deprecated; please use"
                + " RoleManager.createRequestRoleIntent() and Activity.startActivityForResult()"
                + " instead");

        mRoleName = RoleManager.ROLE_DIALER;
        mPackageName = null;

        // Intent.EXTRA_CALLING_PACKAGE is set in PermissionPolicyService.Internal
        // .isActionRemovedForCallingPackage() and can be trusted.
        String callingPackageName = intent.getStringExtra(Intent.EXTRA_CALLING_PACKAGE);
        String extraPackageName = intent.getStringExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME);
        if (Objects.equals(extraPackageName, callingPackageName)) {
            // Requesting for itself is okay.
            mPackageName = extraPackageName;
            return true;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        String holderPackageName = CollectionUtils.firstOrNull(roleManager.getRoleHolders(
                RoleManager.ROLE_DIALER));
        if (Objects.equals(callingPackageName, holderPackageName)) {
            // Giving away its own role is okay.
            mPackageName = extraPackageName;
            return true;
        }

        // If we reach here it's not okay.
        return false;
    }

    /**
     * Handle compatibility with the old {@link com.android.settings.SmsDefaultDialog}.
     *
     * @return whether we should continue requesting the role. The activity should be finished if
     *         {@code false} is returned.
     */
    private boolean handleSmsDefaultDialogCompatibility() {
        Intent intent = getIntent();
        if (!Objects.equals(intent.getAction(), Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)) {
            return true;
        }

        Log.w(LOG_TAG, "Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT is deprecated; please use"
                + " RoleManager.createRequestRoleIntent() and Activity.startActivityForResult()"
                + " instead");

        mRoleName = RoleManager.ROLE_SMS;
        mPackageName = null;

        // Intent.EXTRA_CALLING_PACKAGE is set in PermissionPolicyService.Internal
        // .isActionRemovedForCallingPackage() and can be trusted.
        String callingPackageName = intent.getStringExtra(Intent.EXTRA_CALLING_PACKAGE);
        String extraPackageName = intent.getStringExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME);
        if (extraPackageName == null) {
            // Launch the settings activity to show the list.
            // TODO: Return RESULT_OK if any changes were made?
            Intent defaultAppActivityIntent = DefaultAppActivity.createIntent(
                    RoleManager.ROLE_SMS, Process.myUserHandle(), this)
                    .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            startActivity(defaultAppActivityIntent);
            return false;
        }

        if (Objects.equals(extraPackageName, callingPackageName)) {
            // Requesting for itself is okay.
            mPackageName = extraPackageName;
            return true;
        }

        RoleManager roleManager = getSystemService(RoleManager.class);
        String holderPackageName = CollectionUtils.firstOrNull(roleManager.getRoleHolders(
                RoleManager.ROLE_SMS));
        if (Objects.equals(callingPackageName, holderPackageName)) {
            // Giving away its own role is okay.
            mPackageName = extraPackageName;
            return true;
        }

        // If we reach here it's not okay.
        return false;
    }

    private void reportRequestResult(int result) {
        RequestRoleFragment.reportRequestResult(getApplicationUid(mPackageName, this), mPackageName,
                mRoleName, -1, -1, null, -1, null, result);
    }

    private static int getApplicationUid(@Nullable String packageName, @NonNull Context context) {
        if (packageName == null) {
            return -1;
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            return -1;
        }
        return applicationInfo.uid;
    }
}
