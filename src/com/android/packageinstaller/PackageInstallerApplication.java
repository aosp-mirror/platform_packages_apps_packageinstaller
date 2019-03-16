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

package com.android.packageinstaller;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.util.ArrayMap;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.ui.SpecialAppAccessListActivity;

public class PackageInstallerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        PackageItemInfo.forceSafeLabels();
        updateSpecialAppAccessListActivityEnabledState();
    }

    private void updateSpecialAppAccessListActivityEnabledState() {
        ArrayMap<String, Role> roles = Roles.get(this);
        boolean hasVisibleSpecialAppAccess = false;
        int rolesSize = roles.size();
        for (int i = 0; i < rolesSize; i++) {
            Role role = roles.valueAt(i);

            if (!role.isAvailable(this) || !role.isVisible(this)) {
                continue;
            }
            if (!role.isExclusive()) {
                hasVisibleSpecialAppAccess = true;
                break;
            }
        }

        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, SpecialAppAccessListActivity.class);
        int enabledState = hasVisibleSpecialAppAccess
                ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(componentName, enabledState,
                PackageManager.DONT_KILL_APP);
    }
}
