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
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.ui.handheld.FilterSpinner.FilterSpinnerAdapter;
import com.android.packageinstaller.permission.ui.handheld.FilterSpinner.TimeFilterItem;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Show the usage of all permission groups by a single app.
 *
 * <p>Shows a list of app usage of permission groups, each of which links to
 * AppPermissionsFragment.
 */
public class AppPermissionUsageFragment extends SettingsWithButtonHeader implements
        OnItemSelectedListener {

    private static final String LOG_TAG = "AppPermissionUsageFragment";

    private static final String KEY_SPINNER_TIME_INDEX = "_time_index";
    private static final String SPINNER_TIME_INDEX_KEY = AppPermissionUsageFragment.class.getName()
            + KEY_SPINNER_TIME_INDEX;

    private @NonNull String mPackageName;
    private @NonNull ApplicationInfo mAppInfo;

    private @NonNull PermissionUsages mPermissionUsages;

    private Spinner mFilterSpinner;
    private FilterSpinnerAdapter<TimeFilterItem> mFilterAdapter;

    /**
     * Only used to restore spinner state after onCreate. Once the list of times is reported, this
     * becomes invalid.
     */
    private int mSavedSpinnerIndex;

    /**
     * @return A new fragment
     */
    public static @NonNull AppPermissionUsageFragment newInstance(@NonNull String packageName,
            @NonNull UserHandle userHandle) {
        AppPermissionUsageFragment fragment = new AppPermissionUsageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
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

        if (savedInstanceState != null) {
            mSavedSpinnerIndex = savedInstanceState.getInt(SPINNER_TIME_INDEX_KEY);
        }

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        UserHandle userHandle = getArguments().getParcelable(Intent.EXTRA_USER);
        Activity activity = getActivity();
        mAppInfo = getApplicationInfo(getActivity(), mPackageName, userHandle);
        if (mAppInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        mPermissionUsages = new PermissionUsages(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getPreferenceManager().getContext();
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        View spinnerView = inflater.inflate(R.layout.single_spinner, root, false);
        getPreferencesContainer().addView(spinnerView, 1);

        mFilterSpinner = spinnerView.requireViewById(R.id.filter_spinner);
        mFilterAdapter = new FilterSpinnerAdapter<>(context);
        mFilterSpinner.setAdapter(mFilterAdapter);
        mFilterSpinner.setOnItemSelectedListener(this);

        FilterSpinner.addTimeFilters(mFilterAdapter, context);
        mFilterSpinner.setSelection(mSavedSpinnerIndex);

        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mAppInfo == null) {
            return;
        }
        Drawable icon = Utils.getBadgedIcon(getActivity(), mAppInfo);
        setHeader(icon, Utils.getFullAppLabel(mAppInfo, getContext()), true);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        reloadData();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SPINNER_TIME_INDEX_KEY, mFilterSpinner.getSelectedItemPosition());
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
            @NonNull String packageName, @NonNull UserHandle userHandle) {
        try {
            return activity.createPackageContextAsUser(packageName, 0,
                    userHandle).getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package: " + activity.getCallingPackage(), e);
            return null;
        }
    }

    private void updateUi() {
        if (!Utils.isPermissionsHubEnabled()) {
            setLoading(false, true);
            return;
        }
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

        // Get the current values of the time filter.
        TimeFilterItem timeFilterItem = getSelectedFilterItem();
        long startTime = (timeFilterItem == null ? 0
                : (System.currentTimeMillis() - timeFilterItem.getTime()));

        final AppPermissionUsage appPermissionUsage = permissionUsages.get(0);
        final List<AppPermissionUsage.GroupUsage> groupUsages = appPermissionUsage.getGroupUsages();
        groupUsages.sort(Comparator.comparing(GroupUsage::getAccessCount).reversed());

        final int permissionCount = groupUsages.size();
        for (int permissionIdx = 0; permissionIdx < permissionCount; permissionIdx++) {
            final GroupUsage groupUsage = groupUsages.get(permissionIdx);
            if (groupUsage.getAccessCount() <= 0 || groupUsage.getLastAccessTime() < startTime) {
                continue;
            }
            final AppPermissionGroup group = groupUsage.getGroup();
            // STOPSHIP: Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
            if (group.getLabel().equals("Storage")) {
                continue;
            }
            PermissionControlPreference pref = new PermissionControlPreference(context, group);
            pref.setTitle(groupUsage.getGroup().getLabel());
            pref.setUsageSummary(groupUsage, Utils.getAbsoluteLastUsageString(context, groupUsage));
            pref.setIcon(Utils.applyTint(context, group.getIconResId(),
                    android.R.attr.colorControlNormal));
            pref.setKey(group.getName());
            screen.addPreference(pref);
        }

        setLoading(false, true);
    }

    private TimeFilterItem getSelectedFilterItem() {
        int pos = mFilterSpinner.getSelectedItemPosition();
        if (pos != AdapterView.INVALID_POSITION) {
            return mFilterAdapter.getFilter(pos);
        }
        return null;
    }

    private void reloadData() {
        TimeFilterItem timeFilterItem = getSelectedFilterItem();
        if (timeFilterItem == null) {
            return;
        }
        long beginTimeMillis = Math.max(System.currentTimeMillis() - timeFilterItem.getTime(),
                Instant.EPOCH.toEpochMilli());
        mPermissionUsages.load(mAppInfo.uid, mPackageName, null, beginTimeMillis, Long.MAX_VALUE,
                PermissionUsages.USAGE_FLAG_LAST | PermissionUsages.USAGE_FLAG_HISTORICAL,
                getActivity().getLoaderManager(),
                true, this::updateUi, false);
    }
}
