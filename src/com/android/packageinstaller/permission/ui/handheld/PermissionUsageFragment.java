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

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Spinner;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.packageinstaller.permission.model.PermissionGroups;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.BarChartInfo;
import com.android.settingslib.widget.BarChartPreference;
import com.android.settingslib.widget.BarViewInfo;
import com.android.settingslib.widget.settingsspinner.SettingsSpinnerAdapter;

import java.lang.annotation.Retention;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Show the usage of all apps of all permission groups.
 *
 * <p>Shows a filterable list of app usage of permission groups, each of which links to
 * AppPermissionsFragment.
 */
public class PermissionUsageFragment extends PermissionsFrameFragment implements
        PermissionGroups.PermissionsGroupsChangeCallback, OnItemSelectedListener {
    private static final String LOG_TAG = "PermissionUsageFragment";

    @Retention(SOURCE)
    @IntDef(value = {SORT_MOST_PERMISSIONS, SORT_MOST_ACCESSES, SORT_RECENT})
    @interface SortOption {}
    static final int SORT_MOST_PERMISSIONS = 1;
    static final int SORT_MOST_ACCESSES = 2;
    static final int SORT_RECENT = 3;

    private static final int MENU_FILTER_BY_PERMISSIONS = MENU_HIDE_SYSTEM + 1;

    private static final String KEY_SHOW_SYSTEM_PREFS = "_show_system";
    private static final String SHOW_SYSTEM_KEY = PermissionUsageFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;
    private static final String KEY_PERMS_INDEX = "_time_index";
    private static final String PERMS_INDEX_KEY = PermissionUsageFragment.class.getName()
            + KEY_PERMS_INDEX;
    private static final String KEY_SPINNER_TIME_INDEX = "_time_index";
    private static final String SPINNER_TIME_INDEX_KEY = PermissionUsageFragment.class.getName()
            + KEY_SPINNER_TIME_INDEX;
    private static final String KEY_SPINNER_SORT_INDEX = "_sort_index";
    private static final String SPINNER_SORT_INDEX_KEY = PermissionUsageFragment.class.getName()
            + KEY_SPINNER_SORT_INDEX;

    private PermissionGroups mPermissionGroups;

    private @NonNull AppOpsManager mAppOpsManager;
    private Collator mCollator;
    private ArraySet<String> mLauncherPkgs;

    private PermissionGroup mFilterGroup;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;

    private Spinner mFilterSpinnerTime;
    private FilterSpinnerAdapter<TimeFilterItem> mFilterAdapterTime;
    private Spinner mSortSpinner;
    private FilterSpinnerAdapter<SortItem> mSortAdapter;

    /**
     * Only used to restore permission selection state after onCreate. Once the first list of groups
     * is reported, this becomes invalid.
     */
    private CharSequence mSavedPermLabel;

    /**
     * Only used to restore time spinner state after onCreate. Once the list of times is reported,
     * this becomes invalid.
     */
    private int mSavedTimeSpinnerIndex;

    /**
     * Only used to restore sort spinner state after onCreate. Once the list of sorts is reported,
     * this becomes invalid.
     */
    private int mSavedSortSpinnerIndex;

    /**
     * @return A new fragment
     */
    public static @NonNull PermissionUsageFragment newInstance(@Nullable String permissionName) {
        PermissionUsageFragment fragment = new PermissionUsageFragment();
        Bundle arguments = new Bundle();
        if (permissionName != null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
        }
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.permission_usage_title);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
            mSavedPermLabel = savedInstanceState.getCharSequence(PERMS_INDEX_KEY);
            mSavedTimeSpinnerIndex = savedInstanceState.getInt(SPINNER_TIME_INDEX_KEY);
            mSavedSortSpinnerIndex = savedInstanceState.getInt(SPINNER_SORT_INDEX_KEY);
        } else {
            mSavedPermLabel = null;
            mSavedTimeSpinnerIndex = 0;
            mSavedSortSpinnerIndex = 0;
        }

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Context context = getPreferenceManager().getContext();
        mFilterGroup = null;
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mCollator = Collator.getInstance(
                context.getResources().getConfiguration().getLocales().get(0));
        mLauncherPkgs = Utils.getLauncherPackages(context);
        mPermissionGroups = new PermissionGroups(context, getActivity().getLoaderManager(), this,
                true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getPreferenceManager().getContext();
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        // Setup filter spinners.
        View header = inflater.inflate(R.layout.permission_usage_filter_spinners, root, false);
        getPreferencesContainer().addView(header, 0);

        mFilterSpinnerTime = header.requireViewById(R.id.filter_spinner_time);
        mFilterAdapterTime = new FilterSpinnerAdapter<>(context);
        mFilterSpinnerTime.setAdapter(mFilterAdapterTime);
        mFilterSpinnerTime.setOnItemSelectedListener(this);

        mSortSpinner = header.requireViewById(R.id.sort_spinner);
        mSortAdapter = new FilterSpinnerAdapter<>(context);
        mSortSpinner.setAdapter(mSortAdapter);
        mSortSpinner.setOnItemSelectedListener(this);

        // Add time spinner entries.
        mFilterAdapterTime.addFilter(new TimeFilterItem(Long.MAX_VALUE,
                context.getString(R.string.permission_usage_any_time),
                R.string.permission_usage_bar_chart_title_any_time,
                R.string.permission_usage_list_title_any_time));
        mFilterAdapterTime.addFilter(new TimeFilterItem(DAYS.toMillis(7),
                context.getString(R.string.permission_usage_last_7_days),
                R.string.permission_usage_bar_chart_title_last_7_days,
                R.string.permission_usage_list_title_last_7_days));
        mFilterAdapterTime.addFilter(new TimeFilterItem(DAYS.toMillis(1),
                context.getString(R.string.permission_usage_last_day),
                R.string.permission_usage_bar_chart_title_last_day,
                R.string.permission_usage_list_title_last_day));
        mFilterAdapterTime.addFilter(new TimeFilterItem(HOURS.toMillis(1),
                context.getString(R.string.permission_usage_last_hour),
                R.string.permission_usage_bar_chart_title_last_hour,
                R.string.permission_usage_list_title_last_hour));
        mFilterAdapterTime.addFilter(new TimeFilterItem(MINUTES.toMillis(15),
                context.getString(R.string.permission_usage_last_15_minutes),
                R.string.permission_usage_bar_chart_title_last_15_minutes,
                R.string.permission_usage_list_title_last_15_minutes));
        mFilterSpinnerTime.setSelection(mSavedTimeSpinnerIndex);

        // Add sort spinner entries.
        mSortAdapter.addFilter(
                new SortItem(context.getString(R.string.sort_spinner_most_permissions),
                        SORT_MOST_PERMISSIONS));
        mSortAdapter.addFilter(new SortItem(context.getString(R.string.sort_spinner_most_accesses),
                SORT_MOST_ACCESSES));
        mSortAdapter.addFilter(new SortItem(context.getString(R.string.sort_spinner_recent),
                SORT_RECENT));
        mSortSpinner.setSelection(mSavedSortSpinnerIndex);

        return root;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        updateUI();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
        outState.putCharSequence(PERMS_INDEX_KEY,
                mFilterGroup == null ? null : mFilterGroup.getLabel());
        outState.putInt(SPINNER_TIME_INDEX_KEY, mFilterSpinnerTime.getSelectedItemPosition());
        outState.putInt(SPINNER_SORT_INDEX_KEY, mSortSpinner.getSelectedItemPosition());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_FILTER_BY_PERMISSIONS, Menu.NONE, R.string.filter_by_permissions);
        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
            updateMenu();
        }
        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_permission_usage,
                getClass().getName());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
            case MENU_FILTER_BY_PERMISSIONS:
                showPermissionFilterDialog();
                break;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mShowSystem = item.getItemId() == MENU_SHOW_SYSTEM;
                updateUI();
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
    public void onPermissionGroupsChanged() {
        if (mPermissionGroups.getGroups().isEmpty()) {
            return;
        }

        // Use the saved permission group or the one passed as an argument, if applicable.
        String groupName = (mSavedPermLabel == null ? null : mSavedPermLabel.toString());
        if (groupName == null) {
            String permName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
            groupName = Utils.getGroupOfPlatformPermission(permName);
            if (permName != null && groupName == null) {
                Log.w(LOG_TAG, "Invalid platform permission: " + permName);
            }
        }

        if (groupName != null && mFilterGroup == null) {
            List<PermissionGroup> groups = getOSPermissionGroups();
            int numGroups = groups.size();
            for (int i = 0; i < numGroups; i++) {
                PermissionGroup group = groups.get(i);
                if (group.getName().equals(groupName)) {
                    mFilterGroup = group;
                }
            }
        }

        updateUI();
        setLoading(false, true);
    }

    @Override
    public int getEmptyViewString() {
        return R.string.no_permission_usages;
    }

    private void updateUI() {
        List<PermissionGroup> groups = new ArrayList<>(mPermissionGroups.getGroups());
        if (groups.isEmpty() || getActivity() == null) {
            return;
        }
        Context context = getPreferenceManager().getContext();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        // Get the current values of the time filter.
        long timeFilter = Long.MAX_VALUE;
        int pos = mFilterSpinnerTime.getSelectedItemPosition();
        TimeFilterItem timeFilterItem = null;
        if (pos != AdapterView.INVALID_POSITION) {
            timeFilterItem = mFilterAdapterTime.getFilter(pos);
            timeFilter = timeFilterItem.getTime();
        }
        long curTime = System.currentTimeMillis();

        // Find the permission usages we want to add.
        ArrayMap<AppPermissionGroup, PermissionApp> groupToApp = new ArrayMap<>();
        ArrayMap<String, List<AppPermissionUsage>> appToUsages = new ArrayMap<>();
        ArrayMap<String, ArraySet<AppPermissionGroup>> appToGroups = new ArrayMap<>();
        ArrayMap<PermissionGroup, Integer> groupUsers = new ArrayMap<>();
        analyzeUsages(groups, groupToApp, appToUsages, appToGroups, groupUsers, timeFilter,
                context);

        // Update bar chart
        BarChartPreference barChart = createBarChart(groups, groupUsers, timeFilterItem, context);
        screen.addPreference(barChart);

        // Sort the apps.
        pos = mSortSpinner.getSelectedItemPosition();
        if (pos == AdapterView.INVALID_POSITION) {
            pos = 0;
        }
        int sortOption = mSortAdapter.getFilter(pos).getSortOption();
        ArrayMap<AppPermissionGroup, Pair<Integer, Integer>> groupToNumAccesses = new ArrayMap<>();
        List<String> apps = new ArrayList<>(appToUsages.keySet());
        if (sortOption == SORT_MOST_PERMISSIONS) {
            // Sort by number of permissions then by last access time.
            apps.sort((x, y) -> {
                int groupDiff = appToGroups.get(y).size() - appToGroups.get(x).size();
                if (groupDiff != 0) {
                    return groupDiff;
                }
                return compareAccessTime(appToUsages.get(x).get(0), appToUsages.get(y).get(0));
            });
        } else if (sortOption == SORT_MOST_ACCESSES) {
            // Sort by number of accesses
            sortAppsByNumAccesses(apps, appToGroups, groupToNumAccesses, timeFilter);
        } else if (sortOption == SORT_RECENT) {
            // Sort by last access time then by number of permissions.
            apps.sort((x, y) -> {
                int timeDiff = compareAccessTime(appToUsages.get(x).get(0),
                        appToUsages.get(y).get(0));
                if (timeDiff != 0) {
                    return timeDiff;
                }
                return appToGroups.get(y).size() - appToGroups.get(x).size();
            });
        } else {
            Log.w(LOG_TAG, "Unexpected sort option: " + sortOption);
        }

        // Add the preferences.
        PreferenceCategory category = new PreferenceCategory(context);
        if (timeFilterItem != null) {
            category.setTitle(timeFilterItem.getListTitleRes());
        }
        screen.addPreference(category);
        Set<String> addedEntries = new ArraySet<>();
        int numApps = apps.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            String appKey = apps.get(appNum);
            List<AppPermissionGroup> appGroups = new ArrayList<>(appToGroups.get(appKey));
            if (sortOption == SORT_MOST_ACCESSES) {
                appGroups.sort((x, y) -> {
                    Pair<Integer, Integer> xNums = groupToNumAccesses.get(x);
                    Pair<Integer, Integer> yNums = groupToNumAccesses.get(y);
                    return (yNums.first + yNums.second) - (xNums.first + xNums.second);
                });
            } else {
                appGroups.sort((x, y) -> compareAccessTime(x.getAppPermissionUsage().get(0),
                        y.getAppPermissionUsage().get(0)));
            }

            PreferenceGroup parent = category;
            if (appGroups.size() > 1) {
                // Add a "parent" entry for the app that will expand to the individual entries.
                parent = createExpandablePreferenceGroup(groupToApp.get(appGroups.get(0)),
                        appGroups, context);
                category.addPreference(parent);
            }

            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionGroup group = appGroups.get(groupNum);
                AppPermissionUsage usage = group.getAppPermissionUsage().get(0);
                // Filter out entries we've seen before.
                if (!addedEntries.add(appKey + "," + usage.getPermissionGroupName())) {
                    continue;
                }
                PermissionControlPreference pref = createPreference(group, usage,
                        groupToApp.get(group), appGroups, groupToNumAccesses, sortOption, context);
                parent.addPreference(pref);
            }
        }
    }

    /**
     * Analyze all of the usage data and build the data structures we need to build our UI.
     *
     * @param groups all of the permission groups
     * @param groupToApp a map from AppPermissionGroup to PermissionApp
     * @param appToUsages a map from app key to that app's most recent permission usages for each
     *                    permission
     * @param appToGroups a map from app key to that app's AppPermissionGroups
     * @param groupUsers a map of how many apps use each permission group
     * @param timeDiff the number of milliseconds in the past to get usage information.
     * @param context the context
     */
    private void analyzeUsages(@NonNull List<PermissionGroup> groups,
            @NonNull ArrayMap<AppPermissionGroup, PermissionApp> groupToApp,
            @NonNull ArrayMap<String, List<AppPermissionUsage>> appToUsages,
            @NonNull ArrayMap<String, ArraySet<AppPermissionGroup>> appToGroups,
            @NonNull ArrayMap<PermissionGroup, Integer> groupUsers, long timeDiff,
            @NonNull Context context) {
        String permissionGroupFilter =
                (mFilterGroup == null ? null : mFilterGroup.getLabel().toString());
        mHasSystemApps = false;
        boolean menuOptionsInvalided = false;
        long curTime = System.currentTimeMillis();

        int numGroups = groups.size();
        for (int groupNum = 0; groupNum < numGroups; groupNum++) {
            PermissionGroup permissionGroup = groups.get(groupNum);
            groupUsers.put(permissionGroup, 0);

            // Filter out third party permissions
            if (!permissionGroup.getDeclaringPackage().equals(ManagePermissionsFragment.OS_PKG)) {
                continue;
            }
            // Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
            if (permissionGroup.getLabel().equals("Storage")) {
                continue;
            }

            List<PermissionApp> permissionApps = permissionGroup.getPermissionApps().getApps();
            int numApps = permissionApps.size();
            for (int appNum = 0; appNum < numApps; appNum++) {
                PermissionApp permApp = permissionApps.get(appNum);
                AppPermissionGroup group = permApp.getPermissionGroup();
                if (!Utils.shouldShowPermission(context, group)) {
                    continue;
                }
                List<AppPermissionUsage> groupUsages = group.getAppPermissionUsage();
                int numUsages = groupUsages.size();
                for (int usageNum = 0; usageNum < numUsages; usageNum++) {
                    AppPermissionUsage usage = groupUsages.get(usageNum);
                    if (usage.getTime() == 0) {
                        continue;
                    }
                    // Implement time filter.
                    if (curTime - usage.getTime() > timeDiff) {
                        continue;
                    }

                    boolean isSystemApp = Utils.isSystem(permApp, mLauncherPkgs);
                    if (isSystemApp && !menuOptionsInvalided) {
                        mHasSystemApps = true;
                        getActivity().invalidateOptionsMenu();
                        menuOptionsInvalided = true;
                    }

                    if (!isSystemApp || mShowSystem) {
                        groupUsers.put(permissionGroup, groupUsers.get(permissionGroup) + 1);

                        // Implement group filter.
                        // We can't do this earlier because we still need to compute which apps have
                        // used other permissions for the bar chart.
                        if (permissionGroupFilter != null
                                && !permissionGroup.getLabel().equals(permissionGroupFilter)) {
                            break;
                        }

                        String key = permApp.getKey();
                        groupToApp.put(group, permApp);
                        if (!appToUsages.containsKey(key)) {
                            appToUsages.put(key, new ArrayList<>());
                        }
                        appToUsages.get(key).add(usage);
                        if (!appToGroups.containsKey(key)) {
                            appToGroups.put(key, new ArraySet<>());
                        }
                        appToGroups.get(key).add(group);

                        break;
                    }
                }
            }
        }
    }

    /**
     * Create a bar chart showing the permissions that are used by the most apps.
     *
     * @param groups all of the permission groups
     * @param groupUsers a map of how many apps use each permission group
     * @param timeFilterItem the time filter, or null if no filter is set
     * @param context the context
     *
     * @return the Preference representing the bar chart
     */
    private BarChartPreference createBarChart(@NonNull List<PermissionGroup> groups,
            @NonNull ArrayMap<PermissionGroup, Integer> groupUsers,
            @Nullable TimeFilterItem timeFilterItem, @NonNull Context context) {
        BarChartInfo.Builder builder = new BarChartInfo.Builder();
        BarChartPreference barChart = new BarChartPreference(context, null);
        if (timeFilterItem != null) {
            builder.setTitle(timeFilterItem.getGraphTitleRes());
        }
        if (mFilterGroup != null) {
            builder.setDetails(R.string.app_permission_usage_detail_label);
            builder.setDetailsOnClickListener(v -> {
                mFilterGroup = null;
                updateUI();
            });
        }

        groups.sort((x, y) -> groupUsers.get(y) - groupUsers.get(x));

        for (int i = 0; i < 4; i++) {
            PermissionGroup group = groups.get(i);
            BarViewInfo barViewInfo = new BarViewInfo(
                    Utils.applyTint(context, group.getIcon(), android.R.attr.colorControlNormal),
                    groupUsers.get(group), R.string.app_permission_usage_bar_label);

            barViewInfo.setClickListener(v -> {
                mFilterGroup = group;
                updateUI();
            });
            builder.addBarViewInfo(barViewInfo);
        }
        barChart.initializeBarChart(builder.build());
        return barChart;
    }

    /**
     * Sort the list of apps by the number of times they were accessed
     *
     * @param apps the list of app keys
     * @param appToGroups a map from app key to that app's AppPermissionGroups
     * @param groupToNumAccesses a map of the number of foreground and background accesses by an app
     * @param timeDiff the number of milliseconds in the past to get usage information.  If this is
     *                 larger than the current time in milliseconds, we go back as far as possible.
     */
    private void sortAppsByNumAccesses(@NonNull List<String> apps,
            @NonNull ArrayMap<String, ArraySet<AppPermissionGroup>> appToGroups,
            @NonNull ArrayMap<AppPermissionGroup, Pair<Integer, Integer>> groupToNumAccesses,
            long timeDiff) {
        ArrayMap<String, Integer> appToNumAccesses = new ArrayMap<>();
        long curTime = System.currentTimeMillis();

        int numApps = apps.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            String appKey = apps.get(appNum);
            ArraySet<AppPermissionGroup> appGroups = appToGroups.get(appKey);
            int numAppAccesses = 0;

            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionGroup group = appGroups.valueAt(groupNum);
                int numFGAccesses = 0, numBGAccesses = 0;
                AppOpsManager.HistoricalPackageOps history = Utils.getUsageForGroup(group,
                        mAppOpsManager, timeDiff);
                int numEntries = history.getEntryCount();
                for (int accessNum = 0; accessNum < numEntries; accessNum++) {
                    AppOpsManager.HistoricalOpEntry historyEntry = history.getEntryAt(accessNum);
                    numFGAccesses += historyEntry.getForegroundAccessCount();
                    numBGAccesses += historyEntry.getBackgroundAccessCount();
                }

                groupToNumAccesses.put(group, Pair.create(numFGAccesses, numBGAccesses));
                numAppAccesses += numFGAccesses + numBGAccesses;
            }
            appToNumAccesses.put(appKey, numAppAccesses);
        }

        apps.sort((x, y) -> {
            return appToNumAccesses.get(y) - appToNumAccesses.get(x);
        });
    }

    /**
     * Create an expandable preference group that can hold children.
     *
     * @param app the app this group represents
     * @param appGroups the permission groups this app has accessed
     * @param context the context
     *
     * @return the expandable preference group.
     */
    private PreferenceGroup createExpandablePreferenceGroup(@NonNull PermissionApp app,
            @NonNull List<AppPermissionGroup> appGroups, @NonNull Context context) {
        List<Integer> groupIcons = new ArrayList<>(appGroups.size());
        int numGroups = appGroups.size();
        for (int groupNum = 0; groupNum < numGroups; groupNum++) {
            groupIcons.add(appGroups.get(groupNum).getIconResId());
        }

        PreferenceGroup preference = new ExpandablePreferenceGroup(context, groupIcons);
        preference.setTitle(app.getLabel());
        preference.setIcon(app.getIcon());
        return preference;
    }

    /**
     * Create a preference representing an app's use of a permission
     *
     * @param group the AppPermissionGroup
     * @param usage the AppPermissionUsage
     * @param permApp the PermissionApp
     * @param appGroups the permission groups this app has accessed
     * @param groupToNumAccesses a map of the number of foreground and background accesses by an app
     * @param sortOption how the entries should be sorted
     * @param context the context
     *
     * @return the Preference
     */
    private PermissionControlPreference createPreference(@NonNull AppPermissionGroup group,
            @NonNull AppPermissionUsage usage, @NonNull PermissionApp permApp,
            @NonNull List<AppPermissionGroup> appGroups,
            @NonNull ArrayMap<AppPermissionGroup, Pair<Integer, Integer>> groupToNumAccesses,
            @SortOption int sortOption, @NonNull Context context) {
        PermissionControlPreference pref = new PermissionControlPreference(context, group);
        pref.setTitle(permApp.getLabel());

        if (sortOption == SORT_MOST_ACCESSES) {
            Pair<Integer, Integer> numAccesses = groupToNumAccesses.get(group);
            if (numAccesses.second == 0) {
                pref.setSummary(
                        context.getString(R.string.permission_usage_summary_num_accesses,
                                usage.getPermissionGroupLabel(), numAccesses.first));
            } else {
                pref.setSummary(
                        context.getString(
                                R.string.permission_usage_summary_num_accesses_background,
                                usage.getPermissionGroupLabel(),
                                numAccesses.first + numAccesses.second, numAccesses.second));
            }
        } else {
            pref.setSummary(context.getString(R.string.permission_usage_summary_last_access,
                    usage.getPermissionGroupLabel(), Utils.getTimeDiffStr(context,
                            System.currentTimeMillis() - usage.getTime())));
        }
        if (appGroups.size() == 1) {
            pref.setIcon(permApp.getIcon());
        }
        pref.setSummaryIcons(Collections.singletonList(group.getIconResId()));
        pref.setKey(usage.getPackageName() + "," + usage.getPermissionGroupName());
        pref.useSmallerIcon();
        return pref;
    }

    /**
     * Compare two AppPermissionUsages by their access time.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x an AppPermissionUsage.
     * @param y an AppPermissionUsage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessTime(@NonNull AppPermissionUsage x,
            @NonNull AppPermissionUsage y) {
        long lastXAccess = x.getTime();
        long lastYAccess = y.getTime();

        if (lastXAccess > lastYAccess) {
            return -1;
        } else if (lastYAccess > lastXAccess) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Get the permission groups declared by the OS.
     *
     * @return a list of the permission groups declared by the OS.
     */
    private @NonNull List<PermissionGroup> getOSPermissionGroups() {
        List<PermissionGroup> filterGroups = new ArrayList<>();
        List<PermissionGroup> groups = mPermissionGroups.getGroups();
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            PermissionGroup permissionGroup = groups.get(i);
            if (Utils.isModernPermissionGroup(permissionGroup.getName())) {
                filterGroups.add(permissionGroup);
            }
        }
        return filterGroups;
    }

    /**
     * Show a dialog that allows selecting a permission group by which to filter the entries.
     */
    private void showPermissionFilterDialog() {
        Context context = getPreferenceManager().getContext();

        // Get the permission labels.
        List<PermissionGroup> groups = getOSPermissionGroups();
        groups.sort(
                (x, y) -> mCollator.compare(x.getLabel().toString(), y.getLabel().toString()));

        // Create the spinner entries.
        PermissionGroup[] groupsArr = new PermissionGroup[groups.size() + 1];
        CharSequence[] groupLabels = new CharSequence[groupsArr.length];
        groupsArr[0] = null;
        groupLabels[0] = context.getString(R.string.permission_usage_any_permission);
        int selection = 0;
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            PermissionGroup group = groups.get(i);
            groupsArr[i + 1] = group;
            groupLabels[i + 1] = group.getLabel();
            if (group.equals(mFilterGroup)) {
                selection = i + 1;
            }
        }

        // Create the dialog
        Bundle args = new Bundle();
        args.putCharSequence(PermissionsFilterDialog.TITLE,
                context.getString(R.string.filter_by_title));
        args.putCharSequenceArray(PermissionsFilterDialog.ELEMS, groupLabels);
        args.putInt(PermissionsFilterDialog.SELECTION, selection);
        PermissionsFilterDialog chooserDialog = new PermissionsFilterDialog(this, groupsArr);
        chooserDialog.setArguments(args);
        chooserDialog.show(getChildFragmentManager().beginTransaction(), "backgroundChooser");
    }

    /**
     * Callback when the user selects a permission group by which to filter.
     *
     * @param selectedGroup The PermissionGroup to use to filter entries, or null if we should show
     *                      all entries.
     */
    private void onPermissionGroupSelected(@Nullable PermissionGroup selectedGroup) {
        mFilterGroup = selectedGroup;
        updateUI();
    }

    /**
     * A dialog that allows the user to select a permission group by which to filter entries.
     *
     * @see #showPermissionFilterDialog()
     */
    public static class PermissionsFilterDialog extends DialogFragment {
        private static final String TITLE = PermissionsFilterDialog.class.getName() + ".arg.title";
        private static final String ELEMS = PermissionsFilterDialog.class.getName() + ".arg.elems";
        private static final String SELECTION = PermissionsFilterDialog.class.getName()
                + ".arg.selection";

        private @NonNull PermissionUsageFragment mFragment;
        private @NonNull PermissionGroup[] mGroups;

        public PermissionsFilterDialog(@NonNull PermissionUsageFragment fragment,
                @NonNull PermissionGroup[] groups) {
            mFragment = fragment;
            mGroups = groups;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            CharSequence[] elems = getArguments().getCharSequenceArray(ELEMS);
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(getArguments().getCharSequence(TITLE))
                    .setSingleChoiceItems(elems, getArguments().getInt(SELECTION),
                            (dialog, which) -> {
                                dismissAllowingStateLoss();
                                mFragment.onPermissionGroupSelected(mGroups[which]);
                            }
                    );

            return b.create();
        }
    }

    /**
     * An adapter that stores the entries in a filter spinner.
     * @param <T> The type of the entries in the filter spinner.
     */
    private static class FilterSpinnerAdapter<T extends SpinnerItem> extends
            SettingsSpinnerAdapter<CharSequence> {
        private final ArrayList<T> mFilterOptions = new ArrayList<>();

        FilterSpinnerAdapter(@NonNull Context context) {
            super(context);
        }

        public void addFilter(@NonNull T filter) {
            mFilterOptions.add(filter);
            notifyDataSetChanged();
        }

        public T getFilter(int position) {
            return mFilterOptions.get(position);
        }

        @Override
        public int getCount() {
            return mFilterOptions.size();
        }

        @Override
        public CharSequence getItem(int position) {
            return mFilterOptions.get(position).getLabel();
        }

        @Override
        public void clear() {
            mFilterOptions.clear();
            super.clear();
        }
    }

    /**
     * An interface to represent items that we can use as filters.
     */
    private interface SpinnerItem {
        @NonNull String getLabel();
    }

    /**
     * A spinner item representing a given time, e.g., "in the last hour".
     */
    private static class TimeFilterItem implements SpinnerItem {
        private final long mTime;
        private final @NonNull String mLabel;
        private final @StringRes int mGraphTitleRes;
        private final @StringRes int mListTitleRes;

        TimeFilterItem(long time, @NonNull String label, @StringRes int graphTitleRes,
                @StringRes int listTitleRes) {
            mTime = time;
            mLabel = label;
            mGraphTitleRes = graphTitleRes;
            mListTitleRes = listTitleRes;
        }

        /**
         * Get the time represented by this object in milliseconds.
         *
         * @return the time represented by this object.
         */
        public long getTime() {
            return mTime;
        }

        public @NonNull String getLabel() {
            return mLabel;
        }

        public @StringRes int getGraphTitleRes() {
            return mGraphTitleRes;
        }

        public @StringRes int getListTitleRes() {
            return mListTitleRes;
        }
    }

    /**
     * A spinner item representing different ways to sort the entries.
     */
    private static class SortItem implements SpinnerItem {
        private final @NonNull String mLabel;
        private final @SortOption int mSortOption;

        SortItem(@NonNull String label, @SortOption int sortOption) {
            mLabel = label;
            mSortOption = sortOption;
        }

        public @NonNull String getLabel() {
            return mLabel;
        }

        public @SortOption int getSortOption() {
            return mSortOption;
        }
    }
}
