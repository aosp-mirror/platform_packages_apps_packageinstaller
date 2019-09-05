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

package com.android.packageinstaller.permission.ui.auto;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.packageinstaller.permission.utils.Utils;

/** Common utilities shared between permissions settings. */
public final class AutoPermissionsUtils {
    private static final String LOG_TAG = "AutoPermissionsUtils";

    private AutoPermissionsUtils() {
    }

    /** Gets the {@link PackageInfo} for the given package name and user. */
    public static PackageInfo getPackageInfo(Activity activity, @NonNull String packageName,
            @NonNull UserHandle userHandle) {
        try {
            return activity.createPackageContextAsUser(packageName, 0, userHandle)
                    .getPackageManager()
                    .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + activity.getCallingPackage(), e);
            return null;
        }
    }

    /** Creates a {@link Preference} which shows the app icon and app name. */
    public static Preference createHeaderPreference(Context context, ApplicationInfo appInfo) {
        Drawable icon = Utils.getBadgedIcon(context, appInfo);
        Preference preference = new Preference(context);
        preference.setIcon(icon);
        preference.setKey(appInfo.packageName);
        preference.setTitle(Utils.getFullAppLabel(appInfo, context));
        return preference;
    }
}
