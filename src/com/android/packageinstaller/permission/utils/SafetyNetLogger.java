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

import android.content.pm.PackageInfo;
import android.util.EventLog;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.Permission;

import java.util.List;

public final class SafetyNetLogger {

    // The log tag used by SafetyNet to pick entries from the event log.
    private static final int SNET_NET_EVENT_LOG_TAG = 0x534e4554;

    // Log tag for the result of permissions request.
    private static final String PERMISSIONS_REQUESTED = "individual_permissions_requested";

    // Log tag for the result of permissions toggling.
    private static final String PERMISSIONS_TOGGLED = "individual_permissions_toggled";

    private SafetyNetLogger() {
        /* do nothing */
    }

    public static void logPermissionsRequested(PackageInfo packageInfo,
            List<AppPermissionGroup> groups) {
        EventLog.writeEvent(SNET_NET_EVENT_LOG_TAG, PERMISSIONS_REQUESTED,
                packageInfo.applicationInfo.uid, buildChangedPermissionForPackageMessage(
                        packageInfo.packageName, groups));
    }

    public static void logPermissionsToggled(String packageName, List<AppPermissionGroup> groups) {
        EventLog.writeEvent(SNET_NET_EVENT_LOG_TAG, PERMISSIONS_TOGGLED,
                android.os.Process.myUid(), buildChangedPermissionForPackageMessage(
                        packageName, groups));
    }

    private static String buildChangedPermissionForPackageMessage(String packageName,
            List<AppPermissionGroup> groups) {
        StringBuilder builder = new StringBuilder();

        builder.append(packageName).append(':');

        int groupCount = groups.size();
        for (int groupNum = 0; groupNum < groupCount; groupNum++) {
            AppPermissionGroup group = groups.get(groupNum);

            int permissionCount = group.getPermissions().size();
            for (int permissionNum = 0; permissionNum < permissionCount; permissionNum++) {
                Permission permission = group.getPermissions().get(permissionNum);

                if (groupNum > 0 || permissionNum > 0) {
                    builder.append(';');
                }

                builder.append(permission.getName()).append('|');

                if (group.doesSupportRuntimePermissions()) {
                    builder.append(permission.isGranted()).append('|');
                } else {
                    builder.append(permission.isGranted() && (permission.getAppOp() == null
                            || permission.isAppOpAllowed())).append('|');
                }

                builder.append(permission.getFlags());
            }
        }

        return builder.toString();
    }
}
