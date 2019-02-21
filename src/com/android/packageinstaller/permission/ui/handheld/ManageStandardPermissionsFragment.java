/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static java.util.concurrent.TimeUnit.DAYS;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.ui.AppPermissionActivity;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.widget.AppEntitiesHeaderController;
import com.android.settingslib.widget.AppEntityInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that allows the user to manage standard permissions.
 */
public final class ManageStandardPermissionsFragment extends ManagePermissionsFragment {
    private static final String EXTRA_PREFS_KEY = "extra_prefs_key";
    private static final int MAXIMUM_APP_COUNT = 3;

    private @NonNull PermissionUsages mPermissionUsages;
    private @NonNull AppEntitiesHeaderController mAppUsageController;
    private @NonNull ArraySet<String> mLauncherPkgs;

    /**
     * @return A new fragment
     */
    public static ManageStandardPermissionsFragment newInstance() {
        return new ManageStandardPermissionsFragment();
    }


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPermissionUsages = new PermissionUsages(getContext());
        mLauncherPkgs = Utils.getLauncherPackages(getContext());
    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().setTitle(com.android.permissioncontroller.R.string.app_permission_manager);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getPreferenceManager().getContext();
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        View header = inflater.inflate(R.layout.recent_usage_layout, root, false);
        getPreferencesContainer().addView(header, 0);

        View usageView = header.requireViewById(R.id.app_entities_header);
        mAppUsageController = AppEntitiesHeaderController.newInstance(context, usageView)
                .setHeaderTitleRes(R.string.permission_usage_header)
                .setHeaderDetailsRes(R.string.permission_usage_view_details)
                .setHeaderDetailsClickListener((View v) -> {
                    Intent intent = new Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE);
                    intent.putExtra(Intent.EXTRA_DURATION_MILLIS, DAYS.toMillis(1));
                    context.startActivity(intent);
                });

        if (!Utils.isPermissionsHubEnabled()) {
            header.setVisibility(View.GONE);
        }

        mPermissionUsages.load(null, null, System.currentTimeMillis() - DAYS.toMillis(1),
                Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST
                        | PermissionUsages.USAGE_FLAG_HISTORICAL,
                getActivity().getLoaderManager(),
                false, false, this::updateRecentlyUsedWidget, false);

        return root;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void updatePermissionsUi() {
        PreferenceScreen screen = updatePermissionsUi(true);
        if (screen == null) {
            return;
        }

        // Check if we need an additional permissions preference
        List<PermissionGroup> groups = getPermissions().getGroups();
        int numExtraPermissions = 0;
        for (PermissionGroup group : groups) {
            if (!group.getDeclaringPackage().equals(ManagePermissionsFragment.OS_PKG)) {
                numExtraPermissions++;
            }
        }

        Preference additionalPermissionsPreference = screen.findPreference(EXTRA_PREFS_KEY);
        if (numExtraPermissions == 0) {
            if (additionalPermissionsPreference != null) {
                screen.removePreference(additionalPermissionsPreference);
            }
        } else {
            if (additionalPermissionsPreference == null) {
                additionalPermissionsPreference = new Preference(
                        getPreferenceManager().getContext());
                additionalPermissionsPreference.setKey(EXTRA_PREFS_KEY);
                additionalPermissionsPreference.setIcon(Utils.applyTint(getActivity(),
                        R.drawable.ic_more_items,
                        android.R.attr.colorControlNormal));
                additionalPermissionsPreference.setTitle(R.string.additional_permissions);
                additionalPermissionsPreference.setOnPreferenceClickListener(preference -> {
                    ManageCustomPermissionsFragment frag =
                            new ManageCustomPermissionsFragment();
                    frag.setTargetFragment(ManageStandardPermissionsFragment.this, 0);
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(android.R.id.content, frag);
                    ft.addToBackStack(null);
                    ft.commit();
                    return true;
                });

                screen.addPreference(additionalPermissionsPreference);
            }

            additionalPermissionsPreference.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, numExtraPermissions,
                    numExtraPermissions));
        }
    }

    private void updateRecentlyUsedWidget() {
        // Collect the apps that have used permissions.
        Context context = getPreferenceManager().getContext();
        List<Pair<PermissionApp, GroupUsage>> usages = new ArrayList<>();
        List<AppPermissionUsage> permissionUsages = mPermissionUsages.getUsages();
        int numApps = permissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appPermissionUsage = permissionUsages.get(appNum);

            if (appPermissionUsage.getAccessCount() <= 0) {
                continue;
            }
            if (Utils.isSystem(appPermissionUsage.getApp(), mLauncherPkgs)) {
                continue;
            }

            // Get the msot recent usage by this app.
            GroupUsage mostRecentUsage = null;
            List<GroupUsage> appGroups = appPermissionUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                GroupUsage groupUsage = appGroups.get(groupNum);

                if (!Utils.shouldShowPermission(context, groupUsage.getGroup())) {
                    continue;
                }
                // STOPSHIP: Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
                if (groupUsage.getGroup().getLabel().equals("Storage")) {
                    continue;
                }

                if (mostRecentUsage == null
                        || groupUsage.getLastAccessTime() >= mostRecentUsage.getLastAccessTime()) {
                    mostRecentUsage = groupUsage;
                }
            }

            if (mostRecentUsage != null) {
                usages.add(Pair.create(appPermissionUsage.getApp(), mostRecentUsage));
            }
        }
        usages.sort((x, y) -> compareAccessTime(x.second, y.second));

        if (usages.isEmpty()) {
            return;
        }

        int numAppsToShow = Math.min(usages.size(), MAXIMUM_APP_COUNT);

        PermissionApps.PermissionApp[] permApps = new PermissionApps.PermissionApp[numAppsToShow];
        for (int i = 0; i < numAppsToShow; i++) {
            permApps[i] = usages.get(i).first;
        }

        new PermissionApps.AppDataLoader(context, () -> {
            // Show the most recent three usages.
            int i = 0;
            for (; i < numAppsToShow; i++) {
                Pair<PermissionApp, GroupUsage> info = usages.get(i);
                AppPermissionGroup group = info.second.getGroup();
                AppEntityInfo appEntityInfo = new AppEntityInfo.Builder()
                        .setIcon(info.first.getIcon())
                        .setTitle(info.first.getLabel())
                        .setSummary(group.getLabel())
                        .setOnClickListener(v -> {
                            Intent intent = new Intent(context, AppPermissionActivity.class);
                            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, group.getApp().packageName);
                            intent.putExtra(Intent.EXTRA_PERMISSION_NAME, group.getName());
                            intent.putExtra(Intent.EXTRA_USER, group.getUser());
                            context.startActivity(intent);
                        })
                        .build();
                mAppUsageController.setAppEntity(i, appEntityInfo);
            }
            for (; i < MAXIMUM_APP_COUNT; i++) {
                mAppUsageController.removeAppEntity(i);
            }
            mAppUsageController.apply();
        }).execute(permApps);
    }

    private static int compareAccessTime(@NonNull GroupUsage x, @NonNull GroupUsage y) {
        long lastXAccess = x.getLastAccessTime();
        long lastYAccess = y.getLastAccessTime();

        if (lastXAccess > lastYAccess) {
            return -1;
        } else if (lastYAccess > lastXAccess) {
            return 1;
        } else {
            return x.hashCode() - y.hashCode();
        }
    }
}
