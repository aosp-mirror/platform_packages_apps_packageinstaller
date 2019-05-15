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

package com.android.packageinstaller.role.ui.auto;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;

import com.android.permissioncontroller.R;

/** Preference used to represent apps that can be picked as a default app. */
public class AutoDefaultAppPreference extends TwoStatePreference {

    public AutoDefaultAppPreference(Context context) {
        super(context, null, TypedArrayUtils.getAttr(context, R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView == null) {
            return;
        }

        if (isChecked()) {
            CharSequence current = getSummary();
            CharSequence selected = getContext().getString(R.string.car_default_app_selected);
            if (!TextUtils.isEmpty(current)) {
                selected = getContext().getString(R.string.car_default_app_selected_with_info,
                        current);
            }
            summaryView.setText(selected);
            summaryView.setVisibility(View.VISIBLE);
        } else {
            summaryView.setVisibility(View.GONE);
        }
    }
}
