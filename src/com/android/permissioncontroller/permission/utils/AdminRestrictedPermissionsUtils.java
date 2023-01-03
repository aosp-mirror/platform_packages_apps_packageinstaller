/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils;

import android.Manifest;
import android.util.ArraySet;

/**
 * A class for dealing with permissions that the admin may not grant in certain configurations.
 */
public final class AdminRestrictedPermissionsUtils {

    /**
     * A set of permissions that the managed Profile Owner cannot grant.
     */
    private static final ArraySet<String> MANAGED_PROFILE_OWNER_RESTRICTED_PERMISSIONS =
            new ArraySet<>();

    static {
        MANAGED_PROFILE_OWNER_RESTRICTED_PERMISSIONS.add(Manifest.permission.READ_SMS);
    }

    /**
     * Returns true if the admin may grant this permission, false otherwise.
     */
    public static boolean mayAdminGrantPermission(String permission, boolean isManagedProfile) {
        return !isManagedProfile
                || !MANAGED_PROFILE_OWNER_RESTRICTED_PERMISSIONS.contains(permission);
    }
}
