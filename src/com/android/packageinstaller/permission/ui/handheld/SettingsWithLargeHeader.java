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

import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.DeviceUtils;
import com.android.permissioncontroller.R;

/**
 * A class that contains a header.
 */
public abstract class SettingsWithLargeHeader extends PermissionsFrameFragment  {

    private View mHeader;
    protected Intent mInfoIntent;
    protected Drawable mIcon;
    protected CharSequence mLabel;
    protected boolean mSmallIcon;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        if (!DeviceUtils.isTelevision(getContext())) {
            if (mHeader == null) {
                mHeader = inflater.inflate(R.layout.header_large, root, false);
                getPreferencesContainer().addView(mHeader, 0);
            } else if (mHeader.getVisibility() == View.VISIBLE) {
                ((ViewGroup) mHeader.getParent()).removeView(mHeader);
                getPreferencesContainer().addView(mHeader, 0);
                updateHeader(mHeader);
                mHeader.requireViewById(R.id.header_link).setVisibility(View.VISIBLE);
            }
        }

        return root;
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
            Intent infoIntent, boolean smallIcon) {
        mIcon = icon;
        mLabel = label;
        mInfoIntent = infoIntent;
        mSmallIcon = smallIcon;
        updateHeader(mHeader);
    }

    /**
     * Updates the header to use the correct icon and title.
     *
     * @param header the View that contains the components.
     */
    protected void updateHeader(@NonNull View header) {
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
        mHeader.setVisibility(View.GONE);
    }

    /**
     * Set the summary text in the header.
     *
     * @param summary the text to display
     * @param listener the click listener if the summary should be clickable
     */
    public void setSummary(@NonNull CharSequence summary, @Nullable View.OnClickListener listener) {
        TextView summaryView = mHeader.requireViewById(R.id.header_link);
        summaryView.setVisibility(View.VISIBLE);
        summaryView.setText(summary);
        if (listener != null) {
            summaryView.setOnClickListener(listener);
        } else {
            TypedArray a = getContext().obtainStyledAttributes(
                    new int[] { android.R.attr.textColorSecondary });
            int color = a.getColor(0, 0);
            a.recycle();
            summaryView.setTextColor(color);
        }
    }
}
