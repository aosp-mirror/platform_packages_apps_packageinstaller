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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
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
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.ui.handheld.FilterSpinner.FilterSpinnerAdapter;
import com.android.packageinstaller.permission.ui.handheld.FilterSpinner.SpinnerItem;
import com.android.packageinstaller.permission.ui.handheld.FilterSpinner.TimeFilterItem;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.BarChartInfo;
import com.android.settingslib.widget.BarChartPreference;
import com.android.settingslib.widget.BarViewInfo;

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
public class PermissionUsageFragment extends SettingsWithButtonHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback, OnItemSelectedListener {
    private static final String LOG_TAG = "PermissionUsageFragment";

    @Retention(SOURCE)
    @IntDef(value = {SORT_RECENT, SORT_MOST_PERMISSIONS, SORT_MOST_ACCESSES})
    @interface SortOption {}
    static final int SORT_RECENT = 1;
    static final int SORT_MOST_PERMISSIONS = 2;
    static final int SORT_MOST_ACCESSES = 3;

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

    /**
     * The maximum number of columns shown in the bar chart.
     */
    private static final int MAXIMUM_NUM_BARS = 4;

    private @NonNull PermissionUsages mPermissionUsages;

    private Collator mCollator;
    private ArraySet<String> mLauncherPkgs;

    private String mFilterGroup;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;

    private Spinner mFilterSpinnerTime;
    private FilterSpinnerAdapter<TimeFilterItem> mFilterAdapterTime;
    private Spinner mSortSpinner;
    private FilterSpinnerAdapter<SortItem> mSortAdapter;

