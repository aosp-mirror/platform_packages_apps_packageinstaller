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

package com.android.permissioncontroller.role.ui.auto;

import android.content.Context;
import android.widget.RadioButton;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;

import com.android.permissioncontroller.R;

/** Preference used to represent apps that can be picked as a default app. */
public class AutoDefaultAppPreference extends TwoStatePreference {

    public AutoDefaultAppPreference(Context context) {
        super(context, null, TypedArrayUtils.getAttr(context, R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
        init();
    }

    private void init() {
        setLayoutResource(R.layout.car_radio_button_preference);
        setWidgetLayoutResource(R.layout.radio_button_preference_widget);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        RadioButton radioButton = (RadioButton) holder.findViewById(R.id.radio_button);
        radioButton.setChecked(isChecked());
    }
}

