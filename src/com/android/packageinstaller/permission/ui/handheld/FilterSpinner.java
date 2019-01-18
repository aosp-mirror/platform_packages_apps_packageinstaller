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

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.android.permissioncontroller.R;
import com.android.settingslib.widget.settingsspinner.SettingsSpinnerAdapter;

import java.util.ArrayList;

/**
 * Utility class for using filter spinners.
 */
public class FilterSpinner {

    private FilterSpinner() {
        /* do nothing - hide constructor */
    }

    /**
     * An adapter that stores the entries in a filter spinner.
     *
     * @param <T> The type of the entries in the filter spinner.
     */
    public static class FilterSpinnerAdapter<T extends SpinnerItem> extends
            SettingsSpinnerAdapter<CharSequence> {
        private final ArrayList<T> mFilterOptions = new ArrayList<>();

        FilterSpinnerAdapter(@NonNull Context context) {
            super(context);
        }

        /**
         * Add the given filter to this adapter.
         *
         * @param filter the filter to add
         */
        public void addFilter(@NonNull T filter) {
            mFilterOptions.add(filter);
            notifyDataSetChanged();
        }

        /**
         * Get the filter at the given position.
         *
         * @param position the index of the filter to get.
         *
         * @return the filter at the given index.
         */
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
    public interface SpinnerItem {
        /**
         * Get the label of this item to display to the user.
         *
         * @return the label of this item.
         */
        @NonNull String getLabel();
    }

    /**
     * A spinner item representing a given time, e.g., "in the last hour".
     */
    public static class TimeFilterItem implements SpinnerItem {
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
     * Add time filter entries.
     *
     * @param adapter the filter spinner adapter
     * @param context the context
     */
    public static void addTimeFilters(@NonNull FilterSpinnerAdapter<TimeFilterItem> adapter,
            @NonNull Context context) {
        adapter.addFilter(new TimeFilterItem(Long.MAX_VALUE,
                context.getString(R.string.permission_usage_any_time),
                R.string.permission_usage_bar_chart_title_any_time,
                R.string.permission_usage_list_title_any_time));
        adapter.addFilter(new TimeFilterItem(DAYS.toMillis(7),
                context.getString(R.string.permission_usage_last_7_days),
                R.string.permission_usage_bar_chart_title_last_7_days,
                R.string.permission_usage_list_title_last_7_days));
        adapter.addFilter(new TimeFilterItem(DAYS.toMillis(1),
                context.getString(R.string.permission_usage_last_day),
                R.string.permission_usage_bar_chart_title_last_day,
                R.string.permission_usage_list_title_last_day));
        adapter.addFilter(new TimeFilterItem(HOURS.toMillis(1),
                context.getString(R.string.permission_usage_last_hour),
                R.string.permission_usage_bar_chart_title_last_hour,
                R.string.permission_usage_list_title_last_hour));
        adapter.addFilter(new TimeFilterItem(MINUTES.toMillis(15),
                context.getString(R.string.permission_usage_last_15_minutes),
                R.string.permission_usage_bar_chart_title_last_15_minutes,
                R.string.permission_usage_list_title_last_15_minutes));
        adapter.addFilter(new TimeFilterItem(MINUTES.toMillis(1),
                context.getString(R.string.permission_usage_last_minute),
                R.string.permission_usage_bar_chart_title_last_minute,
                R.string.permission_usage_list_title_last_minute));
    }
}
