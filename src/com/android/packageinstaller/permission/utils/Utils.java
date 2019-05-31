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

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission_group.ACTIVITY_RECOGNITION;
import static android.Manifest.permission_group.CALENDAR;
import static android.Manifest.permission_group.CALL_LOG;
import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.CONTACTS;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;
import static android.Manifest.permission_group.PHONE;
import static android.Manifest.permission_group.SENSORS;
import static android.Manifest.permission_group.SMS;
import static android.Manifest.permission_group.STORAGE;
import static android.app.role.RoleManager.ROLE_ASSISTANT;
import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.os.UserHandle.myUserId;

import static com.android.packageinstaller.Constants.ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY;
import static com.android.packageinstaller.Constants.FORCED_USER_SENSITIVE_UIDS_KEY;
import static com.android.packageinstaller.Constants.PREFERENCES_FILE;

import android.Manifest;
import android.app.Application;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.BidiFormatter;
import androidx.core.util.Preconditions;

import com.android.launcher3.icons.IconFactory;
import com.android.packageinstaller.Constants;
import com.android.packageinstaller.permission.data.PerUserUidToSensitivityLiveData;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Utils {

    private static final String LOG_TAG = "Utils";

    public static final String OS_PKG = "android";

    public static final float DEFAULT_MAX_LABEL_SIZE_PX = 500f;

    /** Whether to show location access check notifications. */
    private static final String PROPERTY_LOCATION_ACCESS_CHECK_ENABLED =
            "location_access_check_enabled";

    /** All permission whitelists. */
    public static final int FLAGS_PERMISSION_WHITELIST_ALL =
            PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                    | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                    | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER;

    /** Mapping permission -> group for all dangerous platform permissions */
    private static final ArrayMap<String, String> PLATFORM_PERMISSIONS;

    /** Mapping group -> permissions for all dangerous platform permissions */
    private static final ArrayMap<String, ArrayList<String>> PLATFORM_PERMISSION_GROUPS;

    private static final Intent LAUNCHER_INTENT = new Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER);

    public static final int FLAGS_ALWAYS_USER_SENSITIVE =
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
                    | FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;

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
        PLATFORM_PERMISSIONS.put(Manifest.permission.ACCESS_MEDIA_LOCATION, STORAGE);

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
     * {@code @NonNull} version of {@link Context#getSystemService(Class)}
     */
    public static @NonNull <M> M getSystemServiceSafe(@NonNull Context context, Class<M> clazz) {
        return Preconditions.checkNotNull(context.getSystemService(clazz),
                "Could not resolve " + clazz.getSimpleName());
    }

    /**
     * {@code @NonNull} version of {@link Context#getSystemService(Class)}
     */
    public static @NonNull <M> M getSystemServiceSafe(@NonNull Context context, Class<M> clazz,
            @NonNull UserHandle user) {
        try {
            return Preconditions.checkNotNull(context.createPackageContextAsUser(
                    context.getPackageName(), 0, user).getSystemService(clazz),
                    "Could not resolve " + clazz.getSimpleName());
        } catch (PackageManager.NameNotFoundException neverHappens) {
            throw new IllegalStateException();
        }
    }

    /**
     * {@code @NonNull} version of {@link Intent#getParcelableExtra(String)}
     */
    public static @NonNull <T extends Parcelable> T getParcelableExtraSafe(@NonNull Intent intent,
            @NonNull String name) {
        return Preconditions.checkNotNull(intent.getParcelableExtra(name),
                "Could not get parcelable extra for " + name);
    }

    /**
     * {@code @NonNull} version of {@link Intent#getStringExtra(String)}
     */
    public static @NonNull String getStringExtraSafe(@NonNull Intent intent,
            @NonNull String name) {
        return Preconditions.checkNotNull(intent.getStringExtra(name),
                "Could not get string extra for " + name);
    }

    /**
     * Get permission group a platform permission belongs to.
     *
     * @param permission the permission to resolve
     *
     * @return The group the permission belongs to
     */
    public static @Nullable String getGroupOfPlatformPermission(@NonNull String permission) {
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
        String groupName = Utils.getGroupOfPlatformPermission(permission.name);
        if (groupName == null) {
            groupName = permission.group;
        }

        return groupName;
    }

    /**
     * Get the names for all platform permissions belonging to a group.
     *
     * @param group the group
     *
     * @return The permission names  or an empty list if the
     *         group is not does not have platform runtime permissions
     */
    public static @NonNull List<String> getPlatformPermissionNamesOfGroup(@NonNull String group) {
        final ArrayList<String> permissions = PLATFORM_PERMISSION_GROUPS.get(group);
        return (permissions != null) ? permissions : Collections.emptyList();
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
    public static @NonNull List<PermissionInfo> getPlatformPermissionsOfGroup(
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
     * @return The infos of permissions belonging to the group or an empty list if the group
     *         does not have runtime permissions
     */
    public static @NonNull List<PermissionInfo> getPermissionInfosForGroup(
            @NonNull PackageManager pm, @NonNull String group)
            throws PackageManager.NameNotFoundException {
        List<PermissionInfo> permissions = pm.queryPermissionsByGroup(group, 0);
        permissions.addAll(getPlatformPermissionsOfGroup(pm, group));

        return permissions;
    }

    /**
     * Get the {@link PackageItemInfo infos} for the given permission group.
     *
     * @param groupName the group
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return The info of permission group or null if the group does not have runtime permissions.
     */
    public static @Nullable PackageItemInfo getGroupInfo(@NonNull String groupName,
            @NonNull Context context) {
        try {
            return context.getPackageManager().getPermissionGroupInfo(groupName, 0);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        try {
            return context.getPackageManager().getPermissionInfo(groupName, 0);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        return null;
    }

    /**
     * Get the {@link PermissionInfo infos} for all permission infos belonging to a group.
     *
     * @param groupName the group
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return The infos of permissions belonging to the group or null if the group does not have
     *         runtime permissions.
     */
    public static @Nullable List<PermissionInfo> getGroupPermissionInfos(@NonNull String groupName,
            @NonNull Context context) {
        try {
            return Utils.getPermissionInfosForGroup(context.getPackageManager(), groupName);
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        try {
            PermissionInfo permissionInfo = context.getPackageManager()
                    .getPermissionInfo(groupName, 0);
            List<PermissionInfo> permissions = new ArrayList<>();
            permissions.add(permissionInfo);
            return permissions;
        } catch (NameNotFoundException e) {
            /* ignore */
        }
        return null;
    }

    /**
     * Get the label for an application, truncating if it is too long.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    public static String getAppLabel(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        return getAppLabel(applicationInfo, DEFAULT_MAX_LABEL_SIZE_PX, context);
    }

    /**
     * Get the full label for an application without truncation.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    public static String getFullAppLabel(@NonNull ApplicationInfo applicationInfo,
            @NonNull Context context) {
        return getAppLabel(applicationInfo, 0, context);
    }

    /**
     * Get the label for an application with the ability to control truncating.
     *
     * @param applicationInfo the {@link ApplicationInfo} of the application
     * @param ellipsizeDip see {@link TextUtils#makeSafeForPresentation}.
     * @param context the {@code Context} to retrieve {@code PackageManager}
     *
     * @return the label for the application
     */
    @NonNull
    private static String getAppLabel(@NonNull ApplicationInfo applicationInfo, float ellipsizeDip,
            @NonNull Context context) {
        return BidiFormatter.getInstance().unicodeWrap(applicationInfo.loadSafeLabel(
                context.getPackageManager(), ellipsizeDip,
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
     * Get the names of the platform permission groups.
     *
     * @return the names of the platform permission groups.
     */
    public static List<String> getPlatformPermissionGroups() {
        return new ArrayList<>(PLATFORM_PERMISSION_GROUPS.keySet());
    }

    /**
     * Get the names of the platform permissions.
     *
     * @return the names of the platform permissions.
     */
    public static Set<String> getPlatformPermissions() {
        return PLATFORM_PERMISSIONS.keySet();
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
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(LAUNCHER_INTENT,
                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE)) {
            launcherPkgs.add(info.activityInfo.packageName);
        }

        return launcherPkgs;
    }

    public static List<ApplicationInfo> getAllInstalledApplications(Context context) {
        return context.getPackageManager().getInstalledApplications(0);
    }

    /**
     * Is the group or background group user sensitive?
     *
     * @param group The group that might be user sensitive
     *
     * @return {@code true} if the group (or it's subgroup) is user sensitive.
     */
    public static boolean isGroupOrBgGroupUserSensitive(AppPermissionGroup group) {
        return group.isUserSensitive() || (group.getBackgroundPermissions() != null
                && group.getBackgroundPermissions().isUserSensitive());
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
        if (group.getName().equals(STORAGE) && !group.isNonIsolatedStorage()) {
            return Html.fromHtml(
                    String.format(context.getResources().getConfiguration().getLocales().get(0),
                            context.getString(R.string.permgrouprequest_storage_isolated),
                            appLabel), 0);
        } else if (requestRes != 0) {
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
     * Build a string representing the given time if it happened on the current day and the date
     * otherwise.
     *
     * @param context the context.
     * @param lastAccessTime the time in milliseconds.
     *
     * @return a string representing the time or date of the given time or null if the time is 0.
     */
    public static @Nullable String getAbsoluteTimeString(@NonNull Context context,
            long lastAccessTime) {
        if (lastAccessTime == 0) {
            return null;
        }
        if (isToday(lastAccessTime)) {
            return DateFormat.getTimeFormat(context).format(lastAccessTime);
        } else {
            return DateFormat.getMediumDateFormat(context).format(lastAccessTime);
        }
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

    /**
     * Check whether the given time (in milliseconds) is in the current day.
     *
     * @param time the time in milliseconds
     *
     * @return whether the given time is in the current day.
     */
    private static boolean isToday(long time) {
        Calendar today = Calendar.getInstance(Locale.getDefault());
        today.setTimeInMillis(System.currentTimeMillis());
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar date = Calendar.getInstance(Locale.getDefault());
        date.setTimeInMillis(time);
        return !date.before(today);
    }

    /**
     * Add a menu item for searching Settings, if there is an activity handling the action.
     *
     * @param menu the menu to add the menu item into
     * @param context the context for checking whether there is an activity handling the action
     */
    public static void prepareSearchMenuItem(@NonNull Menu menu, @NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_SEARCH_SETTINGS);
        if (context.getPackageManager().resolveActivity(intent, 0) == null) {
            return;
        }
        MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.search_menu);
        searchItem.setIcon(R.drawable.ic_search_24dp);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        searchItem.setOnMenuItemClickListener(item -> {
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(LOG_TAG, "Cannot start activity to search settings", e);
            }
            return true;
        });
    }

    /**
     * Get badged app icon if necessary, similar as used in the Settings UI.
     *
     * @param context The context to use
     * @param appInfo The app the icon belong to
     *
     * @return The icon to use
     */
    public static @NonNull Drawable getBadgedIcon(@NonNull Context context,
            @NonNull ApplicationInfo appInfo) {
        UserHandle user = UserHandle.getUserHandleForUid(appInfo.uid);
        try (IconFactory iconFactory = IconFactory.obtain(context)) {
            Bitmap iconBmp = iconFactory.createBadgedIconBitmap(
                    appInfo.loadUnbadgedIcon(context.getPackageManager()), user, false).icon;
            return new BitmapDrawable(context.getResources(), iconBmp);
        }
    }

    /**
     * Get a string saying what apps with the given permission group can do.
     *
     * @param context The context to use
     * @param groupName The name of the permission group
     * @param description The description of the permission group
     *
     * @return a string saying what apps with the given permission group can do.
     */
    public static @NonNull String getPermissionGroupDescriptionString(@NonNull Context context,
            @NonNull String groupName, @NonNull CharSequence description) {
        switch (groupName) {
            case ACTIVITY_RECOGNITION:
                return context.getString(
                        R.string.permission_description_summary_activity_recognition);
            case CALENDAR:
                return context.getString(R.string.permission_description_summary_calendar);
            case CALL_LOG:
                return context.getString(R.string.permission_description_summary_call_log);
            case CAMERA:
                return context.getString(R.string.permission_description_summary_camera);
            case CONTACTS:
                return context.getString(R.string.permission_description_summary_contacts);
            case LOCATION:
                return context.getString(R.string.permission_description_summary_location);
            case MICROPHONE:
                return context.getString(R.string.permission_description_summary_microphone);
            case PHONE:
                return context.getString(R.string.permission_description_summary_phone);
            case SENSORS:
                return context.getString(R.string.permission_description_summary_sensors);
            case SMS:
                return context.getString(R.string.permission_description_summary_sms);
            case STORAGE:
                return context.getString(R.string.permission_description_summary_storage);
            default:
                return context.getString(R.string.permission_description_summary_generic,
                        description);
        }
    }

    /**
     * Whether the Location Access Check is enabled.
     *
     * @return {@code true} iff the Location Access Check is enabled.
     */
    public static boolean isLocationAccessCheckEnabled() {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_LOCATION_ACCESS_CHECK_ENABLED, true);
    }

    /**
     * Get a device protected storage based shared preferences. Avoid storing sensitive data in it.
     *
     * @param context the context to get the shared preferences
     * @return a device protected storage based shared preferences
     */
    @NonNull
    public static SharedPreferences getDeviceProtectedSharedPreferences(@NonNull Context context) {
        if (!context.isDeviceProtectedStorage()) {
            context = context.createDeviceProtectedStorageContext();
        }
        return context.getSharedPreferences(Constants.PREFERENCES_FILE, MODE_PRIVATE);
    }

    /**
     * Update the {@link PackageManager#FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED} and
     * {@link PackageManager#FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED} for all apps of this user.
     *
     * @see PerUserUidToSensitivityLiveData#loadValueInBackground()
     */
    public static void updateUserSensitive(@NonNull Application application,
            @NonNull UserHandle user) {
        Context userContext = getParentUserContext(application);
        PackageManager pm = userContext.getPackageManager();
        RoleManager rm = userContext.getSystemService(RoleManager.class);
        SharedPreferences prefs = userContext.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE);

        boolean showAssistantRecordAudio = prefs.getBoolean(
                ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY, false);
        Set<String> overriddenUids = prefs.getStringSet(FORCED_USER_SENSITIVE_UIDS_KEY,
                Collections.emptySet());

        List<String> assistants = rm.getRoleHolders(ROLE_ASSISTANT);
        String assistant = null;
        if (!assistants.isEmpty()) {
            if (assistants.size() > 1) {
                Log.wtf(LOG_TAG, "Assistant role is not exclusive");
            }

            // Assistant is an exclusive role
            assistant = assistants.get(0);
        }

        PerUserUidToSensitivityLiveData appUserSensitivityLiveData =
                PerUserUidToSensitivityLiveData.get(user, application);

        // uid -> permission -> flags
        SparseArray<ArrayMap<String, Integer>> uidUserSensitivity =
                appUserSensitivityLiveData.loadValueInBackground();

        // Apply the update
        int numUids = uidUserSensitivity.size();
        for (int uidNum = 0; uidNum < numUids; uidNum++) {
            int uid = uidUserSensitivity.keyAt(uidNum);

            String[] uidPkgs = pm.getPackagesForUid(uid);
            if (uidPkgs == null) {
                continue;
            }

            boolean isOverridden = overriddenUids.contains(String.valueOf(uid));
            boolean isAssistantUid = ArrayUtils.contains(uidPkgs, assistant);

            ArrayMap<String, Integer> uidPermissions = uidUserSensitivity.valueAt(uidNum);

            int numPerms = uidPermissions.size();
            for (int permNum = 0; permNum < numPerms; permNum++) {
                String perm = uidPermissions.keyAt(permNum);

                for (String uidPkg : uidPkgs) {
                    int flags = isOverridden ? FLAGS_ALWAYS_USER_SENSITIVE : uidPermissions.valueAt(
                            permNum);

                    if (isAssistantUid && perm.equals(RECORD_AUDIO)) {
                        flags = showAssistantRecordAudio ? FLAGS_ALWAYS_USER_SENSITIVE : 0;
                    }

                    try {
                        pm.updatePermissionFlags(perm, uidPkg, FLAGS_ALWAYS_USER_SENSITIVE, flags,
                                user);
                        break;
                    } catch (IllegalArgumentException e) {
                        Log.e(LOG_TAG, "Unexpected exception while updating flags for "
                                + uidPkg + " permission " + perm, e);
                    }
                }
            }
        }
    }

    /**
     * Get context of the parent user of the profile group (i.e. usually the 'personal' profile,
     * not the 'work' profile).
     *
     * @param context The context of a user of the profile user group.
     *
     * @return The context of the parent user
     */
    public static Context getParentUserContext(@NonNull Context context) {
        UserHandle parentUser = getSystemServiceSafe(context, UserManager.class)
                .getProfileParent(UserHandle.of(myUserId()));

        if (parentUser == null) {
            return context;
        }

        // In a multi profile environment perform all operations as the parent user of the
        // current profile
        try {
            return context.createPackageContextAsUser(context.getPackageName(), 0,
                    parentUser);
        } catch (PackageManager.NameNotFoundException e) {
            // cannot happen
            throw new IllegalStateException("Could not switch to parent user " + parentUser, e);
        }
    }
}
