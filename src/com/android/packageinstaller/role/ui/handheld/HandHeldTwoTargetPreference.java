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

package com.android.packageinstaller.role.ui.handheld;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.PreferenceViewHolder;

import com.android.packageinstaller.role.ui.TwoTargetPreference;
import com.android.permissioncontroller.R;

/**
 * Handheld implementation of {@link TwoTargetPreference}.
 */
abstract class HandHeldTwoTargetPreference extends TwoTargetPreference {

    HandHeldTwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    HandHeldTwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    HandHeldTwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    HandHeldTwoTargetPreference(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        setLayoutResource(R.layout.two_target_preference);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View widgetFrame = holder.findViewById(android.R.id.widget_frame);
        ViewGroup widgetFrameParent = (ViewGroup) widgetFrame.getParent();
        ViewGroup itemView = (ViewGroup) holder.itemView;
        if (widgetFrameParent != itemView) {
            widgetFrameParent.removeView(widgetFrame);
            itemView.addView(widgetFrame);
        }
    }
}
