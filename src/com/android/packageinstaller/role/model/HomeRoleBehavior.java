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

package com.android.packageinstaller.role.model;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.packageinstaller.role.utils.UserUtils;
import com.android.permissioncontroller.R;

import java.util.Objects;

/**
 * Class for behavior of the home role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultHomePreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultHomePicker
 */
public class HomeRoleBehavior implements RoleBehavior {

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return !UserUtils.isWorkProfile(user, context);
    }

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        // Home is not available for work profile, so we can just use the current user.
        boolean isSettingsApplication = isSettingsApplication(applicationInfo, context);
        preference.setVisible(!isSettingsApplication);
        boolean missingWorkProfileSupport = isMissingWorkProfileSupport(applicationInfo, context);
        preference.setEnabled(!missingWorkProfileSupport);
        preference.setSummary(missingWorkProfileSupport ? context.getString(
                R.string.home_missing_work_profile_support) : null);
    }

    private boolean isMissingWorkProfileSupport(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        boolean hasWorkProfile = UserUtils.getWorkProfile(context) != null;
        if (!hasWorkProfile) {
            return false;
        }
        boolean isWorkProfileSupported = applicationInfo.targetSdkVersion
                >= Build.VERSION_CODES.LOLLIPOP;
        return !isWorkProfileSupported;
    }

    private boolean isSettingsApplication(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        ResolveInfo resolveInfo = packageManager.resolveActivity(new Intent(
                Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY
                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return false;
        }
        return Objects.equals(applicationInfo.packageName, resolveInfo.activityInfo.packageName);
    }
}
