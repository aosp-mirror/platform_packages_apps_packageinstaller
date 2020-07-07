/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.permissioncontroller.permission.ui.television;

import android.content.Context;
import android.view.View;
import android.widget.RadioButton;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;

public class RadioButtonPreference extends Preference {

    private View.OnClickListener mOnClickListener = null;
    private PreferenceViewHolder mViewHolder = null;
    private boolean mIsChecked = false;

    public RadioButtonPreference(Context context, int titleResId) {
        super(context);
        setWidgetLayoutResource(R.layout.radio_button_preference_widget);
        setTitle(titleResId);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder viewHolder) {
        super.onBindViewHolder(viewHolder);
        final RadioButton rb = (RadioButton) viewHolder.findViewById(R.id.radio_button);
        rb.setChecked(mIsChecked);
        mViewHolder = viewHolder;
    }

    @Override
    public void onClick() {
        super.onClick();
        setChecked(true);
    }

    public void setChecked(boolean isChecked) {
        mIsChecked = isChecked;
        if (mViewHolder != null) {
            ((RadioButton) mViewHolder.findViewById(R.id.radio_button))
                    .setChecked(mIsChecked);
        }
    }
}
