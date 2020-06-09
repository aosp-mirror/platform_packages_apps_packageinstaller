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
package com.android.permissioncontroller.permission.utils;

import static android.location.LocationManager.EXTRA_LOCATION_ENABLED;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.R;

import java.util.ArrayList;

public class LocationUtils {

    public static final String LOCATION_PERMISSION = Manifest.permission_group.LOCATION;

    private static final String TAG = LocationUtils.class.getSimpleName();
    private static final long LOCATION_UPDATE_DELAY_MS = 1000;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

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
    public static void startLocationControllerExtraPackageSettings(@NonNull Context context,
            @NonNull UserHandle user) {
        try {
            context.startActivityAsUser(new Intent(
                        Settings.ACTION_LOCATION_CONTROLLER_EXTRA_PACKAGE_SETTINGS), user);
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

    /**
     * A Listener which responds to enabling or disabling of location on the device
     */
    public interface LocationListener {

        /**
         * A callback run any time we receive a broadcast stating the location enable state has
         * changed.
         * @param enabled Whether or not location is enabled
         */
        void onLocationStateChange(boolean enabled);
    }

    private static final ArrayList<LocationListener> sLocationListeners = new ArrayList<>();

    private static BroadcastReceiver sLocationBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isEnabled = intent.getBooleanExtra(EXTRA_LOCATION_ENABLED, true);
            sMainHandler.postDelayed(() -> {
                synchronized (sLocationListeners) {
                    for (LocationListener l : sLocationListeners) {
                        l.onLocationStateChange(isEnabled);
                    }
                }
            }, LOCATION_UPDATE_DELAY_MS);
        }
    };

    /**
     * Add a LocationListener, which will be notified if the location provider is enabled or
     * disabled
     * @param listener the listener to add
     */
    public static void addLocationListener(LocationListener listener) {
        synchronized (sLocationListeners) {
            boolean wasEmpty = sLocationListeners.isEmpty();
            sLocationListeners.add(listener);
            if (wasEmpty) {
                IntentFilter intentFilter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
                PermissionControllerApplication.get().getApplicationContext()
                        .registerReceiverForAllUsers(sLocationBroadcastReceiver, intentFilter,
                                null, null);
            }
        }
    }

    /**
     * Remove a LocationListener
     * @param listener The listener to remove
     *
     * @return True if it was successfully removed, false otherwise
     */
    public static boolean removeLocationListener(LocationListener listener) {
        synchronized (sLocationListeners) {
            boolean success = sLocationListeners.remove(listener);
            if (success && sLocationListeners.isEmpty()) {
                PermissionControllerApplication.get().getApplicationContext()
                        .unregisterReceiver(sLocationBroadcastReceiver);
            }
            return success;
        }
    }
}