    /**
     * Only used to restore permission selection state or use the passed permission after onCreate.
     * Once the first list of groups is reported, this becomes invalid.
     */
    private String mSavedGroupName;

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
    public static @NonNull PermissionUsageFragment newInstance(@Nullable String groupName) {
        PermissionUsageFragment fragment = new PermissionUsageFragment();
        Bundle arguments = new Bundle();
        if (groupName != null) {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
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
            mSavedGroupName = savedInstanceState.getString(PERMS_INDEX_KEY);
            mSavedTimeSpinnerIndex = savedInstanceState.getInt(SPINNER_TIME_INDEX_KEY);
            mSavedSortSpinnerIndex = savedInstanceState.getInt(SPINNER_SORT_INDEX_KEY);
        }

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        if (mSavedGroupName == null) {
            mSavedGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        }

        Context context = getPreferenceManager().getContext();
        mFilterGroup = null;
        mCollator = Collator.getInstance(
                context.getResources().getConfiguration().getLocales().get(0));
        mLauncherPkgs = Utils.getLauncherPackages(context);
        mPermissionUsages = new PermissionUsages(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getPreferenceManager().getContext();
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        // Setup filter spinners.
        View header = inflater.inflate(R.layout.permission_usage_filter_spinners, root, false);
        getPreferencesContainer().addView(header, 1);

        mFilterSpinnerTime = header.requireViewById(R.id.filter_spinner_time);
        mFilterAdapterTime = new FilterSpinnerAdapter<>(context);
        mFilterSpinnerTime.setAdapter(mFilterAdapterTime);
        mFilterSpinnerTime.setOnItemSelectedListener(this);

        mSortSpinner = header.requireViewById(R.id.sort_spinner);
        mSortAdapter = new FilterSpinnerAdapter<>(context);
        mSortSpinner.setAdapter(mSortAdapter);
        mSortSpinner.setOnItemSelectedListener(this);

        // Add time spinner entries.
        FilterSpinner.addTimeFilters(mFilterAdapterTime, context);
        mFilterSpinnerTime.setSelection(mSavedTimeSpinnerIndex);

        // Add sort spinner entries.
        mSortAdapter.addFilter(new SortItem(context.getString(R.string.sort_spinner_recent),
                SORT_RECENT));
        mSortAdapter.addFilter(
                new SortItem(context.getString(R.string.sort_spinner_most_permissions),
                        SORT_MOST_PERMISSIONS));
        mSortSpinner.setSelection(mSavedSortSpinnerIndex);

        return root;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mFilterSpinnerTime) {
            reloadData();
        } else if (parent == mSortSpinner) {
            // We already loaded all data, so don't reload
            updateUI();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
        outState.putString(PERMS_INDEX_KEY, mFilterGroup);
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
                // We already loaded all data, so don't reload
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
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }

        // Use the saved permission group or the one passed as an argument, if applicable.
        if (mSavedGroupName != null && mFilterGroup == null) {
            if (getGroup(mSavedGroupName) != null) {
                mFilterGroup = mSavedGroupName;
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
        final List<AppPermissionUsage> appPermissionUsages =
                new ArrayList<>(mPermissionUsages.getUsages());
        if (appPermissionUsages.isEmpty() || getActivity() == null) {
            return;
        }
        Context context = getActivity();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        mHasSystemApps = false;

        final TimeFilterItem timeFilterItem = getSelectedFilterItem();
        long curTime = System.currentTimeMillis();
        long startTime = (timeFilterItem == null ? 0 : (curTime - timeFilterItem.getTime()));

        List<Pair<AppPermissionUsage, GroupUsage>> usages = new ArrayList<>();
        int numApps = appPermissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appUsage = appPermissionUsages.get(appNum);
            List<GroupUsage> appGroups = appUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                GroupUsage groupUsage = appGroups.get(groupNum);

                if (groupUsage.getAccessCount() <= 0
                        || groupUsage.getLastAccessTime() < startTime) {
                    continue;
                }
                final boolean isSystemApp = Utils.isSystem(appUsage.getApp(), mLauncherPkgs);
                if (!mHasSystemApps) {
                    if (isSystemApp) {
                        mHasSystemApps = true;
                        getActivity().invalidateOptionsMenu();
                    }
                }
                if (isSystemApp && !mShowSystem) {
                    continue;
                }
                // STOPSHIP: Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
                if (groupUsage.getGroup().getLabel().equals("Storage")) {
                    continue;
                }

                usages.add(Pair.create(appUsage, appGroups.get(groupNum)));
            }
        }

        // Update header.
        if (mFilterGroup == null) {
            final BarChartPreference barChart = createBarChart(usages, timeFilterItem, context);
            screen.addPreference(barChart);
            hideHeader();
        } else {
            AppPermissionGroup group = getGroup(mFilterGroup);
            if (group != null) {
                setHeader(Utils.applyTint(context, context.getDrawable(group.getIconResId()),
                        android.R.attr.colorControlNormal),
                        context.getString(R.string.app_permission_usage_filter_label,
                                group.getLabel()), false);
                setSummary(context.getString(R.string.app_permission_usage_remove_filter), v -> {
                    mFilterGroup = null;
                    // We already loaded all data, so don't reload
                    updateUI();
                });
            }
        }

        // Add the preference header.
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        if (timeFilterItem != null) {
            category.setTitle(timeFilterItem.getListTitleRes());
        }

        // Sort the apps.
        final int sortOption = getSelectedSortOption();
        if (sortOption == SORT_RECENT) {
            usages.sort(PermissionUsageFragment::compareAccessRecency);
        } else if (sortOption == SORT_MOST_PERMISSIONS) {
            usages.sort(PermissionUsageFragment::compareAccessUsage);
        } else if (sortOption == SORT_MOST_ACCESSES) {
            usages.sort(PermissionUsageFragment::compareAccessCount);
        } else {
            Log.w(LOG_TAG, "Unexpected sort option: " + sortOption);
        }

        ExpandablePreferenceGroup parent = null;
        AppPermissionUsage lastAppPermissionUsage = null;

        final int numUsages = usages.size();
        for (int usageNum = 0; usageNum < numUsages; usageNum++) {
            final Pair<AppPermissionUsage, GroupUsage> usage = usages.get(usageNum);
            AppPermissionUsage appPermissionUsage = usage.first;
            GroupUsage groupUsage = usage.second;

            if (mFilterGroup != null && !mFilterGroup.equals(groupUsage.getGroup().getName())) {
                continue;
            }

            String accessTimeString = Utils.getAbsoluteLastUsageString(context, groupUsage);

            if (lastAppPermissionUsage != appPermissionUsage) {
                // Add a "parent" entry for the app that will expand to the individual entries.
                parent = createExpandablePreferenceGroup(context, appPermissionUsage,
                        sortOption == SORT_RECENT ? accessTimeString : null);
                category.addPreference(parent);
                lastAppPermissionUsage = appPermissionUsage;
            }

            parent.addPreference(createPermissionUsagePreference(context, appPermissionUsage,
                    groupUsage, accessTimeString));
            parent.addSummaryIcon(groupUsage.getGroup().getIconResId());
        }

        // If there are no entries, don't show anything.
        if (parent == null) {
            screen.removeAll();
        }
    }

