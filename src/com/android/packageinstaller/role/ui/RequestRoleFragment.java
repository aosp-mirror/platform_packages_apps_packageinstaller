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

import android.app.Activity;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.app.role.RoleManagerCallback;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.permission.utils.PackageRemovalMonitor;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.utils.PackageUtils;
import com.android.permissioncontroller.R;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * {@code Fragment} for a role request.
 */
public class RequestRoleFragment extends DialogFragment {

    private static final String LOG_TAG = RequestRoleFragment.class.getSimpleName();

    private String mRoleName;
    private String mPackageName;

    @Nullable
    private PackageRemovalMonitor mPackageRemovalMonitor;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the requested role
     * @param packageName the package name of the application requesting the role
     *
     * @return a new instance of this fragment
     */
    public static RequestRoleFragment newInstance(@NonNull String roleName,
            @NonNull String packageName) {
        RequestRoleFragment instance = new RequestRoleFragment();
        Bundle arguments = new Bundle();
        arguments.putString(RoleManager.EXTRA_REQUEST_ROLE_NAME, roleName);
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        instance.setArguments(arguments);
        return instance;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mPackageName = arguments.getString(Intent.EXTRA_PACKAGE_NAME);
        mRoleName = arguments.getString(RoleManager.EXTRA_REQUEST_ROLE_NAME);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        Role role = Roles.getRoles(context).get(mRoleName);
        if (role == null) {
            Log.w(LOG_TAG, "Unknown role: " + mRoleName);
            finish();
            return super.onCreateDialog(savedInstanceState);
        }
        // FIXME: STOPSHIP: Add a label for role.
        String roleLabel = role.getName();

        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(mPackageName, context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            finish();
            return super.onCreateDialog(savedInstanceState);
        }
        String applicationLabel = Utils.getAppLabel(applicationInfo, context);

        RoleManager roleManager = context.getSystemService(RoleManager.class);
        List<String> currentPackageNames = roleManager.getRoleHolders(mRoleName);
        if (currentPackageNames.contains(mPackageName)) {
            Log.i(LOG_TAG, "Application is already a role holder, role: " + mRoleName
                    + ", application: " + mPackageName);
            setResultOkAndFinish();
            return super.onCreateDialog(savedInstanceState);
        }
        String currentApplicationLabel = role.isExclusive() ? getCurrentApplicationLabel(
                currentPackageNames, context) : null;

        String message;
        if (currentApplicationLabel == null) {
            message = getString(R.string.role_request_message_add, applicationLabel, roleLabel);
        } else {
            message = getString(R.string.role_request_message_replace, applicationLabel,
                    currentApplicationLabel, roleLabel);
        }

        return new AlertDialog.Builder(context, getTheme())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> addRoleHolder())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();

        mPackageRemovalMonitor = new PackageRemovalMonitor(requireContext(), mPackageName) {
            @Override
            protected void onPackageRemoved() {
                finish();
            }
        };
        mPackageRemovalMonitor.register();
    }

    @Override
    public void onStop() {
        super.onStop();

        mPackageRemovalMonitor.unregister();
        mPackageRemovalMonitor = null;
    }

    private void addRoleHolder() {
        Log.i(LOG_TAG, "Adding package as role holder, role: " + mRoleName + ", package: "
                + mPackageName);
        Context context = requireContext();
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        UserHandle user = UserHandle.of(UserHandle.myUserId());
        Executor executor = context.getMainExecutor();
        roleManager.addRoleHolderAsUser(mRoleName, mPackageName, user, executor,
                // TODO: Add progress or simply disable UI.
                // FIXME: STOPSHIP: Leaking, and NPE! Use something like Loader or LiveData instead.
                new RoleManagerCallback() {
                    @Override
                    public void onSuccess() {
                        Log.e(LOG_TAG, "Package added as role holder, role: " + mRoleName
                                + ", package: " + mPackageName);
                        setResultOkAndFinish();
                    }
                    @Override
                    public void onFailure() {
                        Log.e(LOG_TAG, "Failed to add package as role holder, role: " + mRoleName
                                + ", package: " + mPackageName);
                        finish();
                    }
                });
    }

    private void setResultOkAndFinish() {
        requireActivity().setResult(Activity.RESULT_OK);
        finish();
    }

    private void finish() {
        requireActivity().finish();
    }

    private static String getCurrentApplicationLabel(@NonNull List<String> currentPackageNames,
            @NonNull Context context) {
        if (currentPackageNames.isEmpty()) {
            return null;
        }
        String currentPackageName = currentPackageNames.get(0);

        ApplicationInfo currentApplicationInfo = PackageUtils.getApplicationInfo(
                currentPackageName, context);
        if (currentApplicationInfo == null) {
            return null;
        }

        return Utils.getAppLabel(currentApplicationInfo, context);
    }
}
