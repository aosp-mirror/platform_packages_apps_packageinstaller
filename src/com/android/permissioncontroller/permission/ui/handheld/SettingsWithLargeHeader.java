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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;

/**
 * A class that contains a header.
 */
public abstract class SettingsWithLargeHeader extends PermissionsFrameFragment  {
    static final String HEADER_KEY = " HEADER_PREFERENCE";
    private static final int HEADER_SORT_FIRST = -2;

    private View mHeader;
    private LargeHeaderPreference mHeaderPreference;
    protected Intent mInfoIntent;
    protected UserHandle mUserHandle;
    protected Drawable mIcon;
    protected CharSequence mLabel;
    protected boolean mSmallIcon;
    private View.OnClickListener mListener;
    private CharSequence mSummary;
    private boolean mShouldShowHeader = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        if (!DeviceUtils.isTelevision(getContext())) {
            if (mHeader != null && mHeader.getVisibility() == View.VISIBLE) {
                updateHeader(mHeader);
                mHeader.requireViewById(R.id.header_link).setVisibility(View.VISIBLE);
            }
        }

        return root;
    }

    @Override
    public void setPreferenceScreen(PreferenceScreen screen) {
        mHeaderPreference = new LargeHeaderPreference(getContext(), this);
        if (screen.findPreference(HEADER_KEY) == null) {
            screen.addPreference(mHeaderPreference);
        }
        super.setPreferenceScreen(screen);
    }

    /**
     * Set the icon and label to use in the header.
     *
     * @param icon the icon
     * @param label the label
     * @param infoIntent the intent to show on click
     * @param smallIcon whether the icon should be small
     */
    public void setHeader(@NonNull Drawable icon, @NonNull CharSequence label,
            Intent infoIntent, @Nullable UserHandle userHandle, boolean smallIcon) {
        mIcon = icon;
        mLabel = label;
        mInfoIntent = infoIntent;
        mUserHandle = userHandle;
        mSmallIcon = smallIcon;
        if (mHeader != null) {
            updateHeader(mHeader);
        }
    }

    /**
     * Updates the header to use the correct icon and title.
     *
     * @param header the View that contains the components.
     */
    protected void updateHeader(@Nullable View header) {
        if (header != null) {
            header.setVisibility(View.VISIBLE);

            ImageView appIcon = header.requireViewById(R.id.entity_header_icon);
            appIcon.setImageDrawable(mIcon);
            if (mSmallIcon) {
                int size = getContext().getResources().getDimensionPixelSize(
                        R.dimen.permission_icon_header_size);
                appIcon.getLayoutParams().width = size;
                appIcon.getLayoutParams().height = size;
            }
            if (mInfoIntent != null) {
                appIcon.setOnClickListener(v -> getActivity().startActivityAsUser(mInfoIntent,
                        mUserHandle));
                appIcon.setContentDescription(mLabel);
            }

            TextView appName = header.requireViewById(R.id.entity_header_title);
            appName.setText(mLabel);

            header.requireViewById(R.id.entity_header_summary).setVisibility(View.GONE);
            header.requireViewById(R.id.entity_header_second_summary).setVisibility(View.GONE);
            header.requireViewById(R.id.header_link).setVisibility(View.GONE);
        }
    }

    /**
     * Hide the entire header.
     */
    public void hideHeader() {
        if (mHeaderPreference == null) {
            mShouldShowHeader = false;
            return;
        }
        mHeaderPreference.setVisible(false);
        mHeader = null;
    }

    /**
     * Set the summary text in the header. If the header has not been created yet, then save the
     * the summary for later.
     *
     * @param summary the text to display
     * @param listener the click listener if the summary should be clickable
     */
    public void setSummary(@NonNull CharSequence summary, @Nullable View.OnClickListener listener) {
        if (mHeader == null) {
            mSummary = summary;
            mListener = listener;
            return;
        }
        TextView textView = mHeader.requireViewById(R.id.header_text);
        TextView linkView = mHeader.requireViewById(R.id.header_link);
        if (listener != null) {
            linkView.setOnClickListener(listener);
            linkView.setVisibility(View.VISIBLE);
            linkView.setText(summary);
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(summary);
            linkView.setVisibility(View.GONE);
        }
    }

    /**
     * A Preference that will act as the "Large Header" for "SettingsWithLargeHeader" fragments.
     */
    public static class LargeHeaderPreference extends PreferenceCategory {
        private SettingsWithLargeHeader mFragment;

        private LargeHeaderPreference(Context context, SettingsWithLargeHeader fragment) {
            super(context);
            mFragment = fragment;
            setVisible(mFragment.mShouldShowHeader);
            setSelectable(false);
            setLayoutResource(R.layout.header_large);
            setKey(HEADER_KEY);

            // display the header first (lower numbers are ordered higher)
            setOrder(HEADER_SORT_FIRST);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            if (!isVisible()) {
                return;
            }
            super.onBindViewHolder(holder);

            View view = holder.itemView;
            if (view == mFragment.mHeader) {
                return;
            }
            mFragment.mHeader = view;
            if (mFragment.mIcon != null) {
                mFragment.updateHeader(view);
            }
            if (mFragment.mSummary != null) {
                mFragment.setSummary(mFragment.mSummary, mFragment.mListener);
            }
        }
    }
}
