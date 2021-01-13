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

import static android.Manifest.permission.BIND_SOUND_TRIGGER_DETECTION_SERVICE;
import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
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
import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED;
import static android.content.pm.PackageManager.GET_SERVICES;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.UserHandle.myUserId;

import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.Manifest;
import android.app.Application;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.carrier.CarrierService;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.BidiFormatter;
import androidx.core.util.Preconditions;

import com.android.launcher3.icons.IconFactory;
import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.PermissionControllerApplication;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class Utils {

    private static final String LOG_TAG = "Utils";

    public static final String OS_PKG = "android";

    public static final float DEFAULT_MAX_LABEL_SIZE_PX = 500f;

    /** Whether to show the Permissions Hub. */
    private static final String PROPERTY_PERMISSIONS_HUB_ENABLED = "permissions_hub_enabled";

    /** The timeout for one-time permissions */
    private static final String PROPERTY_ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS =
            "one_time_permissions_timeout_millis";

    /** The timeout for auto-revoke permissions */
    public static final String PROPERTY_AUTO_REVOKE_UNUSED_THRESHOLD_MILLIS =
            "auto_revoke_unused_threshold_millis2";

    /** The frequency of running the job for auto-revoke permissions */
    public static final String PROPERTY_AUTO_REVOKE_CHECK_FREQUENCY_MILLIS =
            "auto_revoke_check_frequency_millis";

    /** Whether to show location access check notifications. */
    private static final String PROPERTY_LOCATION_ACCESS_CHECK_ENABLED =
            "location_access_check_enabled";

    /** All permission whitelists. */
    public static final int FLAGS_PERMISSION_WHITELIST_ALL =
            PackageManager.FLAG_PERMISSION_WHITELIST_SYSTEM
                    | PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
                    | PackageManager.FLAG_PERMISSION_WHITELIST_INSTALLER;

    /** All permission restriction excemptions. */
    public static final int FLAGS_PERMISSION_RESTRICTION_ANY_EXEMPT =
            FLAG_PERMISSION_RESTRICTION_SYSTEM_EXEMPT
                    | FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
                    | FLAG_PERMISSION_RESTRICTION_INSTALLER_EXEMPT;

    /**
     * The default length of the timeout for one-time permissions
     */
    public static final long ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS = 1 * 60 * 1000; // 1 minute

    /** Mapping permission -> group for all dangerous platform permissions */
    private static final ArrayMap<String, String> PLATFORM_PERMISSIONS;

    /** Mapping group -> permissions for all dangerous platform permissions */
    private static final ArrayMap<String, ArrayList<String>> PLATFORM_PERMISSION_GROUPS;

    /** Set of groups that will be able to receive one-time grant */
    private static final ArraySet<String> ONE_TIME_PERMISSION_GROUPS;

    private static final ArrayMap<String, Integer> PERM_GROUP_REQUEST_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_REQUEST_DETAIL_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_BACKGROUND_REQUEST_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_UPGRADE_REQUEST_RES;
    private static final ArrayMap<String, Integer> PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES;

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

        ONE_TIME_PERMISSION_GROUPS = new ArraySet<>();
        ONE_TIME_PERMISSION_GROUPS.add(LOCATION);
        ONE_TIME_PERMISSION_GROUPS.add(CAMERA);
        ONE_TIME_PERMISSION_GROUPS.add(MICROPHONE);

        PERM_GROUP_REQUEST_RES = new ArrayMap<>();
        PERM_GROUP_REQUEST_RES.put(CONTACTS, R.string.permgrouprequest_contacts);
        PERM_GROUP_REQUEST_RES.put(LOCATION, R.string.permgrouprequest_location);
        PERM_GROUP_REQUEST_RES.put(CALENDAR, R.string.permgrouprequest_calendar);
        PERM_GROUP_REQUEST_RES.put(SMS, R.string.permgrouprequest_sms);
        PERM_GROUP_REQUEST_RES.put(STORAGE, R.string.permgrouprequest_storage);
        PERM_GROUP_REQUEST_RES.put(MICROPHONE, R.string.permgrouprequest_microphone);
        PERM_GROUP_REQUEST_RES
                .put(ACTIVITY_RECOGNITION, R.string.permgrouprequest_activityRecognition);
        PERM_GROUP_REQUEST_RES.put(CAMERA, R.string.permgrouprequest_camera);
        PERM_GROUP_REQUEST_RES.put(CALL_LOG, R.string.permgrouprequest_calllog);
        PERM_GROUP_REQUEST_RES.put(PHONE, R.string.permgrouprequest_phone);
        PERM_GROUP_REQUEST_RES.put(SENSORS, R.string.permgrouprequest_sensors);

        PERM_GROUP_REQUEST_DETAIL_RES = new ArrayMap<>();
        PERM_GROUP_REQUEST_DETAIL_RES.put(LOCATION, R.string.permgrouprequestdetail_location);

        PERM_GROUP_BACKGROUND_REQUEST_RES = new ArrayMap<>();
        PERM_GROUP_BACKGROUND_REQUEST_RES
                .put(LOCATION, R.string.permgroupbackgroundrequest_location);

        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES = new ArrayMap<>();
        PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES
                .put(LOCATION, R.string.permgroupbackgroundrequestdetail_location);

        PERM_GROUP_UPGRADE_REQUEST_RES = new ArrayMap<>();
        PERM_GROUP_UPGRADE_REQUEST_RES.put(LOCATION, R.string.permgroupupgraderequest_location);

        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES = new ArrayMap<>();
        PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES
                .put(LOCATION, R.string.permgroupupgraderequestdetail_location);
    }

    private Utils() {
        /* do nothing - hide constructor */
    }

    private static ArrayMap<UserHandle, Context> sUserContexts = new ArrayMap<>();

    public enum ForegroundCapableType {
        SOUND_TRIGGER,
        ASSISTANT,
        VOICE_INTERACTION,
        CARRIER_SERVICE,
        NONE
    }

    /**
     * Creates and caches a PackageContext for the requested user, or returns the previously cached
     * value. The package of the PackageContext is the application's package.
     *
     * @param app The currently running application
     * @param user The desired user for the context
     *
     * @return The generated or cached Context for the requested user
     *
     * @throws PackageManager.NameNotFoundException If the app has no package name attached
     */
    public static @NonNull Context getUserContext(Application app, UserHandle user) throws
            PackageManager.NameNotFoundException {
        if (!sUserContexts.containsKey(user)) {
            sUserContexts.put(user, app.getApplicationContext()
                    .createPackageContextAsUser(app.getPackageName(), 0, user));
        }
        return sUserContexts.get(user);
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
     * Returns true if a permission is dangerous, installed, and not removed
     * @param permissionInfo The permission we wish to check
     * @return If all of the conditions are met
     */
    public static boolean isPermissionDangerousInstalledNotRemoved(PermissionInfo permissionInfo) {
        return permissionInfo != null
                  && permissionInfo.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
                  && (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) != 0
                  && (permissionInfo.flags & PermissionInfo.FLAG_REMOVED) == 0;
    }

    /**
     * Get permission group a platform permission belongs to, or null if the permission is not a
     * platform permission.
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

        /*
         * If the undefined group is requested, the package manager will return all platform
         * permissions, since they are marked as Undefined in the manifest. Do not return these
         * permissions.
         */
        if (group.equals(Manifest.permission_group.UNDEFINED)) {
            List<PermissionInfo> undefinedPerms = new ArrayList<>();
            for (PermissionInfo permissionInfo : permissions) {
                String permGroup = getGroupOfPlatformPermission(permissionInfo.name);
                if (permGroup == null || permGroup.equals(Manifest.permission_group.UNDEFINED)) {
                    undefinedPerms.add(permissionInfo);
                }
            }
            return undefinedPerms;
        }

        return permissions;
    }

    /**
     * Get the {@link PermissionInfo infos} for all runtime installed permission infos belonging to
     * a group.
     *
     * @param pm    Package manager to use to resolve permission infos
     * @param group the group
     *
     * @return The infos of installed runtime permissions belonging to the group or an empty list
     * if the group does not have runtime permissions
     */
    public static @NonNull List<PermissionInfo> getInstalledRuntimePermissionInfosForGroup(
            @NonNull PackageManager pm, @NonNull String group)
            throws PackageManager.NameNotFoundException {
        List<PermissionInfo> permissions = pm.queryPermissionsByGroup(group, 0);
        permissions.addAll(getPlatformPermissionsOfGroup(pm, group));

        List<PermissionInfo> installedRuntime = new ArrayList<>();
        for (PermissionInfo permissionInfo: permissions) {
            if (permissionInfo.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
                    && (permissionInfo.flags & PermissionInfo.FLAG_INSTALLED) != 0
                    && (permissionInfo.flags & PermissionInfo.FLAG_REMOVED) == 0) {
                installedRuntime.add(permissionInfo);
            }
        }

        /*
         * If the undefined group is requested, the package manager will return all platform
         * permissions, since they are marked as Undefined in the manifest. Do not return these
         * permissions.
         */
        if (group.equals(Manifest.permission_group.UNDEFINED)) {
            List<PermissionInfo> undefinedPerms = new ArrayList<>();
            for (PermissionInfo permissionInfo : installedRuntime) {
                String permGroup = getGroupOfPlatformPermission(permissionInfo.name);
                if (permGroup == null || permGroup.equals(Manifest.permission_group.UNDEFINED)) {
                    undefinedPerms.add(permissionInfo);
                }
            }
            return undefinedPerms;
        }

        return installedRuntime;
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
     * Get the names of the runtime platform permissions
     *
     * @return the names of the runtime platform permissions.
     */
    public static List<String> getRuntimePlatformPermissionNames() {
        return new ArrayList<>(PLATFORM_PERMISSIONS.keySet());
    }

    /**
     * Is the permissions a platform runtime permission
     *
     * @return the names of the runtime platform permissions.
     */
    public static boolean isRuntimePlatformPermission(@NonNull String permission) {
        return PLATFORM_PERMISSIONS.containsKey(permission);
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
            return Html.fromHtml(context.getResources().getString(requestRes, appLabel), 0);
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

    public static long getOneTimePermissionsTimeout() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_PERMISSIONS,
                PROPERTY_ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS, ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS);
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

    /**
     * Whether the permission group supports one-time
     * @param permissionGroup The permission group to check
     * @return {@code true} iff the group supports one-time
     */
    public static boolean supportsOneTimeGrant(String permissionGroup) {
        return ONE_TIME_PERMISSION_GROUPS.contains(permissionGroup);
    }

    /**
     * The resource id for the request message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getRequest(String groupName) {
        return PERM_GROUP_REQUEST_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the request detail message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getRequestDetail(String groupName) {
        return PERM_GROUP_REQUEST_DETAIL_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the background request message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getBackgroundRequest(String groupName) {
        return PERM_GROUP_BACKGROUND_REQUEST_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the background request detail message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getBackgroundRequestDetail(String groupName) {
        return PERM_GROUP_BACKGROUND_REQUEST_DETAIL_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the upgrade request message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getUpgradeRequest(String groupName) {
        return PERM_GROUP_UPGRADE_REQUEST_RES.getOrDefault(groupName, 0);
    }

    /**
     * The resource id for the upgrade request detail message for a permission group
     * @param groupName Permission group name
     * @return The id or 0 if the permission group doesn't exist or have a message
     */
    public static int getUpgradeRequestDetail(String groupName) {
        return PERM_GROUP_UPGRADE_REQUEST_DETAIL_RES.getOrDefault(groupName, 0);
    }

    /**
     * Checks whether a package has an active one-time permission according to the system server's
     * flags
     *
     * @param context the {@code Context} to retrieve {@code PackageManager}
     * @param packageName The package to check for
     * @return Whether a package has an active one-time permission
     */
    public static boolean hasOneTimePermissions(Context context, String packageName) {
        String[] permissions;
        PackageManager pm = context.getPackageManager();
        try {
            permissions = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
        } catch (NameNotFoundException e) {
            Log.w(LOG_TAG, "Checking for one-time permissions in nonexistent package");
            return false;
        }
        if (permissions == null) {
            return false;
        }
        for (String permissionName : permissions) {
            if ((pm.getPermissionFlags(permissionName, packageName, Process.myUserHandle())
                    & PackageManager.FLAG_PERMISSION_ONE_TIME) != 0
                    && pm.checkPermission(permissionName, packageName)
                    == PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a random session ID value that's guaranteed to not be {@code INVALID_SESSION_ID}.
     *
     * @return A valid session ID.
     */
    public static long getValidSessionId() {
        long sessionId = INVALID_SESSION_ID;
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = new Random().nextLong();
        }
        return sessionId;
    }

    /**
     * Gets the label of the Settings application
     *
     * @param pm The packageManager used to get the activity resolution
     *
     * @return The CharSequence title of the settings app
     */
    @Nullable
    public static CharSequence getSettingsLabelForNotifications(PackageManager pm) {
        // We pretend we're the Settings app sending the notification, so figure out its name.
        Intent openSettingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo resolveInfo = pm.resolveActivity(openSettingsIntent, MATCH_SYSTEM_ONLY);
        if (resolveInfo == null) {
            return null;
        }
        return pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo);
    }

    /**
     * If an app could have foreground capabilities it is because it meets some criteria. This
     * function returns which criteria it meets.
     * @param context The context as the user of interest.
     * @param packageName The package to check.
     * @return the type of foreground capable app.
     * @throws NameNotFoundException
     */
    public static @NonNull ForegroundCapableType getForegroundCapableType(@NonNull Context context,
            @NonNull String packageName) throws NameNotFoundException {

        PackageManager pm = context.getPackageManager();

        // Apps which can be bound by SoundTriggerService
        if (pm.checkPermission(CAPTURE_AUDIO_HOTWORD, packageName) == PERMISSION_GRANTED) {
            ServiceInfo[] services = pm.getPackageInfo(packageName, GET_SERVICES).services;
            if (services != null) {
                for (ServiceInfo service : services) {
                    if (BIND_SOUND_TRIGGER_DETECTION_SERVICE.equals(service.permission)) {
                        return ForegroundCapableType.SOUND_TRIGGER;
                    }
                }
            }
        }

        // VoiceInteractionService
        if (context.getSystemService(RoleManager.class).getRoleHolders(RoleManager.ROLE_ASSISTANT)
                .contains(packageName)) {
            return ForegroundCapableType.ASSISTANT;
        }
        ContentResolver contentResolver = context.getContentResolver();
        if (contentResolver != null) {
            String voiceInteraction = Settings.Secure.getString(contentResolver,
                    "voice_interaction_service");
            if (!TextUtils.isEmpty(voiceInteraction)) {
                ComponentName component = ComponentName.unflattenFromString(voiceInteraction);
                if (component != null
                        && TextUtils.equals(packageName, component.getPackageName())) {
                    return ForegroundCapableType.VOICE_INTERACTION;
                }
            }
        }

        // Carrier privileged apps implementing the carrier service
        final TelephonyManager telephonyManager =
                context.getSystemService(TelephonyManager.class);
        int numPhones = telephonyManager.getActiveModemCount();
        for (int phoneId = 0; phoneId < numPhones; phoneId++) {
            List<String> packages = telephonyManager.getCarrierPackageNamesForIntentAndPhone(
                    new Intent(CarrierService.CARRIER_SERVICE_INTERFACE), phoneId);
            if (packages != null && packages.contains(packageName)) {
                return ForegroundCapableType.CARRIER_SERVICE;
            }
        }

        return ForegroundCapableType.NONE;
    }

    /**
     * This tells whether we should blame the app for potential background access. Intended to be
     * used for creating Ui.
     * @param context The context as the user of interest
     * @param packageName The package to check
     * @return true if the given package could possibly have foreground capabilities while in the
     * background, otherwise false.
     * @throws NameNotFoundException
     */
    public static boolean couldHaveForegroundCapabilities(@NonNull Context context,
            @NonNull String packageName) throws NameNotFoundException {
        return getForegroundCapableType(context, packageName) != ForegroundCapableType.NONE;
    }

    /**
     * Determines if a given user is disabled, or is a work profile.
     * @param user The user to check
     * @return true if the user is disabled, or the user is a work profile
     */
    public static boolean isUserDisabledOrWorkProfile(UserHandle user) {
        Application app = PermissionControllerApplication.get();
        UserManager userManager = app.getSystemService(UserManager.class);
        // In android TV, parental control accounts are managed profiles
        return !userManager.getEnabledProfiles().contains(user)
                || (userManager.isManagedProfile(user.getIdentifier())
                && !DeviceUtils.isTelevision(app));
    }

    /**
     * @return Whether a package is an emergency app.
     */
    public static boolean isEmergencyApp(@NonNull Context context,  @NonNull String packageName) {
        try {
            return context.getSystemService(RoleManager.class)
                    .getRoleHolders(RoleManager.ROLE_EMERGENCY).contains(packageName);
        } catch (Throwable t) {
            // Avoid crashing for any reason, this isn't very well tested
            Log.e(LOG_TAG, "Unable to check if " + packageName + " is an emergency app.", t);
            return false;
        }
    }
}
