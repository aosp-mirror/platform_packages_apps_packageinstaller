/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.debug;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Html;
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
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp;
import com.android.permissioncontroller.permission.ui.handheld.PermissionControlPreference;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.ActionBarShadowController;
import com.android.settingslib.widget.BarChartInfo;
import com.android.settingslib.widget.BarChartPreference;
import com.android.settingslib.widget.BarViewInfo;

import java.lang.annotation.Retention;
import java.text.Collator;
import java.time.Instant;
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
public class PermissionUsageFragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {
    private static final String LOG_TAG = "PermissionUsageFragment";

    @Retention(SOURCE)
    @IntDef(value = {SORT_RECENT, SORT_RECENT_APPS})
    @interface SortOption {}
    static final int SORT_RECENT = 1;
    static final int SORT_RECENT_APPS = 2;

    private static final int MENU_SORT_BY_APP = MENU_HIDE_SYSTEM + 1;
    private static final int MENU_SORT_BY_TIME = MENU_HIDE_SYSTEM + 2;
    private static final int MENU_FILTER_BY_PERMISSIONS = MENU_HIDE_SYSTEM + 3;
    private static final int MENU_FILTER_BY_TIME = MENU_HIDE_SYSTEM + 4;
    private static final int MENU_REFRESH = MENU_HIDE_SYSTEM + 5;

    private static final String KEY_SHOW_SYSTEM_PREFS = "_show_system";
    private static final String SHOW_SYSTEM_KEY = PermissionUsageFragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;
    private static final String KEY_PERM_NAME = "_perm_name";
    private static final String PERM_NAME_KEY = PermissionUsageFragment.class.getName()
            + KEY_PERM_NAME;
    private static final String KEY_TIME_INDEX = "_time_index";
    private static final String TIME_INDEX_KEY = PermissionUsageFragment.class.getName()
            + KEY_TIME_INDEX;
    private static final String KEY_SORT = "_sort";
    private static final String SORT_KEY = PermissionUsageFragment.class.getName()
            + KEY_SORT;

    /**
     * The maximum number of columns shown in the bar chart.
     */
    private static final int MAXIMUM_NUM_BARS = 4;

    private @NonNull PermissionUsages mPermissionUsages;
    private @Nullable List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();

    private Collator mCollator;

    private @NonNull List<TimeFilterItem> mFilterTimes;
    private int mFilterTimeIndex;
    private String mFilterGroup;
    private @SortOption int mSort;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private MenuItem mSortByApp;
    private MenuItem mSortByTime;

    private ArrayMap<String, Integer> mGroupAppCounts = new ArrayMap<>();

    private boolean mFinishedInitialLoad;

