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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import com.android.packageinstaller.permission.utils.PackageRemovalMonitor;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.utils.PackageUtils;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * {@code Fragment} for a role request.
 */
public class RequestRoleFragment extends DialogFragment {

    private static final String LOG_TAG = RequestRoleFragment.class.getSimpleName();

    private String mRoleName;
    private String mPackageName;

    private RequestRoleViewModel mViewModel;

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
        String roleLabel = getString(role.getLabelResource());

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
                    + ", package: " + mPackageName);
            setResultOkAndFinish();
            return super.onCreateDialog(savedInstanceState);
        }
        String currentApplicationLabel = role.isExclusive() ? getCurrentApplicationLabel(
                currentPackageNames, context) : null;

        String messageHtml;
        if (currentApplicationLabel == null) {
            messageHtml = getString(R.string.role_request_message_add, applicationLabel, roleLabel);
        } else {
            messageHtml = getString(R.string.role_request_message_replace, applicationLabel,
                    currentApplicationLabel, roleLabel);
        }
        CharSequence message = Html.fromHtml(messageHtml, Html.FROM_HTML_MODE_LEGACY);

        return new AlertDialog.Builder(context, getTheme())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> addRoleHolder())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .create();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this).get(RequestRoleViewModel.class);
        mViewModel.getLiveData().observe(this, this::onRequestRoleStateChanged);
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

    private void onRequestRoleStateChanged(int state) {
        AlertDialog dialog = (AlertDialog) getDialog();
        switch (state) {
            case RequestRoleLiveData.STATE_IDLE:
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                break;
            case RequestRoleLiveData.STATE_ADDING:
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                break;
            case RequestRoleLiveData.STATE_SUCCESS:
                setResultOkAndFinish();
                break;
            case RequestRoleLiveData.STATE_FAILURE:
                finish();
                break;
        }
    }

    private void addRoleHolder() {
        mViewModel.getLiveData().addRoleHolder(mRoleName, mPackageName, requireContext());
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
