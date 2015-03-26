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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.MenuItem;

import com.android.packageinstaller.permission.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.PermissionApps.Callback;

public class PermissionManagementFragment extends SettingsWithHeader implements Callback, OnPreferenceChangeListener {

    public static PermissionManagementFragment newInstance(String permissionName) {
        PermissionManagementFragment instance = new PermissionManagementFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        instance.setArguments(arguments);
        return instance;
    }

    private PermissionApps mPermissionGroup;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        bindUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissionGroup.refresh();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void bindUi() {
        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        mPermissionGroup = new PermissionApps(getActivity(), groupName, this);
        setHeader(mPermissionGroup.getIcon(), mPermissionGroup.getLabel(), null);
    }

    @Override
    public void onPermissionsLoaded() {
        Context context = getActivity();
        PreferenceScreen preferences = getPreferenceScreen();
        if (preferences == null) {
            preferences = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(preferences);
        }
        preferences.removeAll();
        for (PermissionApp app : mPermissionGroup.getApps()) {
            SwitchPreference pref = (SwitchPreference) findPreference(app.getKey());
            if (pref == null) {
                pref = new SwitchPreference(context);
                pref.setOnPreferenceChangeListener(this);
                pref.setKey(app.getKey());
                pref.setIcon(app.getIcon());
                pref.setTitle(app.getLabel());
                pref.setPersistent(false);
                preferences.addPreference(pref);
            }
            pref.setChecked(app.hasRuntimePermissions());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String pkg = preference.getKey();
        PermissionApp app = mPermissionGroup.getApp(pkg);

        if (app == null) {
            return false;
        }
        if (newValue == Boolean.TRUE) {
            app.grantRuntimePermissions();
        } else {
            app.revokeRuntimePermissions();
        }
        return true;
    }

}