    /**
     * @return A new fragment
     */
    public static @NonNull PermissionUsageFragment newInstance(@Nullable String groupName,
            long numMillis) {
        PermissionUsageFragment fragment = new PermissionUsageFragment();
        Bundle arguments = new Bundle();
        if (groupName != null) {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putLong(Intent.EXTRA_DURATION_MILLIS, numMillis);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFinishedInitialLoad = false;
        mSort = SORT_RECENT_APPS;
        mFilterGroup = null;
        initializeTimeFilter();
        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
            mFilterGroup = savedInstanceState.getString(PERM_NAME_KEY);
            mFilterTimeIndex = savedInstanceState.getInt(TIME_INDEX_KEY);
            mSort = savedInstanceState.getInt(SORT_KEY);
        }

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        if (mFilterGroup == null) {
            mFilterGroup = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        }

        Context context = getPreferenceManager().getContext();
        mCollator = Collator.getInstance(
                context.getResources().getConfiguration().getLocales().get(0));
        mPermissionUsages = new PermissionUsages(context);

        reloadData();
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.permission_usage_title);
    }

    /**
     * Initialize the time filter to show the smallest entry greater than the time passed in as an
     * argument.  If nothing is passed, this simply initializes the possible values.
     */
    private void initializeTimeFilter() {
        Context context = getPreferenceManager().getContext();
        mFilterTimes = new ArrayList<>();
        mFilterTimes.add(new TimeFilterItem(Long.MAX_VALUE,
                context.getString(R.string.permission_usage_any_time),
                R.string.permission_usage_list_title_any_time,
                R.string.permission_usage_bar_chart_title_any_time));
        mFilterTimes.add(new TimeFilterItem(DAYS.toMillis(7),
                context.getString(R.string.permission_usage_last_7_days),
                R.string.permission_usage_list_title_last_7_days,
                R.string.permission_usage_bar_chart_title_last_7_days));
        mFilterTimes.add(new TimeFilterItem(DAYS.toMillis(1),
                context.getString(R.string.permission_usage_last_day),
                R.string.permission_usage_list_title_last_day,
                R.string.permission_usage_bar_chart_title_last_day));
        mFilterTimes.add(new TimeFilterItem(HOURS.toMillis(1),
                context.getString(R.string.permission_usage_last_hour),
                R.string.permission_usage_list_title_last_hour,
                R.string.permission_usage_bar_chart_title_last_hour));
        mFilterTimes.add(new TimeFilterItem(MINUTES.toMillis(15),
                context.getString(R.string.permission_usage_last_15_minutes),
                R.string.permission_usage_list_title_last_15_minutes,
                R.string.permission_usage_bar_chart_title_last_15_minutes));
        mFilterTimes.add(new TimeFilterItem(MINUTES.toMillis(1),
                context.getString(R.string.permission_usage_last_minute),
                R.string.permission_usage_list_title_last_minute,
                R.string.permission_usage_bar_chart_title_last_minute));

        long numMillis = getArguments().getLong(Intent.EXTRA_DURATION_MILLIS);
        long supremum = Long.MAX_VALUE;
        int supremumIndex = -1;
        int numTimes = mFilterTimes.size();
        for (int i = 0; i < numTimes; i++) {
            long curTime = mFilterTimes.get(i).getTime();
            if (curTime >= numMillis && curTime <= supremum) {
                supremum = curTime;
                supremumIndex = i;
            }
        }
        if (supremumIndex != -1) {
            mFilterTimeIndex = supremumIndex;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
        outState.putString(PERM_NAME_KEY, mFilterGroup);
        outState.putInt(TIME_INDEX_KEY, mFilterTimeIndex);
        outState.putInt(SORT_KEY, mSort);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        mSortByApp = menu.add(Menu.NONE, MENU_SORT_BY_APP, Menu.NONE, R.string.sort_by_app);
        mSortByTime = menu.add(Menu.NONE, MENU_SORT_BY_TIME, Menu.NONE, R.string.sort_by_time);
        menu.add(Menu.NONE, MENU_FILTER_BY_PERMISSIONS, Menu.NONE, R.string.filter_by_permissions);
        menu.add(Menu.NONE, MENU_FILTER_BY_TIME, Menu.NONE, R.string.filter_by_time);
        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
        }

        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_permission_usage,
                getClass().getName());
        MenuItem refresh = menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE,
                R.string.permission_usage_refresh);
        refresh.setIcon(R.drawable.ic_refresh);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        updateMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
            case MENU_SORT_BY_APP:
                mSort = SORT_RECENT_APPS;
                updateUI();
                updateMenu();
                break;
            case MENU_SORT_BY_TIME:
                mSort = SORT_RECENT;
                updateUI();
                updateMenu();
                break;
            case MENU_FILTER_BY_PERMISSIONS:
                showPermissionFilterDialog();
                break;
            case MENU_FILTER_BY_TIME:
                showTimeFilterDialog();
                break;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mShowSystem = item.getItemId() == MENU_SHOW_SYSTEM;
                // We already loaded all data, so don't reload
                updateUI();
                updateMenu();
                break;
            case MENU_REFRESH:
                reloadData();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMenu() {
        if (mHasSystemApps) {
            /* Do not show system apps for now
                mShowSystemMenu.setVisible(!mShowSystem);
                mHideSystemMenu.setVisible(mShowSystem);
             */
            mShowSystemMenu.setVisible(false);
            mHideSystemMenu.setVisible(false);
        }

        mSortByApp.setVisible(mSort != SORT_RECENT_APPS);
        mSortByTime.setVisible(mSort != SORT_RECENT);
    }

    @Override
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }
        mAppPermissionUsages = new ArrayList<>(mPermissionUsages.getUsages());

        // Ensure the group name is valid.
        if (getGroup(mFilterGroup) == null) {
            mFilterGroup = null;
        }

        updateUI();
    }

    @Override
    public int getEmptyViewString() {
        return R.string.no_permission_usages;
    }

    private void updateUI() {
        if (mAppPermissionUsages.isEmpty() || getActivity() == null) {
            return;
        }
        Context context = getActivity();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        Preference countsWarningPreference = new Preference(getContext()) {
            @Override
            public void onBindViewHolder(PreferenceViewHolder holder) {
                super.onBindViewHolder(holder);
                ((TextView) holder.itemView.findViewById(android.R.id.title))
                        .setTextColor(Color.RED);
                holder.itemView.setBackgroundColor(Color.YELLOW);
            }
        };

        StringBuffer accounts = new StringBuffer();
        for (UserHandle user : getContext().getSystemService(UserManager.class).getAllProfiles()) {
            for (Account account : getContext().createContextAsUser(user, 0).getSystemService(AccountManager.class).getAccounts()) {
                accounts.append(", " + account.name);
            }
        }
        if (accounts.length() > 0) {
            accounts.delete(0, 2);
        }

        countsWarningPreference.setTitle(Html.fromHtml("<b>INTERNAL ONLY</b> - For debugging.<br/><br/>"
                + "- Access counts do not reflect amount of private data accessed.<br/>"
                + "- Data might not be accurate.<br/><br/>"
                + "Accounts: " + accounts, Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH));
        countsWarningPreference.setIcon(R.drawable.ic_info);
        screen.addPreference(countsWarningPreference);

        boolean seenSystemApp = false;

        final TimeFilterItem timeFilterItem = mFilterTimes.get(mFilterTimeIndex);
        long curTime = System.currentTimeMillis();
        long startTime = Math.max(timeFilterItem == null ? 0 : (curTime - timeFilterItem.getTime()),
                Instant.EPOCH.toEpochMilli());

        List<Pair<AppPermissionUsage, GroupUsage>> usages = new ArrayList<>();
        mGroupAppCounts.clear();
        ArrayList<PermissionApp> permApps = new ArrayList<>();
        int numApps = mAppPermissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appUsage = mAppPermissionUsages.get(appNum);
            boolean used = false;
            List<GroupUsage> appGroups = appUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                GroupUsage groupUsage = appGroups.get(groupNum);
                long lastAccessTime = groupUsage.getLastAccessTime();

                if (groupUsage.getAccessCount() <= 0) {
                    continue;
                }
                if (lastAccessTime == 0) {
                    Log.w(LOG_TAG,
                            "Unexpected access time of 0 for " + appUsage.getApp().getKey() + " "
                                    + groupUsage.getGroup().getName());
                    continue;
                }
                if (lastAccessTime < startTime) {
                    continue;
                }
                final boolean isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(
                        groupUsage.getGroup());
                seenSystemApp = seenSystemApp || isSystemApp;
                if (isSystemApp && !mShowSystem) {
                    continue;
                }

                used = true;
                addGroupUser(groupUsage.getGroup().getName());

                // Filter out usages that aren't of the filtered permission group.
                // We do this after we call addGroupUser so we compute the correct usage counts
                // for the permission filter dialog but before we add the usage to our list.
                if (mFilterGroup != null && !mFilterGroup.equals(groupUsage.getGroup().getName())) {
                    continue;
                }

                usages.add(Pair.create(appUsage, appGroups.get(groupNum)));
            }
            if (used) {
                permApps.add(appUsage.getApp());
                addGroupUser(null);
            }
        }

        if (mHasSystemApps != seenSystemApp) {
            mHasSystemApps = seenSystemApp;
            getActivity().invalidateOptionsMenu();
        }

        // Update header.
        if (mFilterGroup == null) {
            screen.addPreference(createBarChart(usages, timeFilterItem, context));
            hideHeader();
        } else {
            AppPermissionGroup group = getGroup(mFilterGroup);
            if (group != null) {
                setHeader(Utils.applyTint(context, context.getDrawable(group.getIconResId()),
                        android.R.attr.colorControlNormal),
                        context.getString(R.string.app_permission_usage_filter_label,
                                group.getLabel()), null, null, true);
                setSummary(context.getString(R.string.app_permission_usage_remove_filter), v -> {
                    onPermissionGroupSelected(null);
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
        if (mSort == SORT_RECENT) {
            usages.sort(PermissionUsageFragment::compareAccessRecency);
        } else if (mSort == SORT_RECENT_APPS) {
            if (mFilterGroup == null) {
                usages.sort(PermissionUsageFragment::compareAccessAppRecency);
            } else {
                usages.sort(PermissionUsageFragment::compareAccessTime);
            }
        } else {
            Log.w(LOG_TAG, "Unexpected sort option: " + mSort);
        }

        // If there are no entries, don't show anything.
        if (usages.isEmpty()) {
            screen.removeAll();
        }

        new PermissionApps.AppDataLoader(context, () -> {
            ExpandablePreferenceGroup parent = null;
            AppPermissionUsage lastAppPermissionUsage = null;
            String lastAccessTimeString = null;
            List<CharSequence> groups = new ArrayList<>();

            final int numUsages = usages.size();
            for (int usageNum = 0; usageNum < numUsages; usageNum++) {
                final Pair<AppPermissionUsage, GroupUsage> usage = usages.get(usageNum);
                AppPermissionUsage appPermissionUsage = usage.first;
                GroupUsage groupUsage = usage.second;

                String accessTimeString = UtilsKt.getAbsoluteLastUsageString(context, groupUsage);

                if (lastAppPermissionUsage != appPermissionUsage || (mSort == SORT_RECENT
                        && !accessTimeString.equals(lastAccessTimeString))) {
                    setPermissionSummary(parent, groups);
                    // Add a "parent" entry for the app that will expand to the individual entries.
                    parent = createExpandablePreferenceGroup(context, appPermissionUsage,
                            mSort == SORT_RECENT ? accessTimeString : null);
                    category.addPreference(parent);
                    lastAppPermissionUsage = appPermissionUsage;
                    groups = new ArrayList<>();
                }

                parent.addPreference(createPermissionUsagePreference(context, appPermissionUsage,
                        groupUsage, accessTimeString));
                groups.add(groupUsage.getGroup().getLabel());
                lastAccessTimeString = accessTimeString;
            }

            setPermissionSummary(parent, groups);

            setLoading(false, true);
            mFinishedInitialLoad = true;
            setProgressBarVisible(false);
            mPermissionUsages.stopLoader(getActivity().getLoaderManager());
        }).execute(permApps.toArray(new PermissionApps.PermissionApp[permApps.size()]));
    }

    private void addGroupUser(String app) {
        Integer count = mGroupAppCounts.get(app);
        if (count == null) {
            mGroupAppCounts.put(app, 1);
        } else {
            mGroupAppCounts.put(app, count + 1);
        }
    }

    private void setPermissionSummary(@NonNull ExpandablePreferenceGroup pref,
            @NonNull List<CharSequence> groups) {
        if (pref == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            sb.append(groups.get(i));
            if (i < numGroups - 1) {
                sb.append(getString(R.string.item_separator));
            }
        }
        pref.setSummary(sb.toString());
    }

    /**
     * Reloads the data to show.
     */
    private void reloadData() {
        final TimeFilterItem timeFilterItem = mFilterTimes.get(mFilterTimeIndex);
        final long filterTimeBeginMillis = Math.max(System.currentTimeMillis()
                - timeFilterItem.getTime(), Instant.EPOCH.toEpochMilli());
        mPermissionUsages.load(null /*filterPackageName*/, null /*filterPermissionGroups*/,
                filterTimeBeginMillis, Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST
                        | PermissionUsages.USAGE_FLAG_HISTORICAL, getActivity().getLoaderManager(),
                false /*getUiInfo*/, false /*getNonPlatformPermissions*/, this /*callback*/,
                false /*sync*/);
        if (mFinishedInitialLoad) {
            setProgressBarVisible(true);
        }
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
        ArrayList<AppPermissionGroup> groups = new ArrayList<>();
        ArrayMap<String, Integer> groupToAppCount = new ArrayMap<>();
        int usageCount = usages.size();
        for (int i = 0; i < usageCount; i++) {
            Pair<AppPermissionUsage, GroupUsage> usage = usages.get(i);
            GroupUsage groupUsage = usage.second;
            Integer count = groupToAppCount.get(groupUsage.getGroup().getName());
            if (count == null) {
                groups.add(groupUsage.getGroup());
                groupToAppCount.put(groupUsage.getGroup().getName(), 1);
            } else {
                groupToAppCount.put(groupUsage.getGroup().getName(), count + 1);
            }
        }

        groups.sort((x, y) -> {
            String xName = x.getName();
            String yName = y.getName();
            int usageDiff = compareLong(groupToAppCount.get(xName), groupToAppCount.get(yName));
            if (usageDiff != 0) {
                return usageDiff;
            }
            if (xName.equals(LOCATION)) {
                return -1;
            } else if (yName.equals(LOCATION)) {
                return 1;
            } else if (xName.equals(MICROPHONE)) {
                return -1;
            } else if (yName.equals(MICROPHONE)) {
                return 1;
            } else if (xName.equals(CAMERA)) {
                return -1;
            } else if (yName.equals(CAMERA)) {
                return 1;
            }
            return x.getName().compareTo(y.getName());
        });

        BarChartInfo.Builder builder = new BarChartInfo.Builder();
        if (timeFilterItem != null) {
            builder.setTitle(timeFilterItem.getGraphTitleRes());
        }

        int numBarsToShow = Math.min(groups.size(), MAXIMUM_NUM_BARS);
        for (int i = 0; i < numBarsToShow; i++) {
            AppPermissionGroup group = groups.get(i);
            int count = groupToAppCount.get(group.getName());
            Drawable icon = Utils.applyTint(context,
                    Utils.loadDrawable(context.getPackageManager(), group.getIconPkg(),
                            group.getIconResId()), android.R.attr.colorControlNormal);
            BarViewInfo barViewInfo = new BarViewInfo(icon, count, group.getLabel(),
                    context.getResources().getQuantityString(R.plurals.permission_usage_bar_label,
                            count, count), group.getLabel());
            barViewInfo.setClickListener(v -> onPermissionGroupSelected(group.getName()));
            builder.addBarViewInfo(barViewInfo);
        }

        BarChartPreference barChart = new BarChartPreference(context, null);
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
                groupUsage.getGroup(), PermissionUsageFragment.class.getName());

        final AppPermissionGroup group = groupUsage.getGroup();
        pref.setTitle(group.getLabel());
        pref.setUsageSummary(groupUsage, accessTimeStr);
        pref.setTitleIcons(Collections.singletonList(group.getIconResId()));
        pref.setKey(group.getApp().packageName + "," + group.getName());
        pref.useSmallerIcon();
        pref.setRightIcon(context.getDrawable(R.drawable.ic_settings_outline));
        return pref;
    }

    /**
     * Compare two usages by whichever app was used most recently.  If the two represent the same
     * app, sort by which group was used most recently.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessAppRecency(@NonNull Pair<AppPermissionUsage, GroupUsage> x,
            @NonNull Pair<AppPermissionUsage, GroupUsage> y) {
        if (x.first.getApp().getKey().equals(y.first.getApp().getKey())) {
            return compareAccessTime(x.second, y.second);
        }
        return compareAccessTime(x.first, y.first);
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
        return compareAccessTime(x.second, y.second);
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
    private static int compareAccessTime(@NonNull GroupUsage x, @NonNull GroupUsage y) {
        final int timeDiff = compareLong(x.getLastAccessTime(), y.getLastAccessTime());
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
        final int numGroups = mAppPermissionUsages.size();
        for (int i = 0; i < numGroups; i++) {
            final AppPermissionUsage appUsage = mAppPermissionUsages.get(i);
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

        // Create the dialog entries.
        String[] groupNames = new String[groups.size() + 1];
        CharSequence[] groupLabels = new CharSequence[groupNames.length];
        int[] groupAccessCounts = new int[groupNames.length];
        groupNames[0] = null;
        groupLabels[0] = context.getString(R.string.permission_usage_any_permission);
        Integer allAccesses = mGroupAppCounts.get(null);
        if (allAccesses == null) {
            allAccesses = 0;
        }
        groupAccessCounts[0] = allAccesses;
        int selection = 0;
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            AppPermissionGroup group = groups.get(i);
            groupNames[i + 1] = group.getName();
            groupLabels[i + 1] = group.getLabel();
            Integer appCount = mGroupAppCounts.get(group.getName());
            if (appCount == null) {
                appCount = 0;
            }
            groupAccessCounts[i + 1] = appCount;
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
        args.putStringArray(PermissionsFilterDialog.GROUPS, groupNames);
        args.putIntArray(PermissionsFilterDialog.ACCESS_COUNTS, groupAccessCounts);
        PermissionsFilterDialog chooserDialog = new PermissionsFilterDialog();
        chooserDialog.setArguments(args);
        chooserDialog.setTargetFragment(this, 0);
        chooserDialog.show(getFragmentManager().beginTransaction(),
                PermissionsFilterDialog.class.getName());
    }

    /**
     * Callback when the user selects a permission group by which to filter.
     *
     * @param selectedGroup The PermissionGroup to use to filter entries, or null if we should show
     *                      all entries.
     */
    private void onPermissionGroupSelected(@Nullable String selectedGroup) {
        Fragment frag = newInstance(selectedGroup, mFilterTimes.get(mFilterTimeIndex).getTime());
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("PermissionUsage")
                .commit();
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
        private static final String GROUPS = PermissionsFilterDialog.class.getName()
                + ".arg.groups";
        private static final String ACCESS_COUNTS = PermissionsFilterDialog.class.getName()
                + ".arg.access_counts";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setView(createDialogView());

            return b.create();
        }

        private @NonNull View createDialogView() {
            PermissionUsageFragment fragment = (PermissionUsageFragment) getTargetFragment();
            CharSequence[] elems = getArguments().getCharSequenceArray(ELEMS);
            String[] groups = getArguments().getStringArray(GROUPS);
            int[] accessCounts = getArguments().getIntArray(ACCESS_COUNTS);
            int selectedIndex = getArguments().getInt(SELECTION);

            LayoutInflater layoutInflater = LayoutInflater.from(fragment.getActivity());
            View view = layoutInflater.inflate(R.layout.permission_filter_dialog, null);
            ViewGroup itemsListView = view.requireViewById(R.id.items_container);

            ((TextView) view.requireViewById(R.id.title)).setText(
                    getArguments().getCharSequence(TITLE));

            ActionBarShadowController.attachToView(view.requireViewById(R.id.title_container),
                    getLifecycle(), view.requireViewById(R.id.scroll_view));

            for (int i = 0; i < elems.length; i++) {
                String groupName = groups[i];
                View itemView = layoutInflater.inflate(R.layout.permission_filter_dialog_item,
                        itemsListView, false);

                ((TextView) itemView.requireViewById(R.id.title)).setText(elems[i]);
                ((TextView) itemView.requireViewById(R.id.summary)).setText(
                        getActivity().getResources().getQuantityString(
                                R.plurals.permission_usage_permission_filter_subtitle,
                                accessCounts[i], accessCounts[i]));

                itemView.setOnClickListener((v) -> {
                    dismissAllowingStateLoss();
                    fragment.onPermissionGroupSelected(groupName);
                });

                RadioButton radioButton = itemView.requireViewById(R.id.radio_button);
                radioButton.setChecked(i == selectedIndex);
                radioButton.setOnClickListener((v) -> {
                    dismissAllowingStateLoss();
                    fragment.onPermissionGroupSelected(groupName);
                });

                itemsListView.addView(itemView);
            }

            return view;
        }
    }

    private void showTimeFilterDialog() {
        Context context = getPreferenceManager().getContext();

        CharSequence[] labels = new CharSequence[mFilterTimes.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = mFilterTimes.get(i).getLabel();
        }

        // Create the dialog
        Bundle args = new Bundle();
        args.putCharSequence(TimeFilterDialog.TITLE,
                context.getString(R.string.filter_by_title));
        args.putCharSequenceArray(TimeFilterDialog.ELEMS, labels);
        args.putInt(TimeFilterDialog.SELECTION, mFilterTimeIndex);
        TimeFilterDialog chooserDialog = new TimeFilterDialog();
        chooserDialog.setArguments(args);
        chooserDialog.setTargetFragment(this, 0);
        chooserDialog.show(getFragmentManager().beginTransaction(),
                TimeFilterDialog.class.getName());
    }

    /**
     * Callback when the user selects a time by which to filter.
     *
     * @param selectedIndex The index of the dialog option selected by the user.
     */
    private void onTimeSelected(int selectedIndex) {
        mFilterTimeIndex = selectedIndex;
        reloadData();
    }

    /**
     * A dialog that allows the user to select a time by which to filter entries.
     *
     * @see #showTimeFilterDialog()
     */
    public static class TimeFilterDialog extends DialogFragment {
        private static final String TITLE = TimeFilterDialog.class.getName() + ".arg.title";
        private static final String ELEMS = TimeFilterDialog.class.getName() + ".arg.elems";
        private static final String SELECTION = TimeFilterDialog.class.getName() + ".arg.selection";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            PermissionUsageFragment fragment = (PermissionUsageFragment) getTargetFragment();
            CharSequence[] elems = getArguments().getCharSequenceArray(ELEMS);
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(getArguments().getCharSequence(TITLE))
                    .setSingleChoiceItems(elems, getArguments().getInt(SELECTION),
                            (dialog, which) -> {
                                dismissAllowingStateLoss();
                                fragment.onTimeSelected(which);
                            }
                    );

            return b.create();
        }
    }

    /**
     * A class representing a given time, e.g., "in the last hour".
     */
    private static class TimeFilterItem {
        private final long mTime;
        private final @NonNull String mLabel;
        private final @StringRes int mListTitleRes;
        private final @StringRes int mGraphTitleRes;

        TimeFilterItem(long time, @NonNull String label, @StringRes int listTitleRes,
                @StringRes int graphTitleRes) {
            mTime = time;
            mLabel = label;
            mListTitleRes = listTitleRes;
            mGraphTitleRes = graphTitleRes;
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

        public @StringRes int getListTitleRes() {
            return mListTitleRes;
        }

        public @StringRes int getGraphTitleRes() {
            return mGraphTitleRes;
        }
    }
}
