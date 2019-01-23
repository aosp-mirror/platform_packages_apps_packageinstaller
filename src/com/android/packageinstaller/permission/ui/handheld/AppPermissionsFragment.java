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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;

import java.text.Collator;
import java.util.ArrayList;

/**
 * Show and manage permission groups for an app.
 *
 * <p>Shows the list of permission groups the app has requested at one permission for.
 */
public final class AppPermissionsFragment extends SettingsWithButtonHeader {

    private static final String LOG_TAG = "ManagePermsFragment";

    static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";

    private AppPermissions mAppPermissions;
    private PreferenceScreen mExtraScreen;

    private Collator mCollator;

    public static AppPermissionsFragment newInstance(String packageName) {
        return setPackageName(new AppPermissionsFragment(), packageName);
    }

    private static <T extends Fragment> T setPackageName(T fragment, String packageName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true /* loading */, false /* animate */);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        Activity activity = getActivity();
        PackageInfo packageInfo = getPackageInfo(activity, packageName);
        if (packageInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        addPreferencesFromResource(R.xml.allowed_denied);

        mAppPermissions = new AppPermissions(activity, packageInfo, true, new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        });

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));
        updatePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppPermissions.refresh();
        updatePreferences();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                getActivity().finish();
                return true;
            }

            case MENU_ALL_PERMS: {
                showAllPermissions(null);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mAppPermissions != null) {
            bindUi(this, mAppPermissions.getPackageInfo());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_ALL_PERMS, Menu.NONE, R.string.all_permissions);
        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_app_permissions,
                getClass().getName());
    }

    private void showAllPermissions(String filterGroup) {
        Fragment frag = AllAppPermissionsFragment.newInstance(
                getArguments().getString(Intent.EXTRA_PACKAGE_NAME),
                filterGroup);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    private static void bindUi(SettingsWithButtonHeader fragment, PackageInfo packageInfo) {
        Activity activity = fragment.getActivity();
        ApplicationInfo appInfo = packageInfo.applicationInfo;

        Drawable icon = Utils.getBadgedIcon(activity, appInfo);
        fragment.setHeader(icon, Utils.getFullAppLabel(appInfo, activity), true);

        ActionBar ab = activity.getActionBar();
        if (ab != null) {
            ab.setTitle(R.string.app_permissions);
        }
    }

    private void updatePreferences() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        PreferenceCategory allowed = (PreferenceCategory) findPreference("allowed");
        PreferenceCategory denied = (PreferenceCategory) findPreference("denied");

        allowed.removeAll();
        denied.removeAll();

        findPreference("allowed_foreground").setVisible(false);

        if (mExtraScreen != null) {
            mExtraScreen.removeAll();
        }

        final Preference extraPerms = new Preference(context);
        extraPerms.setIcon(R.drawable.ic_toc);
        extraPerms.setTitle(R.string.additional_permissions);
        boolean extraPermsAreAllowed = false;

        ArrayList<AppPermissionGroup> groups = new ArrayList<>(
                mAppPermissions.getPermissionGroups());
        groups.sort((x, y) -> mCollator.compare(x.getLabel(), y.getLabel()));
        allowed.setOrderingAsAdded(true);
        denied.setOrderingAsAdded(true);

        for (int i = 0; i < groups.size(); i++) {
            AppPermissionGroup group = groups.get(i);
            if (!Utils.shouldShowPermission(getContext(), group)) {
                continue;
            }

            boolean isPlatform = group.getDeclaringPackage().equals(Utils.OS_PKG);

            PermissionControlPreference preference = new PermissionControlPreference(context,
                    group);
            preference.setKey(group.getName());
            Drawable icon = Utils.loadDrawable(context.getPackageManager(),
                    group.getIconPkg(), group.getIconResId());
            preference.setIcon(Utils.applyTint(context, icon,
                    android.R.attr.colorControlNormal));
            preference.setTitle(group.getFullLabel());
            String lastAccessStr = Utils.getAbsoluteLastUsageString(context,
                    PermissionUsages.loadLastGroupUsage(context, group));
            // STOPSHIP: Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
            if (lastAccessStr != null && !group.getLabel().equals("Storage")) {
                preference.setSummary(
                        context.getString(R.string.app_permission_most_recent_summary,
                                lastAccessStr));
            } else {
                preference.setGroupSummary(group);
            }

            if (isPlatform) {
                PreferenceCategory category =
                        group.areRuntimePermissionsGranted() ? allowed : denied;
                category.addPreference(preference);
            } else {
                if (mExtraScreen == null) {
                    mExtraScreen = getPreferenceManager().createPreferenceScreen(context);
                }
                mExtraScreen.addPreference(preference);
                if (group.areRuntimePermissionsGranted()) {
                    extraPermsAreAllowed = true;
                }
            }
        }

        if (mExtraScreen != null) {
            extraPerms.setOnPreferenceClickListener(preference -> {
                AdditionalPermissionsFragment frag = new AdditionalPermissionsFragment();
                setPackageName(frag, getArguments().getString(Intent.EXTRA_PACKAGE_NAME));
                frag.setTargetFragment(AppPermissionsFragment.this, 0);
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, frag)
                        .addToBackStack(null)
                        .commit();
                return true;
            });
            int count = mExtraScreen.getPreferenceCount();
            extraPerms.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, count, count));
            PreferenceCategory category = extraPermsAreAllowed ? allowed : denied;
            category.addPreference(extraPerms);
        }

        if (allowed.getPreferenceCount() > 0) {
            Preference details = new Preference(context);
            details.setTitle(R.string.detailed_usage_link);
            details.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_REVIEW_APP_PERMISSION_USAGE);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME,
                        getArguments().getString(Intent.EXTRA_PACKAGE_NAME));
                intent.putExtra(Intent.EXTRA_USER, UserHandle.getUserHandleForUid(
                        mAppPermissions.getPackageInfo().applicationInfo.uid));
                context.startActivity(intent);
                return true;
            });
            allowed.addPreference(details);

            if (!Utils.isPermissionsHubEnabled()) {
                allowed.removePreference(details);
            }
        } else {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_permissions_allowed));
            allowed.addPreference(empty);
        }
        if (denied.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_permissions_denied));
            denied.addPreference(empty);
        }

        setLoading(false /* loading */, true /* animate */);
    }

    private static PackageInfo getPackageInfo(Activity activity, String packageName) {
        try {
            return activity.getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + activity.getCallingPackage(), e);
            return null;
        }
    }

    /**
     * Class that shows additional permissions.
     */
    public static class AdditionalPermissionsFragment extends SettingsWithButtonHeader {
        AppPermissionsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mOuterFragment = (AppPermissionsFragment) getTargetFragment();
            super.onCreate(savedInstanceState);
            setHeader(mOuterFragment.mIcon, mOuterFragment.mLabel, true);
            setHasOptionsMenu(true);
            setPreferenceScreen(mOuterFragment.mExtraScreen);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
            bindUi(this, getPackageInfo(getActivity(), packageName));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
