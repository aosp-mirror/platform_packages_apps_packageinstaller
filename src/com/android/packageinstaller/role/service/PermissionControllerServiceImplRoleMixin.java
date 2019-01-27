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

package com.android.packageinstaller.role.service;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;

/**
 * Mixin for {@link com.android.packageinstaller.permission.service.PermissionControllerServiceImpl}
 * methods that are related to roles.
 */
public class PermissionControllerServiceImplRoleMixin {

    private PermissionControllerServiceImplRoleMixin() {}

    /**
     * @see android.permission.PermissionControllerService#onIsApplicationQualifiedForRole(String,
     *      String)
     */
    public static boolean onIsApplicationQualifiedForRole(@NonNull String roleName,
            @NonNull String packageName, @NonNull Context context) {
        Role role = Roles.get(context).get(roleName);
        if (role == null) {
            return false;
        }
        if (!role.isAvailable(context)) {
            return false;
        }
        return role.isPackageQualified(packageName, context);
    }
}
