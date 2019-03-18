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
import android.content.pm.PackageInfo;
import android.os.Process;
import android.os.UserHandle;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.List;

/**
 * Class for behavior of the emergency role.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultEmergencyPreferenceController
 * @see com.android.settings.applications.defaultapps.DefaultEmergencyPicker
 */
public class EmergencyRoleBehavior implements RoleBehavior {

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        return telephonyManager.isEmergencyAssistanceEnabled() && telephonyManager.isVoiceCapable();
    }

    @Nullable
    @Override
    public String getFallbackHolder(@NonNull Role role, @NonNull Context context) {
        List<String> packageNames = role.getQualifyingPackagesAsUser(Process.myUserHandle(),
                context);
        PackageInfo fallbackPackageInfo = null;
        int packageNamesSize = packageNames.size();
        for (int i = 0; i < packageNamesSize; i++) {
            String packageName = packageNames.get(i);

            PackageInfo packageInfo = PackageUtils.getPackageInfo(packageName, 0, context);
            if (packageInfo == null) {
                continue;
            }
            if (fallbackPackageInfo == null || packageInfo.firstInstallTime
                    < fallbackPackageInfo.firstInstallTime) {
                fallbackPackageInfo = packageInfo;
            }
        }
        return fallbackPackageInfo != null ? fallbackPackageInfo.packageName : null;
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showDefaultEmergency", context);
    }

    @Nullable
    @Override
    public CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return EncryptionUnawareConfirmationMixin.getConfirmationMessage(role, packageName,
                context);
    }
}
