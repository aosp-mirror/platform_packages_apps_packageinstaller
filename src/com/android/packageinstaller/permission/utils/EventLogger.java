/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.app.AppOpsManager;
import android.content.Context;
import android.os.SystemProperties;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.Arrays;
import java.util.List;

/**
 * For each permission there are four events. The events are in the order of
 * #ALL_DANGEROUS_PERMISSIONS. The four events per permission are (in that order): "requested",
 * "granted", "denied", and "revoked".
 */
public class EventLogger {
    private static final String LOG_TAG = EventLogger.class.getSimpleName();

    /** All dangerous permission names in the same order as the events in MetricsEvent */
    private static final List<String> ALL_DANGEROUS_PERMISSIONS = Arrays.asList(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBER,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.ADD_VOICEMAIL,
            Manifest.permission.USE_SIP,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.READ_CELL_BROADCASTS,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);

    /**
     * Get the first event id for the permission.
     *
     * <p>There are four events for each permission: <ul>
     *     <li>Request permission: first id + 0</li>
     *     <li>Grant permission: first id + 1</li>
     *     <li>Request for permission denied: first id + 2</li>
     *     <li>Revoke permission: first id + 3</li>
     * </ul></p>
     *
     * @param name name of the permission
     *
     * @return The first event id for the permission
     */
    private static int getBaseEventId(@NonNull String name) {
        int eventIdIndex = ALL_DANGEROUS_PERMISSIONS.indexOf(name);

        if (eventIdIndex == -1) {
            if (AppOpsManager.permissionToOpCode(name) == AppOpsManager.OP_NONE
                    || "user".equals(SystemProperties.get("ro.build.type"))) {
                Log.i(LOG_TAG, "Unknown permission " + name);

                return MetricsEvent.ACTION_PERMISSION_REQUEST_UNKNOWN;
            } else {
                // Most likely #ALL_DANGEROUS_PERMISSIONS needs to be updated.
                //
                // Also update
                // - PackageManagerService#ALL_DANGEROUS_PERMISSIONS
                // - metrics_constants.proto
                throw new IllegalStateException("Unknown permission " + name);
            }
        }

        return MetricsEvent.ACTION_PERMISSION_REQUEST_READ_CALENDAR + eventIdIndex * 4;
    }

    /**
     * Log that a permission was requested.
     *
     * @param context Context of the caller
     * @param name name of the permission
     * @param packageName package permission if for
     */
    public static void logPermissionRequested(@NonNull Context context, @NonNull String name,
            @NonNull String packageName) {
        MetricsLogger.action(context, getBaseEventId(name), packageName);
    }

    /**
     * Log that a permission request was denied.
     *
     * @param context Context of the caller
     * @param name name of the permission
     * @param packageName package permission if for
     */
    public static void logPermissionDenied(@NonNull Context context, @NonNull String name,
            @NonNull String packageName) {
        MetricsLogger.action(context, getBaseEventId(name) + 2, packageName);
    }

}
