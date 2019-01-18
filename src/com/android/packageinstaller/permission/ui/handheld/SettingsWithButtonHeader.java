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

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.DeviceUtils;
import com.android.permissioncontroller.R;

/**
 * A class that contains a header with a row of buttons.
 */
public abstract class SettingsWithButtonHeader extends PermissionsFrameFragment  {

    private View mHeader;
    protected Drawable mIcon;
    protected CharSequence mLabel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        if (!DeviceUtils.isTelevision(getContext())) {
            mHeader = inflater.inflate(R.layout.button_header, root, false);
            getPreferencesContainer().addView(mHeader, 0);
        }

        return root;
    }

    /**
     * Set the icon and label to use in the header.
     *
     * @param icon the icon
     * @param label the label
     * @param showButtons whether to show the app action buttons
     */
    public void setHeader(@NonNull Drawable icon, @NonNull CharSequence label,
            boolean showButtons) {
        mIcon = icon;
        mLabel = label;
        updateHeader(mHeader, showButtons);
    }

    /**
     * Updates the header to use the correct icon, title, and buttons.
     *
     * @param header the View that contains the components.
     * @param showButtons whether to show the app action buttons
     */
    protected void updateHeader(@NonNull View header, boolean showButtons) {
        if (header != null) {
            header.setVisibility(View.VISIBLE);

            ImageView appIcon = header.requireViewById(R.id.entity_header_icon);
            appIcon.setImageDrawable(mIcon);

            TextView appName = header.requireViewById(R.id.entity_header_title);
            appName.setText(mLabel);

            header.requireViewById(R.id.entity_header_summary).setVisibility(View.GONE);
            header.requireViewById(R.id.entity_header_second_summary).setVisibility(View.GONE);
            header.requireViewById(R.id.header_link).setVisibility(View.GONE);

            if (showButtons) {
                Button button1 = header.requireViewById(R.id.button1);
                button1.setText(R.string.launch_app);
                setButtonIcon(button1, R.drawable.ic_open);
                button1.setVisibility(View.VISIBLE);
                button1.setEnabled(false);

                Button button2 = header.requireViewById(R.id.button2);
                button2.setText(R.string.uninstall_app);
                setButtonIcon(button2, R.drawable.ic_delete);
                button2.setVisibility(View.VISIBLE);
                button2.setEnabled(false);

                Button button3 = header.requireViewById(R.id.button3);
                button3.setText(R.string.force_stop_app);
                setButtonIcon(button3, R.drawable.ic_force_stop);
                button3.setVisibility(View.VISIBLE);
                button3.setEnabled(false);
            } else {
                header.requireViewById(R.id.button1).setVisibility(View.GONE);
                header.requireViewById(R.id.button2).setVisibility(View.GONE);
                header.requireViewById(R.id.button3).setVisibility(View.GONE);
            }

            header.requireViewById(R.id.button4).setVisibility(View.GONE);
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
    public void setSummary(@NonNull String summary, @Nullable View.OnClickListener listener) {
        TextView summaryView = mHeader.requireViewById(R.id.header_link);
        summaryView.setVisibility(View.VISIBLE);
        summaryView.setText(summary);
        if (listener != null) {
            summaryView.setOnClickListener(listener);
        }
    }

    private void setButtonIcon(@NonNull Button button, @DrawableRes int iconResId) {
        button.setCompoundDrawablesWithIntrinsicBounds(null, getContext().getDrawable(iconResId),
                null, null);
    }
}