    private TimeFilterItem getSelectedFilterItem() {
        // Get the current values of the time filter.
        final int pos = mFilterSpinnerTime.getSelectedItemPosition();
        TimeFilterItem timeFilterItem = null;
        if (pos != AdapterView.INVALID_POSITION) {
            timeFilterItem = mFilterAdapterTime.getFilter(pos);
        }
        return timeFilterItem;
    }

    private int getSelectedSortOption() {
        final int pos = mSortSpinner.getSelectedItemPosition();
        if (pos == AdapterView.INVALID_POSITION) {
            return SORT_MOST_PERMISSIONS;
        }
        return mSortAdapter.getFilter(pos).getSortOption();
    }

    /**
     * Reloads the data to show.
     */
    private void reloadData() {
        final TimeFilterItem timeFilterItem = getSelectedFilterItem();
        if (timeFilterItem == null) {
            return;
        }
        final long filterTimeBeginMillis = Math.max(System.currentTimeMillis()
                - timeFilterItem.getTime(), 0);
        mPermissionUsages.load(null /*filterPackageName*/, null,
                filterTimeBeginMillis, Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST
                        | PermissionUsages.USAGE_FLAG_HISTORICAL, getActivity().getLoaderManager(),
                true /*getUiInfo*/, this /*callback*/, false /*sync*/);
    }

    /**
     * Create a bar chart showing the permissions that are used by the most apps.
     *
     * @param usages the usages
     * @param timeFilterItem the time filter, or null if no filter is set
     * @param context the context
     *
     * @return the Preference representing the bar chart
     */
    private BarChartPreference createBarChart(
            @NonNull List<Pair<AppPermissionUsage, GroupUsage>> usages,
            @Nullable TimeFilterItem timeFilterItem, @NonNull Context context) {
        BarChartInfo.Builder builder = new BarChartInfo.Builder();
        BarChartPreference barChart = new BarChartPreference(context, null);
        if (timeFilterItem != null) {
            builder.setTitle(timeFilterItem.getGraphTitleRes());
        }

        final ArrayList<AppPermissionGroup> groups = new ArrayList<>();
        final ArrayMap<String, Integer> groupToAppCount = new ArrayMap<>();
        final int usageCount = usages.size();
        for (int i = 0; i < usageCount; i++) {
            final Pair<AppPermissionUsage, GroupUsage> usage = usages.get(i);
            GroupUsage groupUsage = usage.second;
            final Integer count = groupToAppCount.get(groupUsage.getGroup().getName());
            if (count == null) {
                groups.add(groupUsage.getGroup());
                groupToAppCount.put(groupUsage.getGroup().getName(), 1);
            } else {
                groupToAppCount.put(groupUsage.getGroup().getName(), count + 1);
            }
        }

        groups.sort((x, y) -> {
            final int usageDiff = compareLong(groupToAppCount.get(x.getName()),
                    groupToAppCount.get(y.getName()));
            if (usageDiff != 0) {
                return usageDiff;
            }
            // Make sure we lose no data if same
            return y.hashCode() - x.hashCode();
        });

        int numBarsToShow = Math.min(groups.size(), MAXIMUM_NUM_BARS);
        for (int i = 0; i < numBarsToShow; i++) {
            final AppPermissionGroup group = groups.get(i);
            final Drawable icon = Utils.loadDrawable(context.getPackageManager(),
                    group.getIconPkg(), group.getIconResId());
            BarViewInfo barViewInfo = new BarViewInfo(
                    Utils.applyTint(context, icon, android.R.attr.colorControlNormal),
                    // The cast should not be a prob in practice
                    groupToAppCount.get(group.getName()),
                    R.string.app_permission_usage_bar_label);

            barViewInfo.setClickListener(v -> {
                mFilterGroup = group.getName();
                // We already loaded all data, so don't reload
                updateUI();
            });
            builder.addBarViewInfo(barViewInfo);
        }
        barChart.initializeBarChart(builder.build());
        return barChart;
    }

