/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.permission.ui.auto;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.packageinstaller.auto.AutoSettingsFrameFragment;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.Callback;
import com.android.packageinstaller.permission.ui.handheld.PermissionAppsFragment;
import com.android.packageinstaller.permission.ui.handheld.PermissionControlPreference;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Map;

/** Shows the list of applications which have (or do not have) the given permission. */
public class AutoPermissionAppsFragment extends AutoSettingsFrameFragment implements Callback {

    private static final String KEY_SHOW_SYSTEM_PREFS = "_showSystem";
    private static final String KEY_ALLOWED_PERMISSIONS_GROUP = "allowed_permissions_group";
    private static final String KEY_ALLOWED_FOREGROUND_PERMISSIONS_GROUP =
            "allowed_foreground_permissions_group";
    private static final String KEY_DENIED_PERMISSIONS_GROUP = "denied_permissions_group";

    private static final String SHOW_SYSTEM_KEY = AutoPermissionAppsFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;

    /** Creates a new instance of {@link AutoPermissionAppsFragment} for the given permission. */
    public static AutoPermissionAppsFragment newInstance(String permissionName) {
        return setPermissionName(new AutoPermissionAppsFragment(), permissionName);
    }

    private static <T extends Fragment> T setPermissionName(T fragment, String permissionName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        fragment.setArguments(arguments);
        return fragment;
    }

    private PermissionApps mPermissionApps;

    private boolean mShowSystem;
    private boolean mHasSystemApps;

    private Collator mCollator;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
        }

        setLoading(true);

        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        mPermissionApps = new PermissionApps(getActivity(), groupName, /* callback= */ this);
        mPermissionApps.refresh(/* getUiInfo= */ true);

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        setShowSystemAppsToggle();
        bindUi(mPermissionApps, groupName);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
    }

    @Override
    public void onStart() {
        super.onStart();
        mPermissionApps.refresh(/* getUiInfo= */ true);
    }

    private void setShowSystemAppsToggle() {
        if (!mHasSystemApps) {
            setAction(/* label= */ null, /* onClickListener= */ null);
            return;
        }

        // Show the opposite label from the current state.
        String label;
        if (mShowSystem) {
            label = getString(R.string.menu_hide_system);
        } else {
            label = getString(R.string.menu_show_system);
        }

        setAction(label, v -> {
            mShowSystem = !mShowSystem;
            if (mPermissionApps.getApps() != null) {
                onPermissionsLoaded(mPermissionApps);
            }
            setShowSystemAppsToggle();
        });
    }

    private void bindUi(PermissionApps permissionApps, @NonNull String groupName) {
        CharSequence label = permissionApps.getFullLabel();
        setHeaderLabel(label);

        Drawable icon = permissionApps.getIcon();
        Preference header = new Preference(getContext());
        header.setTitle(label);
        header.setIcon(icon);
        header.setSummary(Utils.getPermissionGroupDescriptionString(getContext(), groupName,
                permissionApps.getDescription()));
        getPreferenceScreen().addPreference(header);

        PreferenceGroup allowed = new PreferenceCategory(getContext());
        allowed.setKey(KEY_ALLOWED_PERMISSIONS_GROUP);
        allowed.setTitle(R.string.allowed_header);
        allowed.setVisible(false);
        getPreferenceScreen().addPreference(allowed);

        PreferenceGroup foreground = new PreferenceCategory(getContext());
        foreground.setKey(KEY_ALLOWED_FOREGROUND_PERMISSIONS_GROUP);
        foreground.setTitle(R.string.allowed_foreground_header);
        foreground.setVisible(false);
        getPreferenceScreen().addPreference(foreground);

        PreferenceGroup denied = new PreferenceCategory(getContext());
        denied.setKey(KEY_DENIED_PERMISSIONS_GROUP);
        denied.setTitle(R.string.denied_header);
        denied.setVisible(false);
        getPreferenceScreen().addPreference(denied);
    }

    @Override
    public void onPermissionsLoaded(PermissionApps permissionApps) {
        Context context = getPreferenceManager().getContext();

        if (context == null || getActivity() == null) {
            return;
        }

        PreferenceCategory allowed = findPreference(KEY_ALLOWED_PERMISSIONS_GROUP);
        PreferenceCategory allowedForeground = findPreference(
                KEY_ALLOWED_FOREGROUND_PERMISSIONS_GROUP);
        PreferenceCategory denied = findPreference(KEY_DENIED_PERMISSIONS_GROUP);

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

        mHasSystemApps = false;
        boolean hasPermissionWithBackgroundMode = false;

        ArrayList<PermissionApps.PermissionApp> sortedApps = new ArrayList<>(
                permissionApps.getApps());
        sortedApps.sort((x, y) -> {
            int result = mCollator.compare(x.getLabel(), y.getLabel());
            if (result == 0) {
                result = x.getUid() - y.getUid();
            }
            return result;
        });

        for (int i = 0; i < sortedApps.size(); i++) {
            PermissionApps.PermissionApp app = sortedApps.get(i);
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

            if (isSystemApp) {
                mHasSystemApps = true;
            }

            if (isSystemApp && !mShowSystem) {
                continue;
            }

            PreferenceCategory category;
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
                    PermissionAppsFragment.class.getName());
            pref.setKey(key);
            pref.setIcon(app.getIcon());
            pref.setTitle(Utils.getFullAppLabel(app.getAppInfo(), context));
            pref.setEllipsizeEnd();
            pref.useSmallerIcon();
            category.addPreference(pref);
        }

        if (hasPermissionWithBackgroundMode) {
            allowed.setTitle(R.string.allowed_always_header);
        }

        if (allowed.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(R.string.no_apps_allowed);
            empty.setSelectable(false);
            allowed.addPreference(empty);
        }
        allowed.setVisible(true);

        allowedForeground.setVisible(allowedForeground.getPreferenceCount() > 0);

        if (denied.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(R.string.no_apps_denied);
            empty.setSelectable(false);
            denied.addPreference(empty);
        }
        denied.setVisible(true);

        setShowSystemAppsToggle();
        setLoading(false);
    }
}
