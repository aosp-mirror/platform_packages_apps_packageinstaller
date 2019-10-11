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
import android.content.pm.ApplicationInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.role.utils.PackageUtils;
import com.android.permissioncontroller.R;

/**
 * Mixin for {@link RoleBehavior#getConfirmationMessage(Role, String, Context)}
 * that returns a confirmation message when the application is not direct boot aware.
 */
public class EncryptionUnawareConfirmationMixin {

    private static final String LOG_TAG = EncryptionUnawareConfirmationMixin.class.getSimpleName();

    /**
     * @see RoleBehavior#getConfirmationMessage(Role, String, Context)
     */
    @Nullable
    public static CharSequence getConfirmationMessage(@NonNull Role role,
            @NonNull String packageName, @NonNull Context context) {
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Cannot get ApplicationInfo for application, package name: "
                    + packageName);
            return null;
        }
        if (applicationInfo.isEncryptionAware()) {
            return null;
        }
        return context.getString(R.string.encryption_unaware_confirmation_message);
    }
}
