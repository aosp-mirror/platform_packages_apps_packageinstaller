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

package com.android.packageinstaller.permission.ui.auto;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/** {@link Preference} with the widget layout as a separate target. */
public class AutoTwoTargetPreference extends Preference {

    private OnSecondTargetClickListener mListener;
    private boolean mIsDividerVisible = true;

    public AutoTwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public AutoTwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public AutoTwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AutoTwoTargetPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.car_two_target_preference);
    }

    /** Set the listener for second target click. */
    public void setOnSecondTargetClickListener(@Nullable OnSecondTargetClickListener listener) {
        mListener = listener;
        notifyChanged();
    }

    /** Sets the visibility of the divider. */
    public void setDividerVisible(boolean visible) {
        mIsDividerVisible = visible;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View actionContainer = holder.findViewById(R.id.action_widget_container);
        View divider = holder.findViewById(R.id.two_target_divider);
        FrameLayout widgetFrame = (FrameLayout) holder.findViewById(android.R.id.widget_frame);
        if (mListener != null) {
            actionContainer.setVisibility(View.VISIBLE);
            divider.setVisibility(mIsDividerVisible ? View.VISIBLE : View.GONE);
            widgetFrame.setVisibility(View.VISIBLE);
            widgetFrame.setOnClickListener(v -> mListener.onSecondTargetClick(this));
        } else {
            actionContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Listener for second target click.
     */
    public interface OnSecondTargetClickListener {

        /**
         * Callback when the second target is clicked.
         *
         * @param preference the {@link AutoTwoTargetPreference} that was clicked
         */
        void onSecondTargetClick(@NonNull AutoTwoTargetPreference preference);
    }
}
