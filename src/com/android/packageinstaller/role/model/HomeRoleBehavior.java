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

import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.packageinstaller.permission.utils.CollectionUtils;
import com.android.packageinstaller.role.ui.TwoTargetPreference;
import com.android.packageinstaller.role.utils.UserUtils;
import com.android.permissioncontroller.R;

import java.util.List;
import java.util.Objects;

/**
 * Class for behavior of the home role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultHomePreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultHomePicker
 */
public class HomeRoleBehavior implements RoleBehavior {

    private static final String LOG_TAG = HomeRoleBehavior.class.getSimpleName();

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return !UserUtils.isWorkProfile(user, context);
    }

    /**
     * @see com.android.server.pm.PackageManagerService#getDefaultHomeActivity(int)
     */
    @Nullable
    @Override
    public String getFallbackHolder(@NonNull Role role, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = role.getRequiredComponents().get(0).getIntentFilterData().createIntent();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);

        String packageName = null;
        int priority = Integer.MIN_VALUE;
        int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);

            // Leave the fallback to PackageManagerService if there is only the fallback home in
            // Settings, because if we fallback to it here, we cannot fallback to a normal home
            // later, and user cannot see the fallback home in the UI anyway.
            if (isSettingsApplication(resolveInfo.activityInfo.applicationInfo, context)) {
                continue;
            }
            if (resolveInfo.priority > priority) {
                packageName = resolveInfo.activityInfo.packageName;
                priority = resolveInfo.priority;
            } else if (resolveInfo.priority == priority) {
                packageName = null;
            }
        }
        return packageName;
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showDefaultHome", context);
    }

    @Override
    public void preparePreferenceAsUser(@NonNull Role role, @NonNull TwoTargetPreference preference,
            @NonNull UserHandle user, @NonNull Context context) {
        TwoTargetPreference.OnSecondTargetClickListener listener = null;
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        String packageName = CollectionUtils.firstOrNull(roleManager.getRoleHoldersAsUser(
                role.getName(), user));
        if (packageName != null) {
            Intent intent = new Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PackageManager userPackageManager = UserUtils.getUserContext(context, user)
                    .getPackageManager();
            ActivityInfo activityInfo = intent.resolveActivityInfo(userPackageManager, 0);
            if (activityInfo != null && activityInfo.exported) {
                listener = preference2 -> {
                    try {
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(LOG_TAG, "Cannot start activity for home app preferences", e);
                    }
                };
            }
        }
        preference.setOnSecondTargetClickListener(listener);
    }

    @Override
    public boolean isApplicationVisibleAsUser(@NonNull Role role,
            @NonNull ApplicationInfo applicationInfo, @NonNull UserHandle user,
            @NonNull Context context) {
        // Home is not available for work profile, so we can just use the current user.
        return !isSettingsApplication(applicationInfo, context);
    }

    @Override
    public void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
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

    @Override
    public void onHolderSelectedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        // Launch the new home app so the change is immediately visible even if the home button is
        // not pressed.
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
