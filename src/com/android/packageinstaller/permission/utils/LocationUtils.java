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
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.permissioncontroller.R;

public class LocationUtils {

    public static final String LOCATION_PERMISSION = Manifest.permission_group.LOCATION;

    private static final String TAG = LocationUtils.class.getSimpleName();

    public static void showLocationDialog(final Context context, CharSequence label) {
        new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_dialog_alert_material)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(context.getString(R.string.location_warning, label))
                .setNegativeButton(R.string.ok, null)
                .setPositiveButton(R.string.location_settings, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        context.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .show();
    }

    /** Start the settings page for the location controller extra package. */
    public static void startLocationControllerExtraPackageSettings(@NonNull Context context) {
        try {
            context.startActivity(new Intent(
                        Settings.ACTION_LOCATION_CONTROLLER_EXTRA_PACKAGE_SETTINGS));
            return;
        } catch (ActivityNotFoundException e) {
            // In rare cases where location controller extra package is set, but
            // no activity exists to handle the location controller extra package settings
            // intent, log an error instead of crashing permission controller.
            Log.e(TAG, "No activity to handle "
                        + "android.settings.LOCATION_CONTROLLER_EXTRA_PACKAGE_SETTINGS");
        }
    }

    public static boolean isLocationEnabled(Context context) {
        return context.getSystemService(LocationManager.class).isLocationEnabled();
    }

    public static boolean isLocationGroupAndProvider(Context context, String groupName,
            String packageName) {
        return LOCATION_PERMISSION.equals(groupName)
                && context.getSystemService(LocationManager.class).isProviderPackage(packageName);
    }

    public static boolean isLocationGroupAndControllerExtraPackage(@NonNull Context context,
            @NonNull String groupName, @NonNull String packageName) {
        return LOCATION_PERMISSION.equals(groupName)
                && packageName.equals(context.getSystemService(LocationManager.class)
                        .getExtraLocationControllerPackage());
    }

    /** Returns whether the location controller extra package is enabled. */
    public static boolean isExtraLocationControllerPackageEnabled(Context context) {
        try {
            return context.getSystemService(LocationManager.class)
                    .isExtraLocationControllerPackageEnabled();
        } catch (Exception e) {
            return false;
        }

    }
}
