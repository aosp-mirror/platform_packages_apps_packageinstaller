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

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A preference group that expands/collapses its children when clicked.
 */
public class ExpandablePreferenceGroup extends PreferenceGroup {
    private @NonNull Context mContext;
    private @NonNull List<Preference> mPreferences;
    private @NonNull List<Pair<Integer, CharSequence>> mSummaryIcons;
    private boolean mExpanded;

    public ExpandablePreferenceGroup(@NonNull Context context) {
        super(context, null);

        mContext = context;
        mPreferences = new ArrayList<>();
        mSummaryIcons = new ArrayList<>();
        mExpanded = false;

        setLayoutResource(R.layout.preference_usage);
        setWidgetLayoutResource(R.layout.image_view);
        setOnPreferenceClickListener(preference -> {
            if (!mExpanded) {
                int numPreferences = mPreferences.size();
                for (int i = 0; i < numPreferences; i++) {
                    super.addPreference(mPreferences.get(i));
                }
            } else {
                removeAll();
            }
            mExpanded = !mExpanded;
            return true;
        });
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
        int rightIconSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.secondary_app_icon_size);
        icon.setMaxWidth(rightIconSize);
        icon.setMaxHeight(rightIconSize);

        super.onBindViewHolder(holder);

        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        summary.setMaxLines(1);
        summary.setEllipsize(TextUtils.TruncateAt.END);

        ImageView rightImageView = holder.findViewById(
                android.R.id.widget_frame).findViewById(R.id.icon);
        if (mExpanded) {
            rightImageView.setImageResource(R.drawable.ic_arrow_up);
        } else {
            rightImageView.setImageResource(R.drawable.ic_arrow_down);
        }

        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        holder.findViewById(R.id.title_widget_frame).setVisibility(View.GONE);

        ViewGroup summaryFrame = (ViewGroup) holder.findViewById(R.id.summary_widget_frame);
        if (mSummaryIcons.isEmpty()) {
            summaryFrame.setVisibility(View.GONE);
        } else {
            summaryFrame.removeAllViews();
            int numIcons = mSummaryIcons.size();
            for (int i = 0; i < numIcons; i++) {
                LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
                ViewGroup group = (ViewGroup) inflater.inflate(R.layout.title_summary_image_view,
                        null);
                ImageView imageView = group.requireViewById(R.id.icon);
                Pair<Integer, CharSequence> summaryIcons = mSummaryIcons.get(i);
                imageView.setImageResource(summaryIcons.first);
                if (summaryIcons.second != null) {
                    imageView.setContentDescription(summaryIcons.second);
                }
                summaryFrame.addView(group);
            }
        }
    }

    @Override
    public boolean addPreference(Preference preference) {
        mPreferences.add(preference);
        return true;
    }

    /**
     * Show the given icon next to this preference's summary.
     *
     * @param resId the resourceId of the drawable to use as the icon.
     */
    public void addSummaryIcon(@DrawableRes int resId, @Nullable CharSequence contentDescription) {
        mSummaryIcons.add(Pair.create(resId, contentDescription));
    }
}