    /**
     * Create an expandable preference group that can hold children.
     *
     * @param context the context
     * @param appPermissionUsage the permission usage for an app
     *
     * @return the expandable preference group.
     */
    private ExpandablePreferenceGroup createExpandablePreferenceGroup(@NonNull Context context,
            @NonNull AppPermissionUsage appPermissionUsage, @Nullable String summaryString) {
        ExpandablePreferenceGroup preference = new ExpandablePreferenceGroup(context);
        preference.setTitle(appPermissionUsage.getApp().getLabel());
        preference.setIcon(appPermissionUsage.getApp().getIcon());
        if (summaryString != null) {
            preference.setSummary(summaryString);
        }
        return preference;
    }

    /**
     * Create a preference representing an app's use of a permission
     *
     * @param context the context
     * @param appPermissionUsage the permission usage for the app
     * @param groupUsage the permission item to add
     * @param accessTimeStr the string representing the access time
     *
     * @return the Preference
     */
    private PermissionControlPreference createPermissionUsagePreference(@NonNull Context context,
            @NonNull AppPermissionUsage appPermissionUsage,
            @NonNull GroupUsage groupUsage, @NonNull String accessTimeStr) {
        final PermissionControlPreference pref = new PermissionControlPreference(context,
                groupUsage.getGroup());

        final AppPermissionGroup group = groupUsage.getGroup();
        pref.setTitle(group.getLabel());
        pref.setUsageSummary(groupUsage, accessTimeStr);
        pref.setTitleIcons(Collections.singletonList(group.getIconResId()));
        pref.setKey(group.getApp().packageName + "," + group.getName());
        pref.useSmallerIcon();
        return pref;
    }

    /**
     * Compare two usages by the number of apps that accessed each group.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * We ignore the GroupUsage here to ensure that we keep all usages by the same app together.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessUsage(@NonNull Pair<AppPermissionUsage, GroupUsage> x,
            @NonNull Pair<AppPermissionUsage, GroupUsage> y) {
        final int groupDiff = getAccessedGroupCount(y.first) - getAccessedGroupCount(x.first);
        if (groupDiff != 0) {
            return groupDiff;
        }
        return compareAccessTime(x.first, y.first);
    }

    /**
     * Gets the number of permission groups that have been accessed by the given app.
     *
     * @param appPermissionUsage The app permission usage.
     * @return The access count.
     */
    private static int getAccessedGroupCount(@NonNull AppPermissionUsage appPermissionUsage) {
        int accessedCount = 0;
        final List<GroupUsage> groupUsages = appPermissionUsage.getGroupUsages();
        final int groupCount = groupUsages.size();
        for (int i = 0; i < groupCount; i++) {
            final GroupUsage groupUsage = groupUsages.get(i);
            // STOPSHIP: Ignore {READ,WRITE}_EXTERNAL_STORAGE since they're going away.
            if (groupUsage.getGroup().getLabel().equals("Storage")) {
                continue;
            }
            if (groupUsage.getAccessCount() > 0) {
                accessedCount++;
            }
        }
        return accessedCount;
    }

