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
package com.android.permissioncontroller.permission.ui.handheld;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__DENIED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_APPS_FRAGMENT_VIEWED__CATEGORY__UNDEFINED;
import static com.android.permissioncontroller.permission.ui.Category.ALLOWED;
import static com.android.permissioncontroller.permission.ui.Category.ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.permission.ui.Category.ASK;
import static com.android.permissioncontroller.permission.ui.Category.DENIED;

import android.Manifest;
import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.Category;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.HelpUtils;

import java.text.Collator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kotlin.Pair;

/**
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
public final class PermissionAppsFragment extends SettingsWithLargeHeader {

    private static final String KEY_SHOW_SYSTEM_PREFS = "_showSystem";
    private static final String CREATION_LOGGED_SYSTEM_PREFS = "_creationLogged";
    private static final String KEY_FOOTER = "_footer";
    private static final String KEY_EMPTY = "_empty";
    private static final String LOG_TAG = "PermissionAppsFragment";
    private static final String STORAGE_ALLOWED_FULL = "allowed_storage_full";
    private static final String STORAGE_ALLOWED_SCOPED = "allowed_storage_scoped";
    private static final int SHOW_LOAD_DELAY_MS = 200;

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

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param permGroupName The name of the permission group
     * @param sessionId     The current session ID
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(String permGroupName, long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, permGroupName);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        return arguments;
    }


    private static <T extends Fragment> T setPermissionNameAndSessionId(
            T fragment, String permissionName, long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }

    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private String mPermGroupName;
    private Collator mCollator;
    private PermissionAppsViewModel mViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        PermissionAppsViewModelFactory factory =
                new PermissionAppsViewModelFactory(getActivity().getApplication(), mPermGroupName,
                        this, new Bundle());
        mViewModel = new ViewModelProvider(this, factory).get(PermissionAppsViewModel.class);

        mViewModel.getCategorizedAppsLiveData().observe(this, this::onPackagesLoaded);
        mViewModel.getShouldShowSystemLiveData().observe(this, this::updateMenu);
        mViewModel.getHasSystemAppsLiveData().observe(this, (Boolean hasSystem) ->
                getActivity().invalidateOptionsMenu());

        if (!mViewModel.arePackagesLoaded()) {
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                if (!mViewModel.arePackagesLoaded()) {
                    setLoading(true /* loading */, false /* animate */);
                }
            }, SHOW_LOAD_DELAY_MS);
        } else if (mViewModel.getCategorizedAppsLiveData().getValue() != null) {
            onPackagesLoaded(mViewModel.getCategorizedAppsLiveData().getValue());
        }

        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mViewModel.getHasSystemAppsLiveData().getValue()) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
            updateMenu(mViewModel.getShouldShowSystemLiveData().getValue());
        }

        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_app_permissions,
                getClass().getName());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mViewModel.updateShowSystem(false);
                getActivity().onBackPressed();
                return true;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mViewModel.updateShowSystem(item.getItemId() == MENU_SHOW_SYSTEM);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMenu(Boolean showSystem) {
        if (showSystem == null) {
            showSystem = false;
        }
        if (mShowSystemMenu != null && mHideSystemMenu != null) {
            mShowSystemMenu.setVisible(!showSystem);
            mHideSystemMenu.setVisible(showSystem);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi(this, mPermGroupName);
    }

    private static void bindUi(SettingsWithLargeHeader fragment, @NonNull String groupName) {
        Context context = fragment.getContext();
        if (context == null || fragment.getActivity() == null) {
            return;
        }
        Drawable icon = KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName);

        CharSequence label = KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName);
        CharSequence description = KotlinUtils.INSTANCE.getPermGroupDescription(context, groupName);

        fragment.setHeader(icon, label, null, null, true);
        fragment.setSummary(Utils.getPermissionGroupDescriptionString(fragment.getActivity(),
                groupName, description), null);
        fragment.getActivity().setTitle(label);
    }

    private void onPackagesLoaded(Map<Category, List<Pair<String, UserHandle>>> categories) {
        boolean isStorage = mPermGroupName.equals(Manifest.permission_group.STORAGE);
        if (getPreferenceScreen() == null) {
            if (isStorage) {
                addPreferencesFromResource(R.xml.allowed_denied_storage);
            } else {
                addPreferencesFromResource(R.xml.allowed_denied);
            }
            // Hide allowed foreground label by default, to avoid briefly showing it before updating
            findPreference(ALLOWED_FOREGROUND.getCategoryName()).setVisible(false);
        }
        Context context = getPreferenceManager().getContext();

        if (context == null || getActivity() == null || categories == null) {
            return;
        }

        Map<String, Preference> existingPrefs = new ArrayMap<>();

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            PreferenceCategory category = (PreferenceCategory)
                    getPreferenceScreen().getPreference(i);
            category.setOrderingAsAdded(true);
            int numPreferences = category.getPreferenceCount();
            for (int j = 0; j < numPreferences; j++) {
                Preference preference = category.getPreference(j);
                existingPrefs.put(preference.getKey(), preference);
            }
            category.removeAll();
        }

        long viewIdForLogging = new Random().nextLong();
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        Boolean showAlways = mViewModel.getShowAllowAlwaysStringLiveData().getValue();
        if (!isStorage) {
            if (showAlways != null && showAlways) {
                findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_always_header);
            } else {
                findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_header);
            }
        }

        for (Category grantCategory : categories.keySet()) {
            List<Pair<String, UserHandle>> packages = categories.get(grantCategory);
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());


            // If this category is empty, and this isn't the "allowed" category of the storage
            // permission, set up the empty preference.
            if (packages.size() == 0 && (!isStorage || !grantCategory.equals(ALLOWED))) {
                Preference empty = new Preference(context);
                empty.setSelectable(false);
                empty.setKey(category.getKey() + KEY_EMPTY);
                if (grantCategory.equals(ALLOWED)) {
                    empty.setTitle(getString(R.string.no_apps_allowed));
                } else if (grantCategory.equals(ALLOWED_FOREGROUND)) {
                    category.setVisible(false);
                } else if (grantCategory.equals(ASK)) {
                    category.setVisible(false);
                } else {
                    empty.setTitle(getString(R.string.no_apps_denied));
                }
                category.addPreference(empty);
                continue;
            } else if (grantCategory.equals(ALLOWED_FOREGROUND)) {
                category.setVisible(true);
            } else if (grantCategory.equals(ASK)) {
                category.setVisible(true);
            }

            for (Pair<String, UserHandle> packageUserLabel : packages) {
                String packageName = packageUserLabel.getFirst();
                UserHandle user = packageUserLabel.getSecond();

                String key = user + packageName;

                if (isStorage && grantCategory.equals(ALLOWED)) {
                    category = mViewModel.shouldUseFullStorageString(packageName, user)
                            ? findPreference(STORAGE_ALLOWED_FULL)
                            : findPreference(STORAGE_ALLOWED_SCOPED);
                }

                Preference existingPref = existingPrefs.get(key);
                if (existingPref != null) {
                    if (existingPref instanceof SmartIconLoadPackagePermissionPreference) {
                        ((SmartIconLoadPackagePermissionPreference) existingPref).setGrantCategory(
                                grantCategory.getCategoryName());
                    }
                    category.addPreference(existingPref);
                    continue;
                }

                SmartIconLoadPackagePermissionPreference pref =
                        new SmartIconLoadPackagePermissionPreference(getActivity().getApplication(),
                                packageName, user, context, mPermGroupName,
                                PermissionAppsFragment.class.getName(), sessionId,
                                grantCategory.getCategoryName());
                pref.setKey(key);
                pref.setTitle(KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(),
                        packageName, user));

                category.addPreference(pref);
                if (!mViewModel.getCreationLogged()) {
                    logPermissionAppsFragmentCreated(packageName, user, viewIdForLogging,
                            grantCategory.equals(ALLOWED), grantCategory.equals(ALLOWED_FOREGROUND),
                            grantCategory.equals(DENIED));
                }
            }

            if (isStorage && grantCategory.equals(ALLOWED)) {
                PreferenceCategory full = findPreference(STORAGE_ALLOWED_FULL);
                PreferenceCategory scoped = findPreference(STORAGE_ALLOWED_SCOPED);
                if (full.getPreferenceCount() == 0) {
                    Preference empty = new Preference(context);
                    empty.setSelectable(false);
                    empty.setKey(STORAGE_ALLOWED_FULL + KEY_EMPTY);
                    empty.setTitle(getString(R.string.no_apps_allowed_full));
                    full.addPreference(empty);
                }

                if (scoped.getPreferenceCount() == 0) {
                    Preference empty = new Preference(context);
                    empty.setSelectable(false);
                    empty.setKey(STORAGE_ALLOWED_FULL + KEY_EMPTY);
                    empty.setTitle(getString(R.string.no_apps_allowed_scoped));
                    scoped.addPreference(empty);
                }
                KotlinUtils.INSTANCE.sortPreferenceGroup(full, false, this::comparePreference);
                KotlinUtils.INSTANCE.sortPreferenceGroup(scoped, false, this::comparePreference);
            } else {
                KotlinUtils.INSTANCE.sortPreferenceGroup(category, false, this::comparePreference);
            }

            mViewModel.setCreationLogged(true);
        }

        setLoading(false /* loading */, true /* animate */);
    }

    private int comparePreference(Preference lhs, Preference rhs) {
        int result = mCollator.compare(lhs.getTitle().toString(),
                rhs.getTitle().toString());
        if (result == 0) {
            result = lhs.getKey().compareTo(rhs.getKey());
        }
        return result;
    }

    private void logPermissionAppsFragmentCreated(String packageName, UserHandle user, long viewId,
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

        Integer uid = KotlinUtils.INSTANCE.getPackageUid(getActivity().getApplication(),
                packageName, user);
        if (uid == null) {
            return;
        }

        PermissionControllerStatsLog.write(PERMISSION_APPS_FRAGMENT_VIEWED, sessionId, viewId,
                mPermGroupName, uid, packageName, category);
        Log.v(LOG_TAG, "PermissionAppsFragment created with sessionId=" + sessionId
                + " permissionGroupName=" + mPermGroupName + " appUid="
                + uid + " packageName=" + packageName
                + " category=" + category);
    }
}
