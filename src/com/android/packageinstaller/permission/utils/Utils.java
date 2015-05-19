/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.packageinstaller.permission.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;

public class Utils {
    private static final String LOG_TAG = "Utils";

    private Utils() {
        /* do nothing - hide constructor */
    }

    public static Drawable loadDrawable(PackageManager pm, String pkg, int resId) {
        try {
            return pm.getResourcesForApplication(pkg).getDrawable(resId, null);
        } catch (Resources.NotFoundException | PackageManager.NameNotFoundException e) {
            Log.d(LOG_TAG, "Couldn't get resource", e);
            return null;
        }
    }

    public static boolean isModernPermissionGroup(String name) {
        switch (name) {
            case Manifest.permission_group.CALENDAR:
            case Manifest.permission_group.CAMERA:
            case Manifest.permission_group.CONTACTS:
            case Manifest.permission_group.LOCATION:
            case Manifest.permission_group.SENSORS:
            case Manifest.permission_group.SMS:
            case Manifest.permission_group.PHONE:
            case Manifest.permission_group.MICROPHONE: {
                return true;
            }

            default: {
                return false;
            }
        }
    }

    public static Drawable applyTint(Context context, Drawable icon, int attr) {
        Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attr, typedValue, true);
        icon.setTint(context.getColor(typedValue.resourceId));
        return icon;
    }
}
