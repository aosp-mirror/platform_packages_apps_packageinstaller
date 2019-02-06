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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.utils.CollectionUtils;

import java.util.List;

/**
 * Mixin for {@link RoleBehavior#getDefaultHolders(Role, Context)} that returns a single default
 * role holder from {@link DefaultRoleHolders}.
 */
public class ExclusiveDefaultHolderMixin {

    private ExclusiveDefaultHolderMixin() {}

    /**
     * @see Role#getDefaultHolders(Context)
     */
    @NonNull
    public static List<String> getDefaultHolders(@NonNull Role role, @NonNull Context context) {
        return CollectionUtils.singletonOrEmpty(getDefaultHolder(role, context));
    }

    /**
     * @see Role#getDefaultHolders(Context)
     */
    @Nullable
    public static String getDefaultHolder(@NonNull Role role, @NonNull Context context) {
        String defaultPackageName = CollectionUtils.firstOrNull(DefaultRoleHolders.get(context).get(
                role.getName()));
        if (defaultPackageName == null) {
            return null;
        }
        if (!role.isPackageQualified(defaultPackageName, context)) {
            return null;
        }
        return defaultPackageName;
    }
}
