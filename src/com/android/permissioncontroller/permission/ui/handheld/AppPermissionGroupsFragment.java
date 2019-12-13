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
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__DENIED;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
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

import kotlin.Triple;

/**
 * Show and manage permission groups for an app.
 *
 * <p>Shows the list of permission groups the app has requested at one permission for.
 */
public final class AppPermissionGroupsFragment extends SettingsWithLargeHeader {

    private static final String LOG_TAG = "ManagePermsFragment";
    private static final String IS_SYSTEM_PERMS_SCREEN = "_is_system_screen";

    static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";

    private AppPermissionGroupsViewModel mViewModel;
    private boolean mIsSystemPermsScreen;
    private String mPackageName;
    private UserHandle mUser;

    private Collator mCollator;

    /**
     * @return A new fragment
     */
    public static AppPermissionGroupsFragment newInstance(@NonNull String packageName,
            @NonNull UserHandle userHandle, long sessionId) {
        AppPermissionGroupsFragment fragment = new AppPermissionGroupsFragment();
        fragment.setArguments(createArgs(packageName, userHandle, sessionId, true));
        return setPackageNameAndUserHandleAndSessionId(
                new AppPermissionGroupsFragment(), packageName, userHandle, sessionId);
    }

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName The name of the package
     * @param userHandle The user of this package
     * @param sessionId The current session ID
     * @param isSystemPermsScreen Whether or not this screen is the system permission screen, or
     * the extra permissions screen
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName, @NonNull UserHandle userHandle,
            long sessionId, boolean isSystemPermsScreen) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putBoolean(IS_SYSTEM_PERMS_SCREEN, isSystemPermsScreen);
        return arguments;
    }

    private static <T extends Fragment> T setPackageNameAndUserHandleAndSessionId(
            @NonNull T fragment, @NonNull String packageName, @NonNull UserHandle userHandle,
            long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mIsSystemPermsScreen = getArguments().getBoolean(IS_SYSTEM_PERMS_SCREEN, true);

        AppPermissionGroupsViewModelFactory factory = new AppPermissionGroupsViewModelFactory(
                getActivity().getApplication(), mPackageName, mUser);

        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionGroupsViewModel.class);
        mViewModel.getPackagePermGroupsLiveData().observe(this, this::updatePreferences);

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        if (mViewModel.getPackagePermGroupsLiveData().getValue() != null) {
            updatePreferences(mViewModel.getPackagePermGroupsLiveData().getValue());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        getActivity().setTitle(R.string.app_permissions);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                getActivity().onBackPressed();
                return true;
            }

            case MENU_ALL_PERMS: {
                mViewModel.showAllPermissions(this);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mIsSystemPermsScreen) {
            menu.add(Menu.NONE, MENU_ALL_PERMS, Menu.NONE, R.string.all_permissions);
            HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_app_permissions,
                    getClass().getName());
        }
    }

    private static void bindUi(SettingsWithLargeHeader fragment, String packageName,
            UserHandle user) {
        Activity activity = fragment.getActivity();
        Intent infoIntent = null;
        if (!activity.getIntent().getBooleanExtra(EXTRA_HIDE_INFO_BUTTON, false)) {
            infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null));
        }

        Drawable icon = KotlinUtils.INSTANCE.getBadgedPackageIcon(activity.getApplication(),
                packageName, user);
        fragment.setHeader(icon, KotlinUtils.INSTANCE.getPackageLabel(activity.getApplication(),
                packageName, user), infoIntent, user, false);

    }

    private void updatePreferences(Map<Category, List<Triple<String, Boolean, Boolean>>> groupMap) {
        if (getPreferenceScreen() == null) {
            addPreferencesFromResource(R.xml.allowed_denied);
            logAppPermissionsFragmentView();
            bindUi(this, mPackageName, mUser);
        }

        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        if (groupMap == null && mViewModel.getPackagePermGroupsLiveData().isInitialized()) {
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            getActivity().finish();
            return;
        }

        findPreference(Category.ALLOWED_FOREGROUND.getCategoryName()).setVisible(false);

        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        for (Category grantCategory : groupMap.keySet()) {
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());
            int numExtraPerms = 0;

            category.removeAll();

            if (grantCategory.equals(Category.ALLOWED_FOREGROUND)) {
                category.setVisible(false);
                category = findPreference(Category.ALLOWED.getCategoryName());
            }

            for (Triple<String, Boolean, Boolean> groupTriple : groupMap.get(grantCategory)) {
                String groupName = groupTriple.getFirst();
                boolean isSystem = groupTriple.getSecond();
                boolean isForegroundOnly = groupTriple.getThird();

                PermissionControlPreference preference = new PermissionControlPreference(context,
                        mPackageName, groupName, mUser, AppPermissionGroupsFragment.class.getName(),
                        sessionId, grantCategory);
                preference.setTitle(KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName));
                preference.setIcon(KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName));
                preference.setKey(preference.getTitle().toString());
                if (isForegroundOnly) {
                    preference.setSummary(R.string.permission_subtitle_only_in_foreground);
                }
                if (isSystem == mIsSystemPermsScreen) {
                    category.addPreference(preference);
                } else if (!isSystem) {
                    numExtraPerms++;
                }
            }

            int noPermsStringRes = grantCategory.equals(Category.DENIED)
                    ? R.string.no_permissions_denied : R.string.no_permissions_allowed;

            if (numExtraPerms > 0) {
                final Preference extraPerms = setUpCustomPermissionsScreen(context, numExtraPerms,
                        grantCategory.getCategoryName());
                category.addPreference(extraPerms);
            }

            if (category.getPreferenceCount() == 0) {
                setNoPermissionPreference(category, noPermsStringRes, context);
            }

            KotlinUtils.INSTANCE.sortPreferenceGroup(category, false,
                    this::comparePreferences);
        }
    }

    private int comparePreferences(Preference lhs, Preference rhs) {
        String additionalTitle = lhs.getContext().getString(R.string.additional_permissions);
        if (lhs.getTitle().equals(additionalTitle)) {
            return 1;
        } else if (rhs.getTitle().equals(additionalTitle)) {
            return -1;
        }
        return mCollator.compare(lhs.getTitle().toString(),
                rhs.getTitle().toString());
    }

    private Preference setUpCustomPermissionsScreen(Context context, int count, String category) {
        final Preference extraPerms = new Preference(context);
        extraPerms.setIcon(Utils.applyTint(getActivity(), R.drawable.ic_toc,
                android.R.attr.colorControlNormal));
        extraPerms.setTitle(R.string.additional_permissions);
        extraPerms.setKey(extraPerms.getTitle() + category);
        extraPerms.setOnPreferenceClickListener(preference -> {
            mViewModel.showExtraPerms(this, getArguments().getLong(EXTRA_SESSION_ID));
            return true;
        });
        extraPerms.setSummary(getResources().getQuantityString(
                R.plurals.additional_permissions_more, count, count));
        return extraPerms;
    }

    private void setNoPermissionPreference(PreferenceCategory category, @StringRes int stringId,
            Context context) {
        Preference empty = new Preference(context);
        empty.setKey(getString(stringId));
        empty.setTitle(empty.getKey());
        empty.setSelectable(false);
        category.addPreference(empty);
    }

    private void logAppPermissionsFragmentView() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }
        String permissionSubtitleOnlyInForeground =
                context.getString(R.string.permission_subtitle_only_in_foreground);


        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        long viewId = new Random().nextLong();

        PreferenceCategory allowed = findPreference(Category.ALLOWED.getCategoryName());

        int numAllowed = allowed.getPreferenceCount();
        for (int i = 0; i < numAllowed; i++) {
            Preference preference = allowed.getPreference(i);

            if (preference.getSummary() == null) {
                // R.string.no_permission_allowed was added to PreferenceCategory
                continue;
            }

            int category = APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
            if (permissionSubtitleOnlyInForeground.contentEquals(preference.getSummary())) {
                category = APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
            }

            logAppPermissionsFragmentViewEntry(sessionId, viewId, preference.getKey(), category);
        }

        PreferenceCategory denied = findPreference(Category.DENIED.getCategoryName());

        int numDenied = denied.getPreferenceCount();
        for (int i = 0; i < numDenied; i++) {
            Preference preference = denied.getPreference(i);
            if (preference.getSummary() == null) {
                // R.string.no_permission_denied was added to PreferenceCategory
                continue;
            }
            logAppPermissionsFragmentViewEntry(sessionId, viewId, preference.getKey(),
                    APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__DENIED);
        }
    }

    private void logAppPermissionsFragmentViewEntry(
            long sessionId, long viewId, String permissionGroupName, int category) {

        Integer uid = KotlinUtils.INSTANCE.getPackageUid(getActivity().getApplication(),
                mPackageName, mUser);
        if (uid == null) {
            return;
        }
        PermissionControllerStatsLog.write(APP_PERMISSIONS_FRAGMENT_VIEWED, sessionId, viewId,
                permissionGroupName, uid, mPackageName, category);
        Log.v(LOG_TAG, "AppPermissionFragment view logged with sessionId=" + sessionId + " viewId="
                + viewId + " permissionGroupName=" + permissionGroupName + " uid="
                + uid + " packageName="
                + mPackageName + " category=" + category);
    }
}
