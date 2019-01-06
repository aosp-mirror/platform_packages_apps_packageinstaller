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

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

/**
 * Class for determining whether the SMS role is available.
 *
 * @see com.android.settings.applications.DefaultAppSettings
 * @see com.android.settings.applications.defaultapps.DefaultSmsPreferenceController#isAvailable()
 */
public class SmsRoleAvailabilityProvider implements RoleAvailabilityProvider {

    @Override
    public boolean isRoleAvailableAsUser(@NonNull UserHandle user, @NonNull Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        if (userManager.isManagedProfile(user.getIdentifier())) {
            return false;
        }
        // FIXME: STOPSHIP: Add an appropriate @SystemApi for this.
        //if (userManager.getUserInfo(user.getIdentifier()).isRestricted()) {
        //    return false;
        //}
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        if (!telephonyManager.isSmsCapable()) {
            return false;
        }
        return true;
    }
}
