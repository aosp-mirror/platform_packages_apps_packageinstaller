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
package com.android.packageinstaller.permission.ui;

import android.annotation.Nullable;
import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.Callback;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;

public final class PermissionAppsFragment extends SettingsWithHeader implements Callback,
        OnPreferenceChangeListener {

    public static PermissionAppsFragment newInstance(String permissionName) {
        PermissionAppsFragment instance = new PermissionAppsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        instance.setArguments(arguments);
        return instance;
    }

    private PermissionApps mPermissionApps;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissionApps.refresh();
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.permissions_frame, container,
                        false);
        ViewGroup prefsContainer = (ViewGroup) rootView.findViewById(R.id.prefs_container);
        if (prefsContainer == null) {
            prefsContainer = rootView;
        }
        prefsContainer.addView(super.onCreateView(inflater, prefsContainer, savedInstanceState));
        View emptyView = rootView.findViewById(R.id.no_permissions);
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi();
    }

    private void bindUi() {
        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        mPermissionApps = new PermissionApps(getActivity(), groupName, this);
        final Drawable icon = mPermissionApps.getIcon();
        final CharSequence label = mPermissionApps.getLabel();
        setHeader(icon, label, null);

        final ViewGroup rootView = (ViewGroup) getView();
        final ImageView iconView = (ImageView) rootView.findViewById(R.id.lb_icon);
        if (iconView != null) {
            iconView.setImageDrawable(icon);
        }
        final TextView titleView = (TextView) rootView.findViewById(R.id.lb_title);
        if (titleView != null) {
            titleView.setText(label);
        }
        final TextView breadcrumbView = (TextView) rootView.findViewById(R.id.lb_breadcrumb);
        if (breadcrumbView != null) {
            breadcrumbView.setText(R.string.app_permissions);
        }
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
        for (PermissionApp app : mPermissionApps.getApps()) {
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
            pref.setChecked(app.areRuntimePermissionsGranted());
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String pkg = preference.getKey();
        PermissionApp app = mPermissionApps.getApp(pkg);

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
