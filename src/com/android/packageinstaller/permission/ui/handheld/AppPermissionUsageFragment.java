/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Show the usage of all permission groups by a single app.
 *
 * <p>Shows a list of app usage of permission groups, each of which links to
 * AppPermissionsFragment.
 */
public class AppPermissionUsageFragment extends SettingsWithButtonHeader {

    private static final String LOG_TAG = "AppPermissionUsageFragment";


    private @NonNull ApplicationInfo mAppInfo;

    private @NonNull PermissionUsages mPermissionUsages;

    /**
     * @return A new fragment
     */
    public static @NonNull AppPermissionUsageFragment newInstance(@NonNull String packageName) {
        return setPackageName(new AppPermissionUsageFragment(), packageName);
    }

    private static <T extends Fragment> T setPackageName(T fragment, @NonNull String packageName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.app_permission_usage_title);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        Activity activity = getActivity();
        mAppInfo = getApplicationInfo(getActivity(), packageName);
        if (mAppInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        final long beginTimeMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        mPermissionUsages = new PermissionUsages(getContext());
        mPermissionUsages.load(packageName, null, beginTimeMillis, Long.MAX_VALUE,
                PermissionUsages.USAGE_FLAG_LAST | PermissionUsages.USAGE_FLAG_HISTORICAL,
                getActivity().getLoaderManager(),
                true, this::updateUi, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Drawable icon = Utils.getBadgedIcon(getActivity(), mAppInfo);
        setHeader(icon, Utils.getFullAppLabel(mAppInfo, getContext()));
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
    public int getEmptyViewString() {
        return R.string.no_permission_usages;
    }

    private static ApplicationInfo getApplicationInfo(@NonNull Activity activity,
            @NonNull String packageName) {
        try {
            return activity.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package: " + activity.getCallingPackage(), e);
            return null;
        }
    }

    private void updateUi() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        // Add the permission usages.
        final List<AppPermissionUsage> permissionUsages = mPermissionUsages.getUsages();
        if (permissionUsages.isEmpty()) {
            return;
        }
        if (permissionUsages.size() > 1) {
            Log.e(LOG_TAG, "Expected one AppPermissionUsage but got: " + permissionUsages);
            getActivity().finish();
            return;
        }

        final AppPermissionUsage appPermissionUsage = permissionUsages.get(0);
        final List<AppPermissionUsage.GroupUsage> groupUsages = appPermissionUsage.getGroupUsages();
        groupUsages.sort(Comparator.comparing(GroupUsage::getAccessCount).reversed());

        final int permissionCount = groupUsages.size();
        for (int permissionIdx = 0; permissionIdx < permissionCount; permissionIdx++) {
            final GroupUsage groupUsage = groupUsages.get(permissionIdx);
            if (groupUsage.getAccessCount() <= 0) {
                continue;
            }
            final AppPermissionGroup group = groupUsage.getGroup();
            // STOPSHIP: Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
            if (group.getLabel().equals("Storage")) {
                continue;
            }
            Preference pref = new PermissionControlPreference(context, group);
            pref.setTitle(groupUsage.getGroup().getLabel());
            if (groupUsage.getAccessDuration() == 0) {
                pref.setSummary(context.getString(R.string.app_permission_usage_summary_no_duration,
                        groupUsage.getAccessCount(), Utils.getLastUsageString(context,
                                groupUsage)));
            } else {
                pref.setSummary(context.getString(R.string.app_permission_usage_summary,
                        groupUsage.getAccessCount(),
                        Utils.getUsageDurationString(context, groupUsage),
                        Utils.getLastUsageString(context, groupUsage)));
            }
            pref.setIcon(Utils.applyTint(context, group.getIconResId(),
                    android.R.attr.colorControlNormal));
            pref.setKey(group.getName());
            screen.addPreference(pref);
        }

        setLoading(false, true);
    }
}
