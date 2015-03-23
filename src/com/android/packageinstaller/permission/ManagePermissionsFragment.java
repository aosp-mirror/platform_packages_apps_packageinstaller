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

package com.android.packageinstaller.permission;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;

public final class ManagePermissionsFragment extends PreferenceFragment {
    private static final String LOG_TAG = "ManagePermissionsFragment";

    private AppPermissions mAppPermissions;

    public static ManagePermissionsFragment newInstance(String packageName) {
        ManagePermissionsFragment instance = new ManagePermissionsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        instance.setArguments(arguments);
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
    }

    private void bindUi() {
        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);

        final PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo == null) {
            getActivity().finish();
            return;
        }

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
        mAppPermissions = new AppPermissions(getActivity(),
                packageInfo, null);

        Preference.OnPreferenceChangeListener changeListener =
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        String groupName = preference.getKey();
                        AppPermissions.PermissionGroup group = mAppPermissions
                                .getPermissionGroup(groupName);

                        if (group == null) {
                            return false;
                        }

                        if (newValue == Boolean.TRUE) {
                            group.grantRuntimePermissions();
                        } else {
                            group.revokeRuntimePermissions();
                        }

                        return true;
                    }
                };

        for (AppPermissions.PermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (group.hasRuntimePermissions()) {
                SwitchPreference preference = new SwitchPreference(getActivity());
                preference.setOnPreferenceChangeListener(changeListener);
                preference.setKey(group.getName());
                preference.setIcon(group.getIconResId());
                preference.setTitle(group.getLabel());
                preference.setPersistent(false);
                screen.addPreference(preference);
            }
        }

        setPreferenceScreen(screen);
    }

    private void updateUi() {
        mAppPermissions.refresh();

        final int preferenceCount = getPreferenceScreen().getPreferenceCount();
        for (int i = 0; i < preferenceCount; i++) {
            SwitchPreference preference = (SwitchPreference)
                    getPreferenceScreen().getPreference(i);
            AppPermissions.PermissionGroup group = mAppPermissions
                    .getPermissionGroup(preference.getKey());
            if (group != null) {
                preference.setChecked(group.areRuntimePermissionsGranted());
            }
        }
    }

    private PackageInfo getPackageInfo(String packageName) {
        try {
            return getActivity().getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + getActivity().getCallingPackage(), e);
            return null;
        }
    }
}