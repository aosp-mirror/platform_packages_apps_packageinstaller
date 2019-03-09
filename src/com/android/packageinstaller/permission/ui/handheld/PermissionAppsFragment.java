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
package com.android.packageinstaller.permission.ui.handheld;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.Callback;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Map;

/**
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
public final class PermissionAppsFragment extends SettingsWithLargeHeader implements Callback {

    private static final String KEY_SHOW_SYSTEM_PREFS = "_showSystem";

    private static final String SHOW_SYSTEM_KEY = PermissionAppsFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;

    public static PermissionAppsFragment newInstance(String permissionName) {
        return setPermissionName(new PermissionAppsFragment(), permissionName);
    }

    private static <T extends Fragment> T setPermissionName(T fragment, String permissionName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        fragment.setArguments(arguments);
        return fragment;
    }

    private PermissionApps mPermissionApps;

    private PreferenceScreen mExtraScreen;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;

    private Callback mOnPermissionsLoadedListener;

    private Collator mCollator;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
        }

        setLoading(true /* loading */, false /* animate */);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        mPermissionApps = new PermissionApps(getActivity(), groupName, this);
        mPermissionApps.refresh(true);

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        addPreferencesFromResource(R.xml.allowed_denied);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissionApps.refresh(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
            updateMenu();
        }

        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_app_permissions,
                getClass().getName());
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
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi(this, mPermissionApps,
                getArguments().getString(Intent.EXTRA_PERMISSION_NAME));
    }

    private static void bindUi(SettingsWithLargeHeader fragment, PermissionApps permissionApps,
            @NonNull String groupName) {
        final Drawable icon = permissionApps.getIcon();
        final CharSequence label = permissionApps.getLabel();

        fragment.setHeader(icon, label, null);
        fragment.setSummary(Utils.getPermissionGroupDescriptionString(fragment.getActivity(),
                groupName, permissionApps.getDescription()), null);

        final ActionBar ab = fragment.getActivity().getActionBar();
        if (ab != null) {
            ab.setTitle(label);
        }
    }

    private void setOnPermissionsLoadedListener(Callback callback) {
        mOnPermissionsLoadedListener = callback;
    }

    @Override
    public void onPermissionsLoaded(PermissionApps permissionApps) {
        Context context = getPreferenceManager().getContext();

        if (context == null || getActivity() == null) {
            return;
        }

        boolean isTelevision = DeviceUtils.isTelevision(context);

        PreferenceCategory allowed = (PreferenceCategory) findPreference("allowed");
        PreferenceCategory allowedForeground = findPreference("allowed_foreground");
        PreferenceCategory denied = (PreferenceCategory) findPreference("denied");

        allowed.setOrderingAsAdded(true);
        allowedForeground.setOrderingAsAdded(true);
        denied.setOrderingAsAdded(true);

        Map<String, Preference> existingPrefs = new ArrayMap<>();
        int numPreferences = allowed.getPreferenceCount();
        for (int i = 0; i < numPreferences; i++) {
            Preference preference = allowed.getPreference(i);
            existingPrefs.put(preference.getKey(), preference);
        }
        allowed.removeAll();
        numPreferences = allowedForeground.getPreferenceCount();
        for (int i = 0; i < numPreferences; i++) {
            Preference preference = allowedForeground.getPreference(i);
            existingPrefs.put(preference.getKey(), preference);
        }
        allowedForeground.removeAll();
        numPreferences = denied.getPreferenceCount();
        for (int i = 0; i < numPreferences; i++) {
            Preference preference = denied.getPreference(i);
            existingPrefs.put(preference.getKey(), preference);
        }
        denied.removeAll();
        if (mExtraScreen != null) {
            for (int i = 0, n = mExtraScreen.getPreferenceCount(); i < n; i++) {
                Preference preference = mExtraScreen.getPreference(i);
                existingPrefs.put(preference.getKey(), preference);
            }
            mExtraScreen.removeAll();
        }

        mHasSystemApps = false;
        boolean menuOptionsInvalided = false;

        ArrayList<PermissionApp> sortedApps = new ArrayList<>(permissionApps.getApps());
        sortedApps.sort((x, y) -> mCollator.compare(x.getLabel(), y.getLabel()));

        for (int i = 0; i < sortedApps.size(); i++) {
            PermissionApp app = sortedApps.get(i);
            AppPermissionGroup group = app.getPermissionGroup();

            if (!Utils.shouldShowPermission(getContext(), group)) {
                continue;
            }

            if (!app.getAppInfo().enabled) {
                continue;
            }

            String key = app.getKey();
            Preference existingPref = existingPrefs.get(key);
            if (existingPref != null) {
                // Without this, existing preferences remember their old order.
                existingPref.setOrder(Preference.DEFAULT_ORDER);
            }

            boolean isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(group);

            if (isSystemApp && !menuOptionsInvalided) {
                mHasSystemApps = true;
                getActivity().invalidateOptionsMenu();
                menuOptionsInvalided = true;
            }

            if (isSystemApp && !isTelevision && !mShowSystem) {
                continue;
            }

            PreferenceCategory category = null;
            if (group.areRuntimePermissionsGranted()) {
                if (!group.hasPermissionWithBackgroundMode()
                        || (group.getBackgroundPermissions() != null
                        && group.getBackgroundPermissions().areRuntimePermissionsGranted())) {
                    category = allowed;
                } else {
                    category = allowedForeground;
                }
            } else {
                category = denied;
            }

            if (existingPref != null) {
                if (existingPref instanceof PermissionControlPreference) {
                    setPreferenceSummary(group, (PermissionControlPreference) existingPref,
                            context);
                }
                category.addPreference(existingPref);
                continue;
            }

            PermissionControlPreference pref = new PermissionControlPreference(context, group);
            pref.setKey(key);
            pref.setIcon(app.getIcon());
            pref.setTitle(Utils.getFullAppLabel(app.getAppInfo(), context));
            pref.setEllipsizeEnd();
            pref.useSmallerIcon();
            setPreferenceSummary(group, pref, context);

            if (isSystemApp && isTelevision) {
                if (mExtraScreen == null) {
                    mExtraScreen = getPreferenceManager().createPreferenceScreen(context);
                }
                mExtraScreen.addPreference(pref);
            } else {
                category.addPreference(pref);
            }
        }

        if (mExtraScreen != null) {
            Preference pref = allowed.findPreference(KEY_SHOW_SYSTEM_PREFS);

            int grantedCount = 0;
            for (int i = 0, n = mExtraScreen.getPreferenceCount(); i < n; i++) {
                if (((SwitchPreferenceCompat) mExtraScreen.getPreference(i)).isChecked()) {
                    grantedCount++;
                }
            }

            if (pref == null) {
                pref = new Preference(context);
                pref.setKey(KEY_SHOW_SYSTEM_PREFS);
                pref.setIcon(Utils.applyTint(context, R.drawable.ic_toc,
                        android.R.attr.colorControlNormal));
                pref.setTitle(R.string.preference_show_system_apps);
                pref.setOnPreferenceClickListener(preference -> {
                    SystemAppsFragment frag = new SystemAppsFragment();
                    setPermissionName(frag, getArguments().getString(Intent.EXTRA_PERMISSION_NAME));
                    frag.setTargetFragment(PermissionAppsFragment.this, 0);
                    getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, frag)
                        .addToBackStack("SystemApps")
                        .commit();
                    return true;
                });
                PreferenceCategory category = grantedCount > 0 ? allowed : denied;
                category.addPreference(pref);
            }

            pref.setSummary(getString(R.string.app_permissions_group_summary,
                    grantedCount, mExtraScreen.getPreferenceCount()));
        }

        if (allowed.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_apps_allowed));
            allowed.addPreference(empty);
        }
        if (allowedForeground.getPreferenceCount() == 0) {
            findPreference("allowed_foreground").setVisible(false);
        } else {
            findPreference("allowed_foreground").setVisible(true);
        }
        if (denied.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_apps_denied));
            denied.addPreference(empty);
        }

        setLoading(false /* loading */, true /* animate */);

        if (mOnPermissionsLoadedListener != null) {
            mOnPermissionsLoadedListener.onPermissionsLoaded(permissionApps);
        }
    }

    private void setPreferenceSummary(AppPermissionGroup group, PermissionControlPreference pref,
            Context context) {
        if (!Utils.isModernPermissionGroup(group.getName())) {
            return;
        }
        String lastAccessStr = Utils.getAbsoluteLastUsageString(context,
                PermissionUsages.loadLastGroupUsage(context, group));
        if (lastAccessStr != null) {
            pref.setSummary(context.getString(R.string.app_permission_most_recent_summary,
                    lastAccessStr));
        } else if (Utils.isPermissionsHubEnabled()) {
            pref.setSummary(context.getString(R.string.app_permission_never_accessed_summary));
        }
    }

    public static class SystemAppsFragment extends SettingsWithLargeHeader implements Callback {
        PermissionAppsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mOuterFragment = (PermissionAppsFragment) getTargetFragment();
            setLoading(true /* loading */, false /* animate */);
            super.onCreate(savedInstanceState);
            setHeader(mOuterFragment.mIcon, mOuterFragment.mLabel, null);
            if (mOuterFragment.mExtraScreen != null) {
                setPreferenceScreen();
            } else {
                mOuterFragment.setOnPermissionsLoadedListener(this);
            }
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
            PermissionApps permissionApps = new PermissionApps(getActivity(),
                    groupName, (Callback) null);
            bindUi(this, permissionApps, groupName);
        }

        @Override
        public void onPermissionsLoaded(PermissionApps permissionApps) {
            setPreferenceScreen();
            mOuterFragment.setOnPermissionsLoadedListener(null);
        }

        private void setPreferenceScreen() {
            setPreferenceScreen(mOuterFragment.mExtraScreen);
            setLoading(false /* loading */, true /* animate */);
        }
    }
}
