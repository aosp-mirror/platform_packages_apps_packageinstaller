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

package com.android.packageinstaller.permission.ui.handheld;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.ui.AppPermissionActivity;
import com.android.permissioncontroller.R;

/**
 * A preference for representing a permission usage by an app.
 */
public class PermissionUsagePreference extends Preference {
    private final @NonNull AppPermissionGroup mGroup;
    private final @Nullable Drawable mWidgetIcon;

    public PermissionUsagePreference(@NonNull Context context, @NonNull AppPermissionGroup group,
            @Nullable Drawable widgetIcon) {
        super(context);
        mGroup = group;
        mWidgetIcon = widgetIcon;
        if (mWidgetIcon != null) {
            setWidgetLayoutResource(R.layout.image_view);
        }
        setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, AppPermissionActivity.class);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mGroup.getApp().packageName);
            intent.putExtra(Intent.EXTRA_PERMISSION_NAME, mGroup.getName());
            intent.putExtra(Intent.EXTRA_USER, mGroup.getUser());
            context.startActivity(intent);
            return true;
        });
    }

    public PermissionUsagePreference(@NonNull Context context, @NonNull AppPermissionGroup group) {
        this(context, group, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (mWidgetIcon != null) {
            View widgetFrame = holder.findViewById(android.R.id.widget_frame);
            ((ImageView) widgetFrame.findViewById(R.id.icon)).setImageDrawable(mWidgetIcon);
        }
    }
}
