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

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

/**
 * {@link Preference} with its icon view set to a fixed size for app icons.
 */
class AppIconPreference extends Preference {

    private Mixin mMixin;

    AppIconPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    AppIconPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    AppIconPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    AppIconPreference(@NonNull Context context) {
        super(context);

        init();
    }

    private void init() {
        mMixin = new Mixin(getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mMixin.onBindViewHolder(holder);
    }

    /**
     * Mixin for implementation of {@link AppIconPreference}.
     */
    public static class Mixin {

        @Px
        private int mIconSize;

        Mixin(@NonNull Context context) {
            mIconSize = context.getResources().getDimensionPixelSize(
                    R.dimen.secondary_app_icon_size);
        }

        /**
         * Binds the view holder so that its icon view is set to a fixed size for app icons.
         *
         * @param holder the view holder passed in by {@link Preference#onBindViewHolder(
         *               PreferenceViewHolder)}
         *
         * @see Preference#onBindViewHolder(PreferenceViewHolder)
         */
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
            View iconView = holder.findViewById(android.R.id.icon);
            ViewGroup.LayoutParams layoutParams = iconView.getLayoutParams();
            boolean changed = false;
            if (layoutParams.width != mIconSize) {
                layoutParams.width = mIconSize;
                changed = true;
            }
            if (layoutParams.height != mIconSize) {
                layoutParams.height = mIconSize;
                changed = true;
            }
            if (changed) {
                iconView.requestLayout();
            }
        }
    }
}
