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
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.utils.SafetyNetLogger;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public final class AppPermissionsFragment extends SettingsWithHeader
        implements OnPreferenceChangeListener {

    private static final String LOG_TAG = "ManagePermsFragment";

    private static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";

    private List<AppPermissionGroup> mToggledGroups;
    private AppPermissions mAppPermissions;
    private PreferenceScreen mExtraScreen;

    private boolean mHasConfirmedRevoke;
    private boolean mShowLegacyPermissions;

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
        mHasConfirmedRevoke = false;
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
            case android.R.id.home: {
                getActivity().finish();
                return true;
            }

            case R.id.toggle_legacy_permissions: {
                mShowLegacyPermissions = !mShowLegacyPermissions;
                bindPermissionsUi();
                return true;
            }
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.toggle_legacy_permissions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.toggle_legacy_permissions);
        if (!mShowLegacyPermissions) {
            item.setTitle(R.string.show_legacy_permissions);
        } else {
            item.setTitle(R.string.hide_legacy_permissions);
        }
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

        mAppPermissions = new AppPermissions(activity, packageInfo, null, new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        });

        bindPermissionsUi();
    }

    private void bindPermissionsUi() {
        final Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(activity);
            setPreferenceScreen(screen);
        } else {
            screen.removeAll();
        }

        if (mExtraScreen != null) {
            mExtraScreen.removeAll();
        }

        final Preference extraPerms = new Preference(activity);
        extraPerms.setIcon(R.drawable.ic_toc);
        extraPerms.setTitle(R.string.additional_permissions);

        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            final boolean isPlatformPermission = group.getDeclaringPackage().equals(Utils.OS_PKG);
            if (!Utils.shouldShowPermission(group, mShowLegacyPermissions)) {
                continue;
            }

            SwitchPreference preference = new SwitchPreference(activity);
            preference.setOnPreferenceChangeListener(this);
            preference.setKey(group.getName());
            Drawable icon = Utils.loadDrawable(activity.getPackageManager(),
                    group.getIconPkg(), group.getIconResId());
            preference.setIcon(Utils.applyTint(getContext(), icon,
                    android.R.attr.colorControlNormal));
            preference.setTitle(group.getLabel());
            preference.setPersistent(false);
            preference.setEnabled(!group.isPolicyFixed());
            preference.setChecked(group.areRuntimePermissionsGranted());

            if (isPlatformPermission) {
                screen.addPreference(preference);
            } else {
                if (mExtraScreen == null) {
                    mExtraScreen = getPreferenceManager().createPreferenceScreen(activity);
                }
                mExtraScreen.addPreference(preference);
            }
        }

        if (mExtraScreen != null) {
            extraPerms.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AdditionalPermissionsFragment frag = new AdditionalPermissionsFragment();
                    frag.setTargetFragment(AppPermissionsFragment.this, 0);
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(android.R.id.content, frag);
                    ft.addToBackStack("AdditionalPerms");
                    ft.commit();
                    return true;
                }
            });
            extraPerms.setSummary(getString(R.string.additional_permissions_more,
                    mExtraScreen.getPreferenceCount()));
            screen.addPreference(extraPerms);
        }
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
        String groupName = preference.getKey();
        final AppPermissionGroup group = mAppPermissions.getPermissionGroup(groupName);

        if (group == null) {
            return false;
        }

        addToggledGroup(group);

        if (newValue == Boolean.TRUE) {
            group.grantRuntimePermissions(false);
        } else {
            if (!group.hasRuntimePermission() && !mHasConfirmedRevoke) {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.old_sdk_deny_warning)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.grant_dialog_button_deny,
                                new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((SwitchPreference) preference).setChecked(false);
                                group.revokeRuntimePermissions(false);
                                mHasConfirmedRevoke = true;
                            }
                        })
                        .show();
                return false;
            } else {
                group.revokeRuntimePermissions(false);
            }
        }

        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        logToggledGroups();
    }

    private void addToggledGroup(AppPermissionGroup group) {
        if (mToggledGroups == null) {
            mToggledGroups = new ArrayList<>();
        }
        // Double toggle is back to initial state.
        if (mToggledGroups.contains(group)) {
            mToggledGroups.remove(group);
        } else {
            mToggledGroups.add(group);
        }
    }

    private void logToggledGroups() {
        if (mToggledGroups != null) {
            String packageName = mAppPermissions.getPackageInfo().packageName;
            SafetyNetLogger.logPermissionsToggled(packageName, mToggledGroups);
            mToggledGroups = null;
        }
    }

    private void updateUi() {
        mAppPermissions.refresh();

        updatePrefs(getPreferenceScreen());
        if (mExtraScreen != null) {
            updatePrefs(mExtraScreen);
        }
    }

    private void updatePrefs(PreferenceScreen screen) {
        final int preferenceCount = screen.getPreferenceCount();
        for (int i = 0; i < preferenceCount; i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof SwitchPreference) {
                SwitchPreference switchPref = (SwitchPreference) preference;
                AppPermissionGroup group = mAppPermissions
                        .getPermissionGroup(switchPref.getKey());
                if (group != null) {
                    switchPref.setChecked(group.areRuntimePermissionsGranted());
                }
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

    public static class AdditionalPermissionsFragment extends SettingsWithHeader {
        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            AppPermissionsFragment target = (AppPermissionsFragment) getTargetFragment();
            setPreferenceScreen(target.mExtraScreen);
            // Copy the header.
            setHeader(target.mIcon, target.mLabel, target.mInfoIntent);
        }
    }
}