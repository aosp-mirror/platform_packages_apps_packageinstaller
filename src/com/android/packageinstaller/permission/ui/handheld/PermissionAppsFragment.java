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
import static com.android.packageinstaller.permission.ui.handheld.PermissionAppsViewModel.Category;
import static com.android.packageinstaller.permission.ui.handheld.PermissionAppsViewModel.Category.ALLOWED;
import static com.android.packageinstaller.permission.ui.handheld.PermissionAppsViewModel.Category.ALLOWED_FOREGROUND;
import static com.android.packageinstaller.permission.ui.handheld.PermissionAppsViewModel.Category.DENIED;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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

import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.data.PackageInfoRepository;
import com.android.packageinstaller.permission.model.livedatatypes.LightPackageInfo;
import com.android.packageinstaller.permission.utils.KotlinUtils;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kotlin.Pair;
import kotlin.Triple;

/**
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
public final class PermissionAppsFragment extends SettingsWithLargeHeader {

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

    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private String mPermGroupName;
    private Collator mCollator;
    private PermissionAppsViewModel mViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);

        PermissionAppsViewModelFactory factory =
                new PermissionAppsViewModelFactory(getActivity().getApplication(), mPermGroupName,
                        this, new Bundle());
        mViewModel = new ViewModelProvider(this, factory).get(PermissionAppsViewModel.class);

        if (mViewModel.getCategorizedAppsLiveData().getValue() == null) {
            setLoading(true /* loading */, false /* animate */);
        }
        mViewModel.getCategorizedAppsLiveData().observe(this, this::onPackagesLoaded);
        mViewModel.getShouldShowSystemLiveData().observe(this, this::updateMenu);
        mViewModel.getHasSystemAppsLiveData().observe(this, (Boolean hasSystem) ->
                getActivity().invalidateOptionsMenu());
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        addPreferencesFromResource(R.xml.allowed_denied);
        // Hide allowed foreground label by default, to avoid briefly showing it before updating
        findPreference(ALLOWED_FOREGROUND.getCategoryName()).setVisible(false);
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
                getActivity().finish();
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
        if (context == null) {
            return;
        }
        Drawable icon = KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName);

        CharSequence label = KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName);
        CharSequence description = KotlinUtils.INSTANCE.getPermGroupDescription(context, groupName);

        fragment.setHeader(icon, label, null, null, true);
        fragment.setSummary(Utils.getPermissionGroupDescriptionString(fragment.getActivity(),
                groupName, description), null);

        final ActionBar ab = fragment.getActivity().getActionBar();
        if (ab != null) {
            ab.setTitle(label);
        }
    }

    private void onPackagesLoaded(Map<Category, List<Pair<String, UserHandle>>> categories) {
        Context context = getPreferenceManager().getContext();

        if (context == null || getActivity() == null || categories == null) {
            return;
        }

        Map<String, Preference> existingPrefs = new ArrayMap<>();

        for (Category grantCategory : categories.keySet()) {
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());
            category.setOrderingAsAdded(true);
            int numPreferences = category.getPreferenceCount();
            for (int i = 0; i < numPreferences; i++) {
                Preference preference = category.getPreference(i);
                existingPrefs.put(preference.getKey(), preference);
            }
            category.removeAll();
        }

        long viewIdForLogging = new Random().nextLong();
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        for (Category grantCategory : categories.keySet()) {
            List<Pair<String, UserHandle>> packages = categories.get(grantCategory);
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());

            if (packages.size() == 0) {
                Preference empty = new Preference(context);
                empty.setSelectable(false);
                if (grantCategory.equals(ALLOWED)) {
                    empty.setTitle(getString(R.string.no_apps_allowed));
                } else if (grantCategory.equals(ALLOWED_FOREGROUND)) {
                    findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_header);
                    category.setVisible(false);
                } else {
                    empty.setTitle(getString(R.string.no_apps_denied));
                }
                category.addPreference(empty);
                continue;
            } else if (grantCategory.equals(ALLOWED_FOREGROUND)) {
                category.setVisible(true);
                findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_always_header);
            }

            ArrayList<Triple<String, UserHandle, String>> packageUserLabelList = new ArrayList<>();
            for (Pair<String, UserHandle> pkg : packages) {
                packageUserLabelList.add(new Triple<>(pkg.getFirst(), pkg.getSecond(),
                        KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(),
                                pkg.getFirst(), pkg.getSecond())));
            }
            packageUserLabelList.sort((x, y) -> {
                int result = mCollator.compare(x.getThird(), y.getThird());
                if (result == 0) {
                    result = x.getSecond().getIdentifier() - y.getSecond().getIdentifier();
                }
                return result;
            });


            for (Triple<String, UserHandle, String> packageUserLabel : packageUserLabelList) {
                String packageName = packageUserLabel.getFirst();
                UserHandle user = packageUserLabel.getSecond();
                String label = packageUserLabel.getThird();

                String key = packageName + user;

                Preference existingPref = existingPrefs.get(key);
                if (existingPref != null) {
                    // Without this, existing preferences remember their old order.
                    existingPref.setOrder(Preference.DEFAULT_ORDER);
                    category.addPreference(existingPref);
                    continue;
                }

                SmartIconLoadPackagePermissionPreference pref =
                        new SmartIconLoadPackagePermissionPreference(getActivity().getApplication(),
                                packageName, user, context, mPermGroupName,
                                PermissionAppsFragment.class.getName(), sessionId);
                pref.setKey(key);
                pref.setTitle(label);

                category.addPreference(pref);
                if (!mViewModel.getCreationLogged()) {
                    logPermissionAppsFragmentCreated(packageName, user, viewIdForLogging,
                            grantCategory.equals(ALLOWED), grantCategory.equals(ALLOWED_FOREGROUND),
                            grantCategory.equals(DENIED));
                }
            }

            mViewModel.setCreationLogged(true);
        }

        if (Utils.isPermissionsHubEnabled() && !Utils.shouldShowPermissionUsage(mPermGroupName)
                && findPreference(KEY_FOOTER) == null) {
            PreferenceCategory footer = new PreferenceCategory(context);
            footer.setKey(KEY_FOOTER);
            getPreferenceScreen().addPreference(footer);
            Preference footerText = new Preference(context);
            footerText.setSummary(context.getString(R.string.app_permission_footer_not_available));
            footerText.setIcon(R.drawable.ic_info_outline);
            footerText.setSelectable(false);
            footer.addPreference(footerText);
        }

        setLoading(false /* loading */, false /* animate */);
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

        int uid;
        LightPackageInfo info = PackageInfoRepository.INSTANCE.getPackageInfoLiveData(
                getActivity().getApplication(), packageName, user).getValue();
        if (info != null) {
            uid = info.getUid();
        } else {
            Context userContext;
            try {
                userContext = Utils.getUserContext(getActivity().getApplication(), user);
                uid = userContext.getPackageManager().getApplicationInfo(packageName, 0).uid;
            } catch (PackageManager.NameNotFoundException e) {
                return;
            }
        }

        PermissionControllerStatsLog.write(PERMISSION_APPS_FRAGMENT_VIEWED, sessionId, viewId,
                mPermGroupName, uid, packageName, category);
        Log.v(LOG_TAG, "PermissionAppsFragment created with sessionId=" + sessionId
                + " permissionGroupName=" + mPermGroupName + " appUid="
                + uid + " packageName=" + packageName
                + " category=" + category);
    }
}
