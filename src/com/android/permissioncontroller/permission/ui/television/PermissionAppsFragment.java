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
package com.android.permissioncontroller.permission.ui.television;

import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.ArraySet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.Callback;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp;
import com.android.permissioncontroller.permission.ui.ReviewPermissionsActivity;
import com.android.permissioncontroller.permission.utils.LocationUtils;
import com.android.permissioncontroller.permission.utils.SafetyNetLogger;
import com.android.permissioncontroller.permission.utils.Utils;

public final class PermissionAppsFragment extends SettingsWithHeader implements Callback,
        OnPreferenceClickListener {

    private static final int MENU_SHOW_SYSTEM = Menu.FIRST;
    private static final int MENU_HIDE_SYSTEM = Menu.FIRST + 1;
    private static final String KEY_CATEGORY_ALLOWED = "_allowed";
    private static final String KEY_CATEGORY_DENIED = "_denied";
    private static final String KEY_NO_APPS_ALLOWED = "_noAppsAllowed";
    private static final String KEY_NO_APPS_DENIED = "_noAppsDenied";
    private static final String KEY_SHOW_SYSTEM_PREFS = "_showSystem";

    public static PermissionAppsFragment newInstance(String permissionName) {
        return setPermissionName(new PermissionAppsFragment(), permissionName);
    }

    private static <T extends PermissionsFrameFragment> T setPermissionName(
            T fragment, String permissionName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        fragment.setArguments(arguments);
        return fragment;
    }

    private PermissionApps mPermissionApps;

    private PreferenceScreen mExtraScreen;

    private ArraySet<AppPermissionGroup> mToggledGroups;
    private boolean mHasConfirmedRevoke;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;

    private Callback mOnPermissionsLoadedListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true /* loading */, false /* animate */);
        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        mPermissionApps = new PermissionApps(getActivity(), groupName, this);
    }

    @Override
    public void onStart() {
        super.onStart();

        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPermissionApps.refresh(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
            updateMenu();
        }
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
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi(this, mPermissionApps);
    }

    private static void bindUi(SettingsWithHeader fragment, PermissionApps permissionApps) {
        final Drawable icon = permissionApps.getIcon();
        final CharSequence label = permissionApps.getLabel();

        fragment.setHeader(null, null, null,
                fragment.getString(R.string.permission_apps_decor_title, label));
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

        mHasSystemApps = false;
        boolean isTelevision = DeviceUtils.isTelevision(context);
        ArraySet<PermissionApp> mainScreenApps = new ArraySet<>();
        ArraySet<PermissionApp> systemScreenApps = new ArraySet<>();
        for (PermissionApp app : permissionApps.getApps()) {
            if (!Utils.shouldShowPermission(getContext(), app.getPermissionGroup())) {
                continue;
            }

            boolean isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(app.getPermissionGroup());
            if (isSystemApp) {
                mHasSystemApps = true;
            }

            if (isSystemApp && isTelevision) {
                systemScreenApps.add(app);
            } else if (!isSystemApp || mShowSystem) {
                mainScreenApps.add(app);
            } else {
                // Ignoring system apps if not TV and not asked to show system apps (mShowSystem
                // is false)
            }
        }

        PreferenceScreen mainScreen = getPreferenceScreen();
        if (!systemScreenApps.isEmpty()) {
            if (mExtraScreen == null) {
                mExtraScreen = getPreferenceManager().createPreferenceScreen(context);
            }
        } else {
            mExtraScreen = null;
        }

        // Updating "main" screen categories
        PreferenceCategory allowedCategory = findOrCreateCategory(context, mainScreen,
                KEY_CATEGORY_ALLOWED, R.string.allowed_header);
        PreferenceCategory deniedCategory = findOrCreateCategory(context, mainScreen,
                KEY_CATEGORY_DENIED, R.string.denied_header);
        updateCategories(context, allowedCategory, deniedCategory, mainScreenApps);


        Preference showSystemAppPref = mainScreen.findPreference(KEY_SHOW_SYSTEM_PREFS);
        if (mExtraScreen != null) {
            // Updating "system" screen categories
            allowedCategory = findOrCreateCategory(context, mExtraScreen, KEY_CATEGORY_ALLOWED,
                    R.string.allowed_header);
            deniedCategory = findOrCreateCategory(context, mExtraScreen, KEY_CATEGORY_DENIED,
                    R.string.denied_header);
            updateCategories(context, allowedCategory, deniedCategory, systemScreenApps);

            // Setting "Show system apps" button on the "main" screen.
            if (showSystemAppPref == null) {
                showSystemAppPref = new Preference(context);
                showSystemAppPref.setKey(KEY_SHOW_SYSTEM_PREFS);
                showSystemAppPref.setIcon(Utils.applyTint(context, R.drawable.ic_toc,
                        android.R.attr.colorControlNormal));
                showSystemAppPref.setTitle(R.string.preference_show_system_apps);
                showSystemAppPref.setOnPreferenceClickListener(this);

                mainScreen.addPreference(showSystemAppPref);
            }

            int allowedSystemApps = allowedCategory.findPreference(KEY_NO_APPS_ALLOWED) != null ? 0
                    : allowedCategory.getPreferenceCount();
            showSystemAppPref.setSummary(getString(R.string.app_permissions_group_summary,
                    allowedSystemApps, systemScreenApps.size()));
        } else if (showSystemAppPref != null) {
            // There are not system apps, but there is a "Show system apps" button: remove the
            // button.
            mainScreen.removePreference(showSystemAppPref);
        }

        setLoading(false /* loading */, true /* animate */);

        if (mOnPermissionsLoadedListener != null) {
            mOnPermissionsLoadedListener.onPermissionsLoaded(permissionApps);
        }

        if (mHasSystemApps) {
            getActivity().invalidateOptionsMenu();
        }
    }

    private void updateCategories(
            Context context,
            PreferenceCategory allowedCategory,
            PreferenceCategory deniedCategory,
            ArraySet<PermissionApp> apps) {
        ArraySet<String> toRemoveFromAllowed = getAllPreferencesKeys(allowedCategory);
        ArraySet<String> toRemoveFromDenied = getAllPreferencesKeys(deniedCategory);

        boolean hasAllowed = false;
        boolean hasDenied = false;

        for (int i = 0, n = apps.size(); i < n; i++) {
            PermissionApp app = apps.valueAt(i);
            String key = app.getKey();

            // One-time granted permissions should appear in the "Denied" category
            boolean isAllowed =
                    app.areRuntimePermissionsGranted() && !app.getPermissionGroup().isOneTime();

            if (isAllowed) {
                hasAllowed = true;
                if (toRemoveFromAllowed.remove(key)) {
                    // Already have this app in the right category.
                    continue;
                }
            } else {
                hasDenied = true;
                if (toRemoveFromDenied.remove(key)) {
                    // Already have this app in the right category.
                    continue;
                }
            }

            PreferenceCategory rightCategory = isAllowed ? allowedCategory : deniedCategory;
            PreferenceCategory otherCategory = isAllowed ? deniedCategory : allowedCategory;

            // Check if we have the app in the wrong category.
            Preference preference = otherCategory.findPreference(key);
            if (preference != null) {
                // Remove from the wrong category, before adding to the right one.
                otherCategory.removePreference(preference);
                // We are leaving the key in toRemoveFrom* list, that's fine.
            } else {
                preference = new Preference(context);
                preference.setOnPreferenceClickListener(this);
                preference.setKey(app.getKey());
                preference.setIcon(app.getIcon());
                preference.setTitle(app.getLabel());
                if (app.isSystemFixed()) {
                    preference.setSummary(
                            getString(R.string.permission_summary_enabled_system_fixed));
                } else if (app.isPolicyFixed()) {
                    preference.setSummary(
                            getString(R.string.permission_summary_enforced_by_policy));
                }
                preference.setPersistent(false);
                preference.setEnabled(!app.isSystemFixed() && !app.isPolicyFixed());
            }

            // Add to the right category.
            rightCategory.addPreference(preference);
        }

        if (!hasAllowed) {
            if (!toRemoveFromAllowed.remove(KEY_NO_APPS_ALLOWED)) {
                // Did not have "No apps allowed" label, adding.
                Preference preference = new Preference(context);
                preference.setKey(KEY_NO_APPS_ALLOWED);
                preference.setLayoutResource(R.layout.preference_permissions_no_apps);
                preference.setTitle(R.string.no_apps_allowed);
                preference.setEnabled(false);

                allowedCategory.addPreference(preference);
            }
        }

        if (!hasDenied) {
            if (!toRemoveFromDenied.remove(KEY_NO_APPS_DENIED)) {
                // Did not have "No apps denied" label, adding.
                Preference preference = new Preference(context);
                preference.setKey(KEY_NO_APPS_DENIED);
                preference.setLayoutResource(R.layout.preference_permissions_no_apps);
                preference.setTitle(R.string.no_apps_denied);
                preference.setEnabled(false);

                deniedCategory.addPreference(preference);
            }
        }

        removePreferences(allowedCategory, toRemoveFromAllowed);
        removePreferences(deniedCategory, toRemoveFromDenied);
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        final String key = preference.getKey();

        if (KEY_SHOW_SYSTEM_PREFS.equals(key)) {
            SystemAppsFragment frag = new SystemAppsFragment();
            setPermissionName(frag, getArguments().getString(
                    Intent.EXTRA_PERMISSION_NAME));
            frag.setTargetFragment(this, 0);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, frag)
                    .addToBackStack("SystemApps")
                    .commit();
            return true;
        }

        final PermissionApp app = mPermissionApps.getApp(key);

        if (LocationUtils.isLocationGroupAndProvider(getContext(), mPermissionApps.getGroupName(),
                app.getPackageName())) {
            LocationUtils.showLocationDialog(getContext(), app.getLabel());
            return true;
        }

        addToggledGroup(app.getPackageName(), app.getPermissionGroup());

        if (app.isReviewRequired()) {
            Intent intent = new Intent(getActivity(), ReviewPermissionsActivity.class);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, app.getPackageName());
            startActivity(intent);
            return true;
        }

        final AppPermissionFragment frag = new AppPermissionFragment();
        frag.setArguments(AppPermissionFragment.createArgs(
                /* packageName= */ app.getPackageName(),
                /* permName= */ null,
                /* groupName= */ app.getPermissionGroup().getName(),
                /* userHandle= */ app.getPermissionGroup().getUser(),
                /* caller= */ null,
                /* sessionId= */ INVALID_SESSION_ID,
                /* grantCategory= */ null));
        frag.setTargetFragment(this, 0);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack(null)
                .commit();

        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        logToggledGroups();
    }

    private PreferenceCategory findOrCreateCategory(
            Context context, PreferenceScreen screen, String key, int titleResId) {
        PreferenceCategory category = screen.findPreference(key);
        if (category == null) {
            category = new PreferenceCategory(context);
            category.setKey(key);
            category.setTitle(titleResId);

            screen.addPreference(category);
        }
        return category;
    }

    private ArraySet<String> getAllPreferencesKeys(PreferenceGroup group) {
        ArraySet<String> keys = new ArraySet<>();
        if (group != null) {
            for (int i = 0, n = group.getPreferenceCount(); i < n; i++) {
                keys.add(group.getPreference(i).getKey());
            }
        }
        return keys;
    }

    private void removePreferences(PreferenceGroup group, ArraySet<String> keys) {
        if (group == null || keys == null) {
            return;
        }
        for (int i = 0, n = keys.size(); i < n; i++) {
            Preference preference = group.findPreference(keys.valueAt(i));
            if (preference != null) {
                group.removePreference(preference);
            }
        }
    }

    private void showWarningIfNeeded(PermissionApp app) {
        final boolean grantedByDefault = app.hasGrantedByDefaultPermissions();
        if (grantedByDefault || (!app.doesSupportRuntimePermissions()
                && !mHasConfirmedRevoke)) {
            new AlertDialog.Builder(getContext())
                    .setMessage(grantedByDefault ? R.string.system_warning
                            : R.string.old_sdk_deny_warning)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (dialog, which) -> {
                                app.revokeRuntimePermissions();
                                if (!grantedByDefault) {
                                    mHasConfirmedRevoke = true;
                                }
                            })
                    .show();
        } else {
            app.revokeRuntimePermissions();
        }
    }

    private void addToggledGroup(String packageName, AppPermissionGroup group) {
        if (mToggledGroups == null) {
            mToggledGroups = new ArraySet<>();
        }

        mToggledGroups.add(group);
    }

    private void logToggledGroups() {
        if (mToggledGroups != null) {
            SafetyNetLogger.logPermissionsToggled(mToggledGroups);
            mToggledGroups = null;
        }
    }

    public static class SystemAppsFragment extends SettingsWithHeader implements Callback {
        PermissionAppsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mOuterFragment = (PermissionAppsFragment) getTargetFragment();
            setLoading(true /* loading */, false /* animate */);
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
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
            PermissionApps permissionApps = new PermissionApps(getActivity(), groupName,
                    (Callback) null);
            bindUi(this, permissionApps);
        }

        @Override
        public void onResume() {
            super.onResume();
            mOuterFragment.mPermissionApps.refresh(true);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mOuterFragment.setOnPermissionsLoadedListener(null);
        }


        private static void bindUi(SettingsWithHeader fragment, PermissionApps permissionApps) {
            final CharSequence label = permissionApps.getLabel();
            fragment.setHeader(null, null, null,
                    fragment.getString(R.string.system_apps_decor_title, label));
        }

        @Override
        public void onPermissionsLoaded(PermissionApps permissionApps) {
            setPreferenceScreen();
        }

        private void setPreferenceScreen() {
            setPreferenceScreen(mOuterFragment.mExtraScreen);
            setLoading(false /* loading */, true /* animate */);
        }
    }
}
