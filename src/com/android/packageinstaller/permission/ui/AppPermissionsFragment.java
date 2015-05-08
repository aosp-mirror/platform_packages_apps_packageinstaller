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
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.PermissionGroup;

public final class AppPermissionsFragment extends SettingsWithHeader
        implements OnPreferenceChangeListener {
    private static final String LOG_TAG = "ManagePermsFragment";

    private static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";

    private AppPermissions mAppPermissions;

    public static AppPermissionsFragment newInstance(String packageName) {
        AppPermissionsFragment instance = new AppPermissionsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        instance.setArguments(arguments);
        return instance;
    }

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
        updateUi();
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
        ((ListView) rootView.findViewById(android.R.id.list)).setEmptyView(emptyView);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi();
    }

    private void bindUi() {
        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);

        final Activity activity = getActivity();
        PackageInfo packageInfo = getPackageInfo(packageName);
        if (packageInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return;
        }
        final PackageManager pm = activity.getPackageManager();
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        final Drawable icon = appInfo.loadIcon(pm);
        final CharSequence label = appInfo.loadLabel(pm);
        Intent infoIntent = null;
        if (!getActivity().getIntent().getBooleanExtra(EXTRA_HIDE_INFO_BUTTON, false)) {
            infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null));
        }
        setHeader(icon, label, infoIntent);

        final ViewGroup rootView = (ViewGroup) getView();
        final ImageView iconView = (ImageView) rootView.findViewById(R.id.lb_icon);
        if (iconView != null) {
            iconView.setImageDrawable(icon);
        }
        final TextView titleView = (TextView) rootView.findViewById(R.id.lb_title);
        if (titleView != null) {
            titleView.setText(R.string.app_permissions);
        }
        final TextView breadcrumbView = (TextView) rootView.findViewById(R.id.lb_breadcrumb);
        if (breadcrumbView != null) {
            breadcrumbView.setText(label);
        }

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(activity);
        mAppPermissions = new AppPermissions(activity, packageInfo, null);

        for (PermissionGroup group : mAppPermissions.getPermissionGroups()) {
            SwitchPreference preference = new SwitchPreference(activity);
            preference.setOnPreferenceChangeListener(this);
            preference.setKey(group.getName());
            preference.setIcon(Utils.loadDrawable(pm, group.getIconPkg(),
                    group.getIconResId()));
            preference.setTitle(group.getLabel());
            preference.setPersistent(false);
            screen.addPreference(preference);
        }

        setPreferenceScreen(screen);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String groupName = preference.getKey();
        PermissionGroup group = mAppPermissions.getPermissionGroup(groupName);

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

    private void updateUi() {
        mAppPermissions.refresh();

        final int preferenceCount = getPreferenceScreen().getPreferenceCount();
        for (int i = 0; i < preferenceCount; i++) {
            SwitchPreference preference = (SwitchPreference)
                    getPreferenceScreen().getPreference(i);
            PermissionGroup group = mAppPermissions
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