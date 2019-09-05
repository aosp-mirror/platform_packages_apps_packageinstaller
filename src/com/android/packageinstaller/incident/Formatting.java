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

package com.android.packageinstaller.incident;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.packageinstaller.permission.utils.Utils;

import java.text.DateFormat;
import java.util.Date;

/**
 * Utility class for formatting the incident report confirmations.
 */
public class Formatting {
    private final Context mContext;
    private final PackageManager mPm;
    private final DateFormat mDateFormat;
    private final DateFormat mTimeFormat;

    /**
     * Constructor.  This object keeps the context.
     */
    Formatting(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
        mDateFormat = android.text.format.DateFormat.getDateFormat(context);
        mTimeFormat = android.text.format.DateFormat.getTimeFormat(context);
    }

    /**
     * Get the name to show the user for an application, given the package name.
     * If the application can't be found, returns null.
     */
    String getAppLabel(String pkg) {
        ApplicationInfo app;
        try {
            app = mPm.getApplicationInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException ex) {
            return null;
        }
        return Utils.getAppLabel(app, mContext);
    }

    /**
     * Format the date portion of a {@link System.currentTimeMillis} as a user-visible string.
     */
    String getDate(long wallTimeMs) {
        return mDateFormat.format(new Date(wallTimeMs));
    }

    /**
     * Format the time portion of a {@link System.currentTimeMillis} as a user-visible string.
     */
    String getTime(long wallTimeMs) {
        return mTimeFormat.format(new Date(wallTimeMs));
    }
}
