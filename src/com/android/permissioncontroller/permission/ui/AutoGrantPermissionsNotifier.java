/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.app.PendingIntent.getActivity;
import static android.content.Intent.EXTRA_PACKAGE_NAME;
import static android.content.Intent.EXTRA_PERMISSION_NAME;
import static android.content.Intent.EXTRA_USER;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Bitmap.createBitmap;
import static android.os.UserHandle.getUserHandleForUid;

import static com.android.permissioncontroller.Constants.ADMIN_AUTO_GRANTED_PERMISSIONS_NOTIFICATION_CHANNEL_ID;
import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.PERMISSION_GRANTED_BY_ADMIN_NOTIFICATION_ID;
import static com.android.permissioncontroller.permission.utils.Utils.getSystemServiceSafe;
import static com.android.permissioncontroller.permission.utils.Utils.getValidSessionId;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.permissioncontroller.R;

import java.util.ArrayList;

/**
 * Notifies the user when the admin has granted sensitive permissions, such as location-related
 * permissions, to apps.
 *
 * <p>NOTE: This class currently only handles location permissions.
 *
 * <p>To handle other sensitive permissions, it would have to be expanded to notify the user
 * not only of location issues and use icons of the different groups associated with different
 * permissions.
 */
class AutoGrantPermissionsNotifier {
    /**
     * Set of permissions for which the user should be notified when the admin auto-grants one of
     * them.
     */
    private static final ArraySet<String> PERMISSIONS_TO_NOTIFY_FOR = new ArraySet<>();

    static {
        PERMISSIONS_TO_NOTIFY_FOR.add(Manifest.permission.ACCESS_FINE_LOCATION);
        PERMISSIONS_TO_NOTIFY_FOR.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        PERMISSIONS_TO_NOTIFY_FOR.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private final @NonNull Context mContext;
    /**
     * The package to which permissions were auto-granted.
     */
    private final @NonNull PackageInfo mPackageInfo;
    /**
     * The permissions that were auto-granted.
     */
    private final ArrayList<String> mGrantedPermissions = new ArrayList<>();

    AutoGrantPermissionsNotifier(@NonNull Context context,
            @NonNull PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
        UserHandle callingUser = getUserHandleForUid(mPackageInfo.applicationInfo.uid);
        mContext = context.createContextAsUser(callingUser, 0);
    }

    /**
     * Create the channel to which the notification about auto-granted permission should be posted
     * to.
     *
     * @param user The user for which the permission was auto-granted.
     */
    private void createAutoGrantNotifierChannel() {
        NotificationManager notificationManager = getSystemServiceSafe(mContext,
                NotificationManager.class);

        NotificationChannel autoGrantedPermissionsChannel = new NotificationChannel(
                ADMIN_AUTO_GRANTED_PERMISSIONS_NOTIFICATION_CHANNEL_ID,
                mContext.getString(R.string.auto_granted_permissions),
                NotificationManager.IMPORTANCE_HIGH);
        autoGrantedPermissionsChannel.enableVibration(false);
        autoGrantedPermissionsChannel.setSound(Uri.EMPTY, null);
        notificationManager.createNotificationChannel(autoGrantedPermissionsChannel);
    }

    /**
     * Notifies the user if any permissions were auto-granted.
     *
     * <p>NOTE: Right now this method only deals with location permissions.
     */
    public void notifyOfAutoGrantPermissions() {
        if (mGrantedPermissions.isEmpty()) {
            return;
        }

        createAutoGrantNotifierChannel();

        PackageManager pm = mContext.getPackageManager();
        CharSequence pkgLabel = pm.getApplicationLabel(mPackageInfo.applicationInfo);

        long sessionId = getValidSessionId();

        Intent manageAppPermission = getSettingsPermissionIntent(sessionId);
        Bitmap pkgIconBmp = getPackageIcon(pm.getApplicationIcon(mPackageInfo.applicationInfo));

        String title = mContext.getString(
                R.string.auto_granted_location_permission_notification_title);
        String messageText = mContext.getString(R.string.auto_granted_permission_notification_body,
                pkgLabel);
        Notification.Builder b = (new Notification.Builder(mContext,
                ADMIN_AUTO_GRANTED_PERMISSIONS_NOTIFICATION_CHANNEL_ID)).setContentTitle(title)
                .setContentText(messageText)
                .setStyle(new Notification.BigTextStyle().bigText(messageText).setBigContentTitle(
                        title))
                // NOTE: Different icons would be needed for different permissions.
                .setSmallIcon(R.drawable.ic_pin_drop)
                .setLargeIcon(pkgIconBmp)
                .setColor(mContext.getColor(android.R.color.system_notification_accent_color))
                .setContentIntent(getActivity(mContext, 0, manageAppPermission,
                        FLAG_UPDATE_CURRENT));

        // Add the Settings app name since we masquerade it.
        CharSequence appName = getSettingsAppName();
        if (appName != null) {
            Bundle extras = new Bundle();
            extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, appName.toString());
            b.addExtras(extras);
        }

        NotificationManager notificationManager = getSystemServiceSafe(mContext,
                NotificationManager.class);
        notificationManager.notify(mPackageInfo.packageName,
                PERMISSION_GRANTED_BY_ADMIN_NOTIFICATION_ID,
                b.build());
    }

    /**
     * Checks if the auto-granted permission is one of those for which the user has to be notified
     * and if so, stores it for when the user actually is notified.
     *
     * @param permissionName the permission that was auto-granted.
     */
    public void onPermissionAutoGranted(@NonNull String permissionName) {
        if (PERMISSIONS_TO_NOTIFY_FOR.contains(permissionName)) {
            mGrantedPermissions.add(permissionName);
        }
    }

    private @Nullable CharSequence getSettingsAppName() {
        PackageManager pm = mContext.getPackageManager();
        // We pretend we're the Settings app sending the notification, so figure out its name.
        Intent openSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo resolveInfo = pm.resolveActivity(openSettingsIntent, 0);
        if (resolveInfo == null) {
            return null;
        }
        return pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo);
    }

    private @NonNull Intent getSettingsPermissionIntent(long sessionId) {
        UserHandle callingUser = getUserHandleForUid(mPackageInfo.applicationInfo.uid);

        return new Intent(mContext, AppPermissionActivity.class)
        .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
        .putExtra(EXTRA_PERMISSION_NAME, ACCESS_FINE_LOCATION)
        .putExtra(EXTRA_PACKAGE_NAME, mPackageInfo.packageName)
        .putExtra(EXTRA_USER, callingUser)
        .putExtra(EXTRA_SESSION_ID, sessionId)
        .putExtra(AppPermissionActivity.EXTRA_CALLER_NAME,
                AutoGrantPermissionsNotifier.class.getName());
    }

    private @NonNull Bitmap getPackageIcon(@NonNull Drawable pkgIcon) {
        Bitmap pkgIconBmp = createBitmap(pkgIcon.getIntrinsicWidth(), pkgIcon.getIntrinsicHeight(),
                ARGB_8888);
        // Draw the icon so it can be displayed.
        Canvas canvas = new Canvas(pkgIconBmp);
        pkgIcon.setBounds(0, 0, pkgIcon.getIntrinsicWidth(), pkgIcon.getIntrinsicHeight());
        pkgIcon.draw(canvas);
        return pkgIconBmp;
    }
}

