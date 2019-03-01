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
import android.content.res.Resources;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Mixin for {@link RoleBehavior#getDefaultHolders(Role, Context)} that returns a single default
 * role holder from the corresponding string resource.
 */
public class VisibilityMixin {

    private static final String LOG_TAG = VisibilityMixin.class.getSimpleName();

    private VisibilityMixin() {}

    /**
     * @see Role#isVisibleAsUser(UserHandle, Context)
     */
    public static boolean isVisible(@NonNull String resourceName, @NonNull Context context) {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(resourceName, "bool", "android");
        if (resourceId == 0) {
            Log.w(LOG_TAG, "Cannot find resource for visibility: " + resourceName);
            return true;
        }
        try {
            return resources.getBoolean(resourceId);
        } catch (Resources.NotFoundException e) {
            Log.w(LOG_TAG, "Cannot get resource for visibility: " + resourceName, e);
            return true;
        }
    }
}
