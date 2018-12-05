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
import android.util.ArraySet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.Callback;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;

/**
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
public final class PermissionAppsFragment extends PermissionsFrameFragment implements Callback {

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

    private ArraySet<String> mLauncherPkgs;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;

    private Callback mOnPermissionsLoadedListener;

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
        mLauncherPkgs = Utils.getLauncherPackages(getContext());

        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        mPermissionApps = new PermissionApps(getActivity(), groupName, this);
        mPermissionApps.refresh(true);

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
        bindUi(this, mPermissionApps);
    }

    private static void bindUi(Fragment fragment, PermissionApps permissionApps) {
        final Drawable icon = permissionApps.getIcon();
        final CharSequence label = permissionApps.getLabel();
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

        if (context == null) {
            return;
        }

        boolean isTelevision = DeviceUtils.isTelevision(context);

        PreferenceCategory allowed = (PreferenceCategory) findPreference("allowed");
        PreferenceCategory denied = (PreferenceCategory) findPreference("denied");

        ArraySet<String> preferencesToRemove = new ArraySet<>();
        int numPreferences = allowed.getPreferenceCount();
        for (int i = 0; i < numPreferences; i++) {
            preferencesToRemove.add(allowed.getPreference(i).getKey());
        }
        numPreferences = denied.getPreferenceCount();
        for (int i = 0; i < numPreferences; i++) {
            preferencesToRemove.add(denied.getPreference(i).getKey());
        }
        if (mExtraScreen != null) {
            for (int i = 0, n = mExtraScreen.getPreferenceCount(); i < n; i++) {
                preferencesToRemove.add(mExtraScreen.getPreference(i).getKey());
            }
        }

        mHasSystemApps = false;
        boolean menuOptionsInvalided = false;

        for (PermissionApp app : permissionApps.getApps()) {
            if (!Utils.shouldShowPermission(getContext(), app.getPermissionGroup())) {
                continue;
            }

            if (!app.getAppInfo().enabled) {
                continue;
            }

            String key = app.getKey();
            preferencesToRemove.remove(key);
            Preference existingPref = findExistingPreference(key, allowed, denied);

            boolean isSystemApp = Utils.isSystem(app, mLauncherPkgs);

            if (isSystemApp && !menuOptionsInvalided) {
                mHasSystemApps = true;
                getActivity().invalidateOptionsMenu();
                menuOptionsInvalided = true;
            }

            if (isSystemApp && !isTelevision && !mShowSystem) {
                if (existingPref != null) {
                    existingPref.getParent().removePreference(existingPref);
                }
                continue;
            }

            PreferenceCategory category = app.areRuntimePermissionsGranted() ? allowed : denied;

            if (existingPref != null) {
                // If the granted status has changed, move the permission to the new category.
                if (category != existingPref.getParent()) {
                    existingPref.getParent().removePreference(existingPref);
                    category.addPreference(existingPref);
                }
                continue;
            }

            Preference pref = new PermissionUsagePreference(context, app.getPermissionGroup());
            pref.setKey(app.getKey());
            pref.setIcon(app.getIcon());
            pref.setTitle(app.getLabel());

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
            preferencesToRemove.remove(KEY_SHOW_SYSTEM_PREFS);
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

        for (String key : preferencesToRemove) {
            Preference pref = findExistingPreference(key, allowed, denied);
            if (pref != null) {
                pref.getParent().removePreference(pref);
            }
        }

        setLoading(false /* loading */, true /* animate */);

        if (mOnPermissionsLoadedListener != null) {
            mOnPermissionsLoadedListener.onPermissionsLoaded(permissionApps);
        }
    }

    private Preference findExistingPreference(String key, PreferenceCategory allowed,
            PreferenceCategory denied) {
        Preference preference = allowed.findPreference(key);
        if (preference != null) {
            return preference;
        }
        preference = denied.findPreference(key);
        if (preference != null) {
            return preference;
        }
        if (mExtraScreen != null) {
            preference = mExtraScreen.findPreference(key);
            if (preference != null) {
                return preference;
            }
        }
        return null;
    }

    public static class SystemAppsFragment extends PermissionsFrameFragment implements Callback {
        PermissionAppsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mOuterFragment = (PermissionAppsFragment) getTargetFragment();
            setLoading(true /* loading */, false /* animate */);
            super.onCreate(savedInstanceState);
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
            PermissionApps permissionApps = new PermissionApps(getActivity(), groupName, null);
            bindUi(this, permissionApps);
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