    /**
     * Compare two usages by their access time.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessTime(@NonNull Pair<AppPermissionUsage, GroupUsage> x,
            @NonNull Pair<AppPermissionUsage, GroupUsage> y) {
        final int timeDiff = compareLong(x.second.getLastAccessTime(),
                y.second.getLastAccessTime());
        if (timeDiff != 0) {
            return timeDiff;
        }
        // Make sure we lose no data if same
        return x.hashCode() - y.hashCode();
    }

    /**
     * Compare two AppPermissionUsage by their access time.
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
        final int timeDiff = compareLong(x.getLastAccessTime(), y.getLastAccessTime());
        if (timeDiff != 0) {
            return timeDiff;
        }
        // Make sure we lose no data if same
        return x.hashCode() - y.hashCode();
    }

    /**
     * Compare two usages by their access count.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessCount(@NonNull Pair<AppPermissionUsage, GroupUsage> x,
            @NonNull Pair<AppPermissionUsage, GroupUsage> y) {
        final int accessDiff = compareLong(x.second.getAccessCount(), y.second.getAccessCount());
        if (accessDiff != 0) {
            return accessDiff;
        }
        return compareAccessTime(x, y);
    }

    /**
     * Compare two longs.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x the first long.
     * @param y the second long.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareLong(long x, long y) {
        if (x > y) {
            return -1;
        } else if (x < y) {
            return 1;
        }
        return 0;
    }

    /**
     * Compare two usages by recency of access.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessRecency(@NonNull Pair<AppPermissionUsage, GroupUsage> x,
            @NonNull Pair<AppPermissionUsage, GroupUsage> y) {
        final int timeDiff = compareAccessTime(x, y);
        if (timeDiff != 0) {
            return timeDiff;
        }
        final int countDiff = compareAccessUsage(x, y);
        if (countDiff != 0) {
            return countDiff;
        }
        // Make sure we lose no data if same
        return x.hashCode() - y.hashCode();
    }

    /**
     * Get the permission groups declared by the OS.
     *
     * @return a list of the permission groups declared by the OS.
     */
    private @NonNull List<AppPermissionGroup> getOSPermissionGroups() {
        final List<AppPermissionGroup> groups = new ArrayList<>();
        final Set<String> seenGroups = new ArraySet<>();
        final List<AppPermissionUsage> appUsages = mPermissionUsages.getUsages();
        final int numGroups = appUsages.size();
        for (int i = 0; i < numGroups; i++) {
            final AppPermissionUsage appUsage = appUsages.get(i);
            final List<GroupUsage> groupUsages = appUsage.getGroupUsages();
            final int groupUsageCount = groupUsages.size();
            for (int j = 0; j < groupUsageCount; j++) {
                final GroupUsage groupUsage = groupUsages.get(j);
                if (Utils.isModernPermissionGroup(groupUsage.getGroup().getName())) {
                    if (seenGroups.add(groupUsage.getGroup().getName())) {
                        groups.add(groupUsage.getGroup());
                    }
                }
            }
        }
        return groups;
    }

    /**
     * Get an AppPermissionGroup that represents the given permission group (and an arbitrary app).
     *
     * @param groupName The name of the permission group.
     *
     * @return an AppPermissionGroup rerepsenting the given permission group or null if no such
     * AppPermissionGroup is found.
     */
    private @Nullable AppPermissionGroup getGroup(@NonNull String groupName) {
        List<AppPermissionGroup> groups = getOSPermissionGroups();
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            if (groups.get(i).getName().equals(groupName)) {
                return groups.get(i);
            }
        }
        return null;
    }

    /**
     * Show a dialog that allows selecting a permission group by which to filter the entries.
     */
    private void showPermissionFilterDialog() {
        Context context = getPreferenceManager().getContext();

        // Get the permission labels.
        List<AppPermissionGroup> groups = getOSPermissionGroups();
        groups.sort(
                (x, y) -> mCollator.compare(x.getLabel().toString(), y.getLabel().toString()));

        // Create the spinner entries.
        String[] groupNames = new String[groups.size() + 1];
        CharSequence[] groupLabels = new CharSequence[groupNames.length];
        groupNames[0] = null;
        groupLabels[0] = context.getString(R.string.permission_usage_any_permission);
        int selection = 0;
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            AppPermissionGroup group = groups.get(i);
            groupNames[i + 1] = group.getName();
            groupLabels[i + 1] = group.getLabel();
            if (group.getName().equals(mFilterGroup)) {
                selection = i + 1;
            }
        }

        // Create the dialog
        Bundle args = new Bundle();
        args.putCharSequence(PermissionsFilterDialog.TITLE,
                context.getString(R.string.filter_by_title));
        args.putCharSequenceArray(PermissionsFilterDialog.ELEMS, groupLabels);
        args.putInt(PermissionsFilterDialog.SELECTION, selection);
        PermissionsFilterDialog chooserDialog = new PermissionsFilterDialog(this, groupNames);
        chooserDialog.setArguments(args);
        chooserDialog.show(getChildFragmentManager().beginTransaction(), "backgroundChooser");
    }

    /**
     * Callback when the user selects a permission group by which to filter.
     *
     * @param selectedGroup The PermissionGroup to use to filter entries, or null if we should show
     *                      all entries.
     */
    private void onPermissionGroupSelected(@Nullable String selectedGroup) {
        mFilterGroup = selectedGroup;
        // We already loaded all data, so don't reload
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
        private @NonNull String[] mGroups;

        public PermissionsFilterDialog(@NonNull PermissionUsageFragment fragment,
                @NonNull String[] groups) {
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
