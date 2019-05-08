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
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.packageinstaller.role.utils.UiUtils;
import com.android.permissioncontroller.R;

/**
 * {@link Preference} acting as the footer of a page.
 */
class FooterPreference extends Preference {

    private static final int ICON_LAYOUT_PADDING_VERTICAL_DP = 16;

    FooterPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    FooterPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    FooterPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    FooterPreference(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        setIcon(R.drawable.ic_info_outline);
        setSelectable(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.setDividerAllowedAbove(true);

        View iconFrame = holder.findViewById(R.id.icon_frame);
        LinearLayout.LayoutParams iconFrameLayoutParams = (LinearLayout.LayoutParams)
                iconFrame.getLayoutParams();
        iconFrameLayoutParams.gravity = Gravity.TOP;
        iconFrame.setLayoutParams(iconFrameLayoutParams);
        int iconFramePaddingVertical = UiUtils.dpToPxOffset(ICON_LAYOUT_PADDING_VERTICAL_DP,
                iconFrame.getContext());
        iconFrame.setPaddingRelative(iconFrame.getPaddingStart(), iconFramePaddingVertical,
                iconFrame.getPaddingEnd(), iconFramePaddingVertical);
    }
}
