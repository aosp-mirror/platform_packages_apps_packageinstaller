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

package com.android.packageinstaller.role.ui.handheld;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioButton;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;

import com.android.permissioncontroller.R;

/**
 * {@link TwoStatePreference} with a radio button.
 *
 * @see com.android.settings.widget.RadioButtonPreference
 */
class RadioButtonPreference extends TwoStatePreference {

    @NonNull
    private final OnCheckedChangeListener mOnCheckedChangeListener = new OnCheckedChangeListener();

    RadioButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setWidgetLayoutResource(R.layout.radio_button_preference_widget);
    }

    RadioButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    RadioButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        // TwoStatePreference(Context, AttributeSet) breaks the default style attribute in
        // Preference(Context, AttributeSet), so we need to add it back here.
        this(context, attrs, TypedArrayUtils.getAttr(context, R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    RadioButtonPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ViewGroup itemView = (ViewGroup) holder.itemView;
        View widgetFrame = holder.findViewById(android.R.id.widget_frame);
        if (itemView.indexOfChild(widgetFrame) != 0) {
            widgetFrame.setPaddingRelative(widgetFrame.getPaddingEnd(), widgetFrame.getPaddingTop(),
                    widgetFrame.getPaddingStart(), widgetFrame.getPaddingBottom());
            itemView.removeView(widgetFrame);
            itemView.addView(widgetFrame, 0);
            itemView.setPaddingRelative(0, itemView.getPaddingTop(), itemView.getPaddingEnd(),
                    itemView.getPaddingBottom());
        }

        RadioButton radioButton = (RadioButton) holder.findViewById(R.id.radio_button);
        radioButton.setOnCheckedChangeListener(null);
        radioButton.setChecked(mChecked);
        radioButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
    }

    private class OnCheckedChangeListener implements CompoundButton.OnCheckedChangeListener {

        OnCheckedChangeListener() {}

        @Override
        public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
            if (!callChangeListener(isChecked)) {
                buttonView.setChecked(!isChecked);
                return;
            }
            setChecked(isChecked);
        }
    }
}
