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

import static com.android.packageinstaller.Constants.EXTRA_SESSION_ID;
import static com.android.packageinstaller.Constants.INVALID_SESSION_ID;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__DENIED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__UNDEFINED;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
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
import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.Callback;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

/**
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
public final class PermissionAppsFragment extends SettingsWithLargeHeader implements Callback {

    private static final String KEY_SHOW_SYSTEM_PREFS = "_showSystem";
    private static final String CREATION_LOGGED_SYSTEM_PREFS = "_creationLogged";
    private static final String KEY_FOOTER = "_footer";
    private static final String LOG_TAG = "PermissionAppsFragment";

    private static final String SHOW_SYSTEM_KEY = PermissionAppsFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;

    private static final String CREATION_LOGGED = PermissionAppsFragment.class.getName()
            + CREATION_LOGGED_SYSTEM_PREFS;

    /**
     * @return A new fragment
     */
    public static PermissionAppsFragment newInstance(String permissionName, long sessionId) {
        return setPermissionNameAndSessionId(
                new PermissionAppsFragment(), permissionName, sessionId);
    }

    private static <T extends Fragment> T setPermissionNameAndSessionId(
            T fragment, String permissionName, long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }

    private PermissionApps mPermissionApps;

    private PreferenceScreen mExtraScreen;

    private boolean mShowSystem;
    private boolean mCreationLogged;
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
            mCreationLogged = savedInstanceState.getBoolean(CREATION_LOGGED);
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
        outState.putBoolean(CREATION_LOGGED, mCreationLogged);
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
        final CharSequence label = permissionApps.getFullLabel();

        fragment.setHeader(icon, label, null, null, true);
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
        boolean hasPermissionWithBackgroundMode = false;

        ArrayList<PermissionApp> sortedApps = new ArrayList<>(permissionApps.getApps());
        sortedApps.sort((x, y) -> {
            int result = mCollator.compare(x.getLabel(), y.getLabel());
            if (result == 0) {
                result = x.getUid() - y.getUid();
            }
            return result;
        });

        long viewIdForLogging = new Random().nextLong();
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        for (int i = 0; i < sortedApps.size(); i++) {
            PermissionApp app = sortedApps.get(i);
            AppPermissionGroup group = app.getPermissionGroup();

            hasPermissionWithBackgroundMode =
                    hasPermissionWithBackgroundMode || group.hasPermissionWithBackgroundMode();

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
                category.addPreference(existingPref);
                continue;
            }

            PermissionControlPreference pref = new PermissionControlPreference(context, group,
                    PermissionAppsFragment.class.getName(), sessionId);
            pref.setKey(key);
            pref.setIcon(app.getIcon());
            pref.setTitle(Utils.getFullAppLabel(app.getAppInfo(), context));
            pref.setEllipsizeEnd();
            pref.useSmallerIcon();

            if (isSystemApp && isTelevision) {
                if (mExtraScreen == null) {
                    mExtraScreen = getPreferenceManager().createPreferenceScreen(context);
                }
                mExtraScreen.addPreference(pref);
            } else {
                category.addPreference(pref);
                if (!mCreationLogged) {
                    logPermissionAppsFragmentCreated(app, viewIdForLogging, category == allowed,
                            category == allowedForeground, category == denied);
                }
            }
        }
        mCreationLogged = true;

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
                    setPermissionNameAndSessionId(frag,
                            getArguments().getString(Intent.EXTRA_PERMISSION_NAME), sessionId);
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

        if (hasPermissionWithBackgroundMode) {
            allowed.setTitle(R.string.allowed_always_header);
        }

        if (allowed.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_apps_allowed));
            empty.setSelectable(false);
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
            empty.setSelectable(false);
            denied.addPreference(empty);
        }

        setLoading(false /* loading */, true /* animate */);

        if (mOnPermissionsLoadedListener != null) {
            mOnPermissionsLoadedListener.onPermissionsLoaded(permissionApps);
        }
    }

    private void logPermissionAppsFragmentCreated(PermissionApp permissionApp, long viewId,
            boolean isAllowed, boolean isAllowedForeground, boolean isDenied) {
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, 0);

        int category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__UNDEFINED;
        if (isAllowed) {
            category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
        } else if (isAllowedForeground) {
            category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
        } else if (isDenied) {
            category = PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__DENIED;
        }

        PermissionControllerStatsLog.write(PERMISSION_APPS_FRAGMENT_VIEWED, sessionId, viewId,
                mPermissionApps.getGroupName(), permissionApp.getUid(),
                permissionApp.getPackageName(), category);
        Log.v(LOG_TAG, "PermissionAppsFragment created with sessionId=" + sessionId
                + " permissionGroupName=" + mPermissionApps.getGroupName() + " appUid="
                + permissionApp.getUid() + " packageName=" + permissionApp.getPackageName()
                + " category=" + category);
    };

    public static class SystemAppsFragment extends SettingsWithLargeHeader implements Callback {
        PermissionAppsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mOuterFragment = (PermissionAppsFragment) getTargetFragment();
            setLoading(true /* loading */, false /* animate */);
            super.onCreate(savedInstanceState);
            setHeader(mOuterFragment.mIcon, mOuterFragment.mLabel, null, null, true);
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
