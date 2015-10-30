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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.util.ArraySet;
import android.util.Log;
import android.util.TypedValue;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;

import java.util.List;

public class Utils {

    private static final String LOG_TAG = "Utils";

    public static final String OS_PKG = "android";

    public static final String[] MODERN_PERMISSION_GROUPS = {
            Manifest.permission_group.CALENDAR,
            Manifest.permission_group.CAMERA,
            Manifest.permission_group.CONTACTS,
            Manifest.permission_group.LOCATION,
            Manifest.permission_group.SENSORS,
            Manifest.permission_group.SMS,
            Manifest.permission_group.PHONE,
            Manifest.permission_group.MICROPHONE,
            Manifest.permission_group.STORAGE
    };

    private static final Intent LAUNCHER_INTENT = new Intent(Intent.ACTION_MAIN, null)
                            .addCategory(Intent.CATEGORY_LAUNCHER);

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
        for (String modernGroup : MODERN_PERMISSION_GROUPS) {
            if (modernGroup.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldShowPermission(AppPermissionGroup group, String packageName) {
        // We currently will not show permissions fixed by the system.
        // which is what the system does for system components.
        if (group.isSystemFixed() && !LocationUtils.isLocationGroupAndProvider(
                group.getName(), packageName)) {
            return false;
        }

        final boolean isPlatformPermission = group.getDeclaringPackage().equals(OS_PKG);
        // Show legacy permissions only if the user chose that.
        if (isPlatformPermission
                && !Utils.isModernPermissionGroup(group.getName())) {
            return false;
        }
        return true;
    }

    public static boolean shouldShowPermission(PermissionApp app) {
        // We currently will not show permissions fixed by the system
        // which is what the system does for system components.
        if (app.isSystemFixed() && !LocationUtils.isLocationGroupAndProvider(
                app.getPermissionGroup().getName(), app.getPackageName())) {
            return false;
        }

        return true;
    }

    public static Drawable applyTint(Context context, Drawable icon, int attr) {
        Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attr, typedValue, true);
        icon = icon.mutate();
        icon.setTint(context.getColor(typedValue.resourceId));
        return icon;
    }

    public static Drawable applyTint(Context context, int iconResId, int attr) {
        return applyTint(context, context.getDrawable(iconResId), attr);
    }

    public static ArraySet<String> getLauncherPackages(Context context) {
        ArraySet<String> launcherPkgs = new ArraySet<>();
        for (ResolveInfo info :
            context.getPackageManager().queryIntentActivities(LAUNCHER_INTENT, 0)) {
            launcherPkgs.add(info.activityInfo.packageName);
        }

        return launcherPkgs;
    }

    public static List<ApplicationInfo> getAllInstalledApplications(Context context) {
        return context.getPackageManager().getInstalledApplications(0);
    }

    public static boolean isSystem(PermissionApp app, ArraySet<String> launcherPkgs) {
        return isSystem(app.getAppInfo(), launcherPkgs);
    }

    public static boolean isSystem(AppPermissions app, ArraySet<String> launcherPkgs) {
        return isSystem(app.getPackageInfo().applicationInfo, launcherPkgs);
    }

    public static boolean isSystem(ApplicationInfo info, ArraySet<String> launcherPkgs) {
        return info.isSystemApp() && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                && !launcherPkgs.contains(info.packageName);
    }
}
