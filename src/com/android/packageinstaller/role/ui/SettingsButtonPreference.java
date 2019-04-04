/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.role.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/**
 * {@link Preference} with a settings button.
 *
 * @see com.android.settings.widget.GearPreference
 */
public class SettingsButtonPreference extends TwoTargetPreference {

    @Nullable
    private OnSettingsButtonClickListener mOnSettingsButtonClickListener;

    public SettingsButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    public SettingsButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    public SettingsButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public SettingsButtonPreference(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.settings_button_preference_widget);
    }

    /**
     * Set the listener for settings button click.
     *
     * @param listener the listener
     */
    public void setOnSettingsButtonClickListener(@Nullable OnSettingsButtonClickListener listener) {
        mOnSettingsButtonClickListener = listener;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View widgetFrame = holder.findViewById(android.R.id.widget_frame);
        widgetFrame.setPadding(0, 0, 0, 0);
        View settingsButton = holder.findViewById(R.id.settings_button);
        if (mOnSettingsButtonClickListener != null) {
            widgetFrame.setVisibility(View.VISIBLE);
            settingsButton.setOnClickListener(view ->
                    mOnSettingsButtonClickListener.onSettingsButtonClick(this));
        } else {
            widgetFrame.setVisibility(View.GONE);
            settingsButton.setOnClickListener(null);
        }
        // Make the settings button enabled even if the preference itself is disabled.
        settingsButton.setEnabled(true);
    }

    /**
     * Listener for settings button click.
     */
    public interface OnSettingsButtonClickListener {

        /**
         * Callback when settings button is clicked.
         *
         * @param preference the {@link SettingsButtonPreference} that was clicked
         */
        void onSettingsButtonClick(@NonNull SettingsButtonPreference preference);
    }
}
