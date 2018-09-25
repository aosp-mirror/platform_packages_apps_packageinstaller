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
package com.android.packageinstaller.permission.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Temporary wrapper for {@code android.util.IconDrawableFactory#getBadgedIcon} as the method is not
 * system API.
 *
 * STOPSHIP: Needs to be removed and replaced by proper system-api (b/115891474)
 */
public class IconDrawableFactory {
    private static final String LOG_TAG = IconDrawableFactory.class.getSimpleName();

    /**
     * Get badged app icon to be used in settings UI.
     *
     * @param context The context to use
     * @param appInfo The app the icon belong to
     * @param user The user the app belong to
     *
     * @return The icon to use in the UI
     */
    public static Drawable getBadgedIcon(@NonNull Context context, @NonNull ApplicationInfo appInfo,
            @NonNull UserHandle user) {
        Method newInstanceMethod;
        Method getBadgedIconMethod;
        try {
            Class<?> sysIconDrawableFactoryClass = Class.forName(
                    "android.util.IconDrawableFactory");

            newInstanceMethod = sysIconDrawableFactoryClass.getMethod("newInstance", Context.class);
            getBadgedIconMethod = sysIconDrawableFactoryClass.getMethod("getBadgedIcon",
                    ApplicationInfo.class, Integer.TYPE);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.e(LOG_TAG, "Cannot resolve icon", e);
            return null;
        }

        Object icon;
        try {
            Object sysIconDrawableFactory = newInstanceMethod.invoke(null, context);
            icon = getBadgedIconMethod.invoke(sysIconDrawableFactory, appInfo,
                    user.getIdentifier());
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.e(LOG_TAG, "Cannot resolve icon", e);
            icon = null;
        }

        if (icon == null) {
            return null;
        } else {
            return (Drawable) icon;
        }
    }
}
