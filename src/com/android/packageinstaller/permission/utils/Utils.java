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

import static android.Manifest.permission_group.ACTIVITY_RECOGNITION;
import static android.Manifest.permission_group.CALENDAR;
import static android.Manifest.permission_group.CALL_LOG;
import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.CONTACTS;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MEDIA_AURAL;
import static android.Manifest.permission_group.MEDIA_VISUAL;
import static android.Manifest.permission_group.MICROPHONE;
import static android.Manifest.permission_group.PHONE;
import static android.Manifest.permission_group.SENSORS;
import static android.Manifest.permission_group.SMS;
import static android.Manifest.permission_group.STORAGE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.BidiFormatter;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Utils {

    private static final String LOG_TAG = "Utils";

    public static final String OS_PKG = "android";

    public static final float DEFAULT_MAX_LABEL_SIZE_PX = 500f;

    /** Mapping permission -> group for all dangerous platform permissions */
    private static final ArrayMap<String, String> PLATFORM_PERMISSIONS;

    /** Mapping group -> permissions for all dangerous platform permissions */
    private static final ArrayMap<String, ArrayList<String>> PLATFORM_PERMISSION_GROUPS;

    private static final Intent LAUNCHER_INTENT = new Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER);

    static {
        PLATFORM_PERMISSIONS = new ArrayMap<>();

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_CONTACTS, CONTACTS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_CONTACTS, CONTACTS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.GET_ACCOUNTS, CONTACTS);

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_CALENDAR, CALENDAR);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_CALENDAR, CALENDAR);

        PLATFORM_PERMISSIONS.put(Manifest.permission.SEND_SMS, SMS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.RECEIVE_SMS, SMS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_SMS, SMS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.RECEIVE_MMS, SMS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.RECEIVE_WAP_PUSH, SMS);
        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_CELL_BROADCASTS, SMS);

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_EXTERNAL_STORAGE, STORAGE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE);

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_MEDIA_AUDIO, MEDIA_AURAL);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_MEDIA_AUDIO, MEDIA_AURAL);

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_MEDIA_IMAGES, MEDIA_VISUAL);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_MEDIA_IMAGES, MEDIA_VISUAL);
        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_MEDIA_VIDEO, MEDIA_VISUAL);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_MEDIA_VIDEO, MEDIA_VISUAL);
        PLATFORM_PERMISSIONS.put(Manifest.permission.ACCESS_MEDIA_LOCATION, MEDIA_VISUAL);

        PLATFORM_PERMISSIONS.put(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION);
        PLATFORM_PERMISSIONS.put(Manifest.permission.ACCESS_COARSE_LOCATION, LOCATION);
        PLATFORM_PERMISSIONS.put(Manifest.permission.ACCESS_BACKGROUND_LOCATION, LOCATION);

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_CALL_LOG, CALL_LOG);
        PLATFORM_PERMISSIONS.put(Manifest.permission.WRITE_CALL_LOG, CALL_LOG);
        PLATFORM_PERMISSIONS.put(Manifest.permission.PROCESS_OUTGOING_CALLS, CALL_LOG);

        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_PHONE_STATE, PHONE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.READ_PHONE_NUMBERS, PHONE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.CALL_PHONE, PHONE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.ADD_VOICEMAIL, PHONE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.USE_SIP, PHONE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.ANSWER_PHONE_CALLS, PHONE);
        PLATFORM_PERMISSIONS.put(Manifest.permission.ACCEPT_HANDOVER, PHONE);

        PLATFORM_PERMISSIONS.put(Manifest.permission.RECORD_AUDIO, MICROPHONE);

        PLATFORM_PERMISSIONS.put(Manifest.permission.ACTIVITY_RECOGNITION, ACTIVITY_RECOGNITION);

        PLATFORM_PERMISSIONS.put(Manifest.permission.CAMERA, CAMERA);

        PLATFORM_PERMISSIONS.put(Manifest.permission.BODY_SENSORS, SENSORS);

        PLATFORM_PERMISSION_GROUPS = new ArrayMap<>();
        int numPlatformPermissions = PLATFORM_PERMISSIONS.size();
        for (int i = 0; i < numPlatformPermissions; i++) {
            String permission = PLATFORM_PERMISSIONS.keyAt(i);
            String permissionGroup = PLATFORM_PERMISSIONS.valueAt(i);

            ArrayList<String> permissionsOfThisGroup = PLATFORM_PERMISSION_GROUPS.get(
                    permissionGroup);
            if (permissionsOfThisGroup == null) {
                permissionsOfThisGroup = new ArrayList<>();
                PLATFORM_PERMISSION_GROUPS.put(permissionGroup, permissionsOfThisGroup);
            }

            permissionsOfThisGroup.add(permission);
        }
    }

    private Utils() {
        /* do nothing - hide constructor */
    }

    /**
     * Get permission group a platform permission belongs to.
     *
     * @param permission the permission to resolve
     *
     * @return The group the permission belongs to
     */
    private static @Nullable String getGroupOfPlatformPermission(@NonNull String permission) {
        return PLATFORM_PERMISSIONS.get(permission);
    }

    /**
     * Get name of the permission group a permission belongs to.
     *
     * @param permission the {@link PermissionInfo info} of the permission to resolve
     *
     * @return The group the permission belongs to
     */
    public static @Nullable String getGroupOfPermission(@NonNull PermissionInfo permission) {
        String groupName = permission.group;
        if (groupName == null) {
            groupName = Utils.getGroupOfPlatformPermission(permission.name);
        }

        return groupName;
    }

    /**
     * Get the {@link PermissionInfo infos} for all platform permissions belonging to a group.
     *
     * @param pm    Package manager to use to resolve permission infos
     * @param group the group
     *
     * @return The infos for platform permissions belonging to the group or an empty list if the
     *         group is not does not have platform runtime permissions
     */
    private static @NonNull List<PermissionInfo> getPlatformPermissionsOfGroup(
            @NonNull PackageManager pm, @NonNull String group) {
        ArrayList<PermissionInfo> permInfos = new ArrayList<>();

        ArrayList<String> permissions = PLATFORM_PERMISSION_GROUPS.get(group);
        if (permissions == null) {
            return Collections.emptyList();
        }

        int numPermissions = permissions.size();
        for (int i = 0; i < numPermissions; i++) {
            String permName = permissions.get(i);
            PermissionInfo permInfo;
            try {
                permInfo = pm.getPermissionInfo(permName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                throw new IllegalStateException(permName + " not defined by platform", e);
            }

            permInfos.add(permInfo);
        }

        return permInfos;
    }

    /**
     * Get the {@link PermissionInfo infos} for all permission infos belonging to a group.
     *
     * @param pm    Package manager to use to resolve permission infos
     * @param group the group
     *
     * @return The infos of permissions belonging to the group or an empty list if the group is not
     *         does not have runtime permissions
     */
    public static @NonNull List<PermissionInfo> getPermissionInfosForGroup(
            @NonNull PackageManager pm, @NonNull String group)
            throws PackageManager.NameNotFoundException {
        List<PermissionInfo> permissions = pm.queryPermissionsByGroup(group, 0);
        permissions.addAll(Utils.getPlatformPermissionsOfGroup(pm, group));

        return permissions;
    }

    /**
     * Get the label for an application.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    public static String getAppLabel(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        return BidiFormatter.getInstance().unicodeWrap(applicationInfo.loadSafeLabel(
                context.getPackageManager(), DEFAULT_MAX_LABEL_SIZE_PX,
                TextUtils.SAFE_STRING_FLAG_TRIM | TextUtils.SAFE_STRING_FLAG_FIRST_LINE)
                .toString());
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
        return PLATFORM_PERMISSION_GROUPS.containsKey(name);
    }

    /**
     * Should UI show this permission.
     *
     * <p>If the user cannot change the group, it should not be shown.
     *
     * @param group The group that might need to be shown to the user
     *
     * @return
     */
    public static boolean shouldShowPermission(Context context, AppPermissionGroup group) {
        boolean isSystemFixed = group.isSystemFixed();
        if (group.getBackgroundPermissions() != null) {
            // If the foreground mode is fixed to "enabled", the background mode might still be
            // switchable. We only want to suppress the group if nothing can be switched
            if (group.areRuntimePermissionsGranted()) {
                isSystemFixed &= group.getBackgroundPermissions().isSystemFixed();
            }
        }

        // We currently will not show permissions fixed by the system.
        // which is what the system does for system components.
        if (isSystemFixed && !LocationUtils.isLocationGroupAndProvider(context,
                group.getName(), group.getApp().packageName)) {
            return false;
        }

        if (!group.isGrantingAllowed()) {
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
        return ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                && !launcherPkgs.contains(info.packageName);
    }

    public static boolean areGroupPermissionsIndividuallyControlled(Context context, String group) {
        if (!context.getPackageManager().arePermissionsIndividuallyControlled()) {
            return false;
        }
        return Manifest.permission_group.SMS.equals(group)
                || Manifest.permission_group.PHONE.equals(group)
                || Manifest.permission_group.CONTACTS.equals(group);
    }

    public static boolean isPermissionIndividuallyControlled(Context context, String permission) {
        if (!context.getPackageManager().arePermissionsIndividuallyControlled()) {
            return false;
        }
        return Manifest.permission.READ_CONTACTS.equals(permission)
                || Manifest.permission.WRITE_CONTACTS.equals(permission)
                || Manifest.permission.SEND_SMS.equals(permission)
                || Manifest.permission.RECEIVE_SMS.equals(permission)
                || Manifest.permission.READ_SMS.equals(permission)
                || Manifest.permission.RECEIVE_MMS.equals(permission)
                || Manifest.permission.CALL_PHONE.equals(permission)
                || Manifest.permission.READ_CALL_LOG.equals(permission)
                || Manifest.permission.WRITE_CALL_LOG.equals(permission);
    }

    /**
     * Get the message shown to grant a permission group to an app.
     *
     * @param appLabel The label of the app
     * @param group the group to be granted
     * @param context A context to resolve resources
     * @param requestRes The resource id of the grant request message
     *
     * @return The formatted message to be used as title when granting permissions
     */
    public static CharSequence getRequestMessage(CharSequence appLabel, AppPermissionGroup group,
            Context context, @StringRes int requestRes) {
        if (requestRes != 0) {
            try {
                return Html.fromHtml(context.getPackageManager().getResourcesForApplication(
                        group.getDeclaringPackage()).getString(requestRes, appLabel), 0);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        return Html.fromHtml(context.getString(R.string.permission_warning_template, appLabel,
                group.getDescription()), 0);
    }

    /**
     * Build a string representing the number of milliseconds passed in.  It rounds to the nearest
     * unit.  For example, given a duration of 3500 and an English locale, this can return
     * "3 seconds".
     * @param context The context.
     * @param duration The number of milliseconds.
     * @return a string representing the given number of milliseconds.
     */
    public static @NonNull String getTimeDiffStr(@NonNull Context context, long duration) {
        long seconds = Math.max(1, duration / 1000);
        if (seconds < 60) {
            return context.getResources().getQuantityString(R.plurals.seconds, (int) seconds,
                    seconds);
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return context.getResources().getQuantityString(R.plurals.minutes, (int) minutes,
                    minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return context.getResources().getQuantityString(R.plurals.hours, (int) hours, hours);
        }
        long days = hours / 24;
        return context.getResources().getQuantityString(R.plurals.days, (int) days, days);
    }
}
