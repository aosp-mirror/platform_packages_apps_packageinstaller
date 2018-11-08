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

package com.android.packageinstaller.role.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Specifies a required {@code Activity} for an application to qualify for a {@link Role}.
 */
public class RequiredActivity extends RequiredComponent {

    public RequiredActivity(@Nullable String permission,
            @NonNull IntentFilterData intentFilterData) {
        super(permission, intentFilterData);
    }

    @NonNull
    @Override
    protected List<ResolveInfo> queryIntentComponents(@NonNull Intent intent,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
    }

    @NonNull
    @Override
    protected ComponentName getComponentComponentName(@NonNull ResolveInfo resolveInfo) {
        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    @Nullable
    @Override
    protected String getComponentPermission(@NonNull ResolveInfo resolveInfo) {
        return resolveInfo.activityInfo.permission;
    }
}
