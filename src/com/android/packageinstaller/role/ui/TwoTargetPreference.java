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

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.preference.Preference;

/**
 * {@link Preference} with the widget layout as a separate target.
 *
 * @see com.android.settingslib.TwoTargetPreference
 */
public abstract class TwoTargetPreference extends Preference {

    public TwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public TwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TwoTargetPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoTargetPreference(@NonNull Context context) {
        super(context);
    }

    /**
     * Set the listener for second target click.
     *
     * @param listener the listener
     */
    public abstract void setOnSecondTargetClickListener(
            @Nullable OnSecondTargetClickListener listener);

    /**
     * Listener for second target click.
     */
    public interface OnSecondTargetClickListener {

        /**
         * Callback when the second target is clicked.
         *
         * @param preference the {@link TwoTargetPreference} that was clicked
         */
        void onSecondTargetClick(@NonNull TwoTargetPreference preference);
    }
}
