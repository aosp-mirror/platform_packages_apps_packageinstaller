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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v14.preference.SwitchPreference;
import android.support.v4.util.ArrayMap;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceScreen;
import android.util.ArraySet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.Callback;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.SafetyNetLogger;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public final class PermissionAppsFragment extends PermissionsFrameFragment implements Callback,
        OnPreferenceChangeListener {

    private static final int MENU_SHOW_SYSTEM = Menu.FIRST;
    private static final int MENU_HIDE_SYSTEM = Menu.FIRST + 1;

    public static PermissionAppsFragment newInstance(String permissionName) {
        PermissionAppsFragment instance = new PermissionAppsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        instance.setArguments(arguments);
        return instance;
    }

    private PermissionApps mPermissionApps;

    private ArrayMap<String, AppPermissionGroup> mToggledGroups;
    private ArraySet<String> mLauncherPkgs;
    private boolean mHasConfirmedRevoke;

    private boolean mShowSystem;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
        mLauncherPkgs = Utils.getLauncherPackages(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissionApps.refresh(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                R.string.menu_show_system);
        mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                R.string.menu_hide_system);
        updateMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mShowSystem = item.getItemId() == MENU_SHOW_SYSTEM;
                if (mPermissionApps.getApps() != null) {
                    onPermissionsLoaded(mPermissionApps);
                }
                updateMenu();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMenu() {
        mShowSystemMenu.setVisible(!mShowSystem);
        mHideSystemMenu.setVisible(mShowSystem);
    }

    @Override
    protected void onSetEmptyText(TextView textView) {
        textView.setText(R.string.no_apps);
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
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setTitle(getString(R.string.permission_title, label));
        }

        final ViewGroup rootView = (ViewGroup) getView();
        final ImageView iconView = (ImageView) rootView.findViewById(R.id.lb_icon);
        if (iconView != null) {
            // Set the icon as the background instead of the image because ImageView
            // doesn't properly scale vector drawables beyond their intrinsic size
            iconView.setBackground(icon);
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
    public void onPermissionsLoaded(PermissionApps permissionApps) {
        Context context = getPreferenceManager().getContext();

        if (context == null) {
            return;
        }

        PreferenceScreen preferences = getPreferenceScreen();
        preferences.removeAll();
        for (PermissionApp app : permissionApps.getApps()) {
            if (!Utils.shouldShowPermission(app)) {
                continue;
            }

            SwitchPreference pref = (SwitchPreference) findPreference(app.getKey());
            if (!mShowSystem && Utils.isSystem(app, mLauncherPkgs)) {
                if (pref != null) {
                    preferences.removePreference(pref);
                }
                continue;
            }
            if (pref == null) {
                pref = new SwitchPreference(context);
                pref.setOnPreferenceChangeListener(this);
                pref.setKey(app.getKey());
                pref.setIcon(app.getIcon());
                pref.setTitle(app.getLabel());
                pref.setPersistent(false);
                pref.setEnabled(!app.isPolicyFixed());
                preferences.addPreference(pref);
            }
            pref.setChecked(app.areRuntimePermissionsGranted());
        }
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
        String pkg = preference.getKey();
        final PermissionApp app = mPermissionApps.getApp(pkg);

        if (app == null) {
            return false;
        }
        addToggledGroup(app.getPackageName(), app.getPermissionGroup());

        if (LocationUtils.isLocked(mPermissionApps.getGroupName(), app.getPackageName())) {
            LocationUtils.showLocationDialog(getContext(), app.getLabel());
            return false;
        }
        if (newValue == Boolean.TRUE) {
            app.grantRuntimePermissions();
        } else {
            final boolean system = app.getAppInfo().isSystemApp();
            if (system || (!app.hasRuntimePermissions() && !mHasConfirmedRevoke)) {
                new AlertDialog.Builder(getContext())
                        .setMessage(system ? R.string.system_warning
                                : R.string.old_sdk_deny_warning)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.grant_dialog_button_deny,
                                new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((SwitchPreference) preference).setChecked(false);
                                app.revokeRuntimePermissions();
                                if (!system) {
                                    mHasConfirmedRevoke = true;
                                }
                            }
                        })
                        .show();
                return false;
            } else {
                app.revokeRuntimePermissions();
            }
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        logToggledGroups();
    }

    private void addToggledGroup(String packageName, AppPermissionGroup group) {
        if (mToggledGroups == null) {
            mToggledGroups = new ArrayMap<>();
        }
        // Double toggle is back to initial state.
        if (mToggledGroups.containsKey(packageName)) {
            mToggledGroups.remove(packageName);
        } else {
            mToggledGroups.put(packageName, group);
        }
    }

    private void logToggledGroups() {
        if (mToggledGroups != null) {
            final int groupCount = mToggledGroups.size();
            for (int i = 0; i < groupCount; i++) {
                String packageName = mToggledGroups.keyAt(i);
                List<AppPermissionGroup> groups = new ArrayList<>();
                groups.add(mToggledGroups.valueAt(i));
                SafetyNetLogger.logPermissionsToggled(packageName, groups);
            }
            mToggledGroups = null;
        }
    }
}
