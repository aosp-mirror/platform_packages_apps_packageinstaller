/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.packageinstaller.permission.ui.wear.settings;

import android.content.Context;
import android.content.res.Resources;
import android.support.wearable.view.CircledImageView;
import android.support.wearable.view.WearableListView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;

import java.util.ArrayList;

public final class PermissionsSettingsAdapter extends SettingsAdapter<AppPermissionGroup> {
    private Resources mRes;

    public PermissionsSettingsAdapter(Context context) {
        super(context, R.layout.permissions_settings_item);
        mRes = context.getResources();
    }

    @Override
    public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new PermissionsViewHolder(new SettingsAdapter.SettingsItem(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        PermissionsViewHolder viewHolder = (PermissionsViewHolder) holder;
        AppPermissionGroup group = get(position).data;

        if (group.isPolicyFixed()) {
            viewHolder.imageView.setEnabled(false);
            viewHolder.textView.setEnabled(false);
            viewHolder.state.setEnabled(false);
            viewHolder.state.setText(
                    mRes.getString(R.string.permission_summary_enforced_by_policy));
        } else {
            viewHolder.imageView.setEnabled(true);
            viewHolder.textView.setEnabled(true);
            viewHolder.state.setEnabled(true);

            if (group.areRuntimePermissionsGranted()) {
                viewHolder.state.setText(R.string.generic_enabled);
            } else {
                viewHolder.state.setText(R.string.generic_disabled);
            }
        }
    }

    private static final class PermissionsViewHolder extends SettingsAdapter.SettingsItemHolder {
        public final TextView state;

        public PermissionsViewHolder(View view) {
            super(view);
            state = (TextView) view.findViewById(R.id.state);
        }
    }
}

