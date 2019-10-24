/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller.role.model;

import android.app.ActivityManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.android.packageinstaller.Constants;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.ui.TwoTargetPreference;
import com.android.packageinstaller.role.utils.PackageUtils;
import com.android.packageinstaller.role.utils.UserUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Specifies a role and its properties.
 * <p>
 * A role is a unique name within the system associated with certain privileges. There can be
 * multiple applications qualifying for a role, but only a subset of them can become role holders.
 * To qualify for a role, an application must meet certain requirements, including defining certain
 * components in its manifest. Then the application will need user consent to become the role
 * holder.
 * <p>
 * Upon becoming a role holder, the application may be granted certain permissions, have certain
 * app ops set to certain modes and certain {@code Activity} components configured as preferred for
 * certain {@code Intent} actions. When an application loses its role, these privileges will also be
 * revoked.
 *
 * @see android.app.role.RoleManager
 */
public class Role {

    private static final String LOG_TAG = Role.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final String PACKAGE_NAME_ANDROID_SYSTEM = "android";

    /**
     * The name of this role. Must be unique.
     */
    @NonNull
    private final String mName;

    /**
     * The behavior of this role.
     */
    @Nullable
    private final RoleBehavior mBehavior;

    /**
     * The string resource for the description of this role.
     */
    @StringRes
    private final int mDescriptionResource;

    /**
     * Whether this role is exclusive, i.e. allows at most one holder.
     */
    private final boolean mExclusive;

    /**
     * The string resource for the label of this role.
     */
    @StringRes
    private final int mLabelResource;

    /**
     * The string resource for the request description of this role, shown below the selected app in
     * the request role dialog.
     */
    @StringRes
    private final int mRequestDescriptionResource;

    /**
     * The string resource for the request title of this role, shown as the title of the request
     * role dialog.
     */
    @StringRes
    private final int mRequestTitleResource;

    /**
     * Whether this role is requestable by applications with
     * {@link android.app.role.RoleManager#createRequestRoleIntent(String)}.
     */
    private final boolean mRequestable;

    /**
     * The string resource for the short label of this role, currently used when in a list of roles.
     */
    @StringRes
    private final int mShortLabelResource;

    /**
     * Whether the UI for this role will show the "None" item. Only valid if this role is
     * {@link #mExclusive exclusive}, and {@link #getFallbackHolder(Context)} should also return
     * empty to allow actually selecting "None".
     */
    private final boolean mShowNone;

    /**
     * Whether this role only accepts system apps as its holders.
     */
    private final boolean mSystemOnly;

    /**
     * The required components for an application to qualify for this role.
     */
    @NonNull
    private final List<RequiredComponent> mRequiredComponents;

    /**
     * The permissions to be granted by this role.
     */
    @NonNull
    private final List<String> mPermissions;

    /**
     * The app ops to be set to allowed by this role.
     */
    @NonNull
    private final List<AppOp> mAppOps;

    /**
     * The set of preferred {@code Activity} configurations to be configured by this role.
     */
    @NonNull
    private final List<PreferredActivity> mPreferredActivities;

    public Role(@NonNull String name, @Nullable RoleBehavior behavior,
            @StringRes int descriptionResource, boolean exclusive, @StringRes int labelResource,
            @StringRes int requestDescriptionResource, @StringRes int requestTitleResource,
            boolean requestable, @StringRes int shortLabelResource, boolean showNone,
            boolean systemOnly, @NonNull List<RequiredComponent> requiredComponents,
            @NonNull List<String> permissions, @NonNull List<AppOp> appOps,
            @NonNull List<PreferredActivity> preferredActivities) {
        mName = name;
        mBehavior = behavior;
        mDescriptionResource = descriptionResource;
        mExclusive = exclusive;
        mLabelResource = labelResource;
        mRequestDescriptionResource = requestDescriptionResource;
        mRequestTitleResource = requestTitleResource;
        mRequestable = requestable;
        mShortLabelResource = shortLabelResource;
        mShowNone = showNone;
        mSystemOnly = systemOnly;
        mRequiredComponents = requiredComponents;
        mPermissions = permissions;
        mAppOps = appOps;
        mPreferredActivities = preferredActivities;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @Nullable
    public RoleBehavior getBehavior() {
        return mBehavior;
    }

    @StringRes
    public int getDescriptionResource() {
        return mDescriptionResource;
    }

    public boolean isExclusive() {
        return mExclusive;
    }

    @StringRes
    public int getLabelResource() {
        return mLabelResource;
    }

    @StringRes
    public int getRequestDescriptionResource() {
        return mRequestDescriptionResource;
    }

    @StringRes
    public int getRequestTitleResource() {
        return mRequestTitleResource;
    }

    public boolean isRequestable() {
        return mRequestable;
    }

    @StringRes
    public int getShortLabelResource() {
        return mShortLabelResource;
    }

    /**
     * @see #mShowNone
     */
    public boolean shouldShowNone() {
        return mShowNone;
    }

    @NonNull
    public List<RequiredComponent> getRequiredComponents() {
        return mRequiredComponents;
    }

    @NonNull
    public List<String> getPermissions() {
        return mPermissions;
    }

    @NonNull
    public List<AppOp> getAppOps() {
        return mAppOps;
    }

    @NonNull
    public List<PreferredActivity> getPreferredActivities() {
        return mPreferredActivities;
    }

    /**
     * Callback when this role is added to the system for the first time.
     *
     * @param context the {@code Context} to retrieve system services
     */
    public void onRoleAdded(@NonNull Context context) {
        if (mBehavior != null) {
            mBehavior.onRoleAdded(this, context);
        }
    }

    /**
     * Check whether this role is available.
     *
     * @param user the user to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether this role is available.
     */
    public boolean isAvailableAsUser(@NonNull UserHandle user, @NonNull Context context) {
        if (mBehavior != null) {
            return mBehavior.isAvailableAsUser(this, user, context);
        }
        return true;
    }

    /**
     * Check whether this role is available, for current user.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether this role is available.
     */
    public boolean isAvailable(@NonNull Context context) {
        return isAvailableAsUser(Process.myUserHandle(), context);
    }

    /**
     * Get the default holders of this role, which will be added when the role is added for the
     * first time.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return the list of package names of the default holders
     */
    @NonNull
    public List<String> getDefaultHolders(@NonNull Context context) {
        if (mBehavior != null) {
            return mBehavior.getDefaultHolders(this, context);
        }
        return Collections.emptyList();
    }

    /**
     * Get the fallback holder of this role, which will be added whenever there are no role holders.
     * <p>
     * Should return {@code null} if this role {@link #mShowNone shows a "None" item}.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return the package name of the fallback holder, or {@code null} if none
     */
    @Nullable
    public String getFallbackHolder(@NonNull Context context) {
        if (mBehavior != null && !isNoneHolderSelected(context)) {
            return mBehavior.getFallbackHolder(this, context);
        }
        return null;
    }

    /**
     * Check whether this role should be visible to user.
     *
     * @param user the user to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether this role should be visible to user
     */
    public boolean isVisibleAsUser(@NonNull UserHandle user, @NonNull Context context) {
        if (mBehavior != null) {
            return mBehavior.isVisibleAsUser(this, user, context);
        }
        return true;
    }

    /**
     * Check whether this role should be visible to user, for current user.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether this role should be visible to user.
     */
    public boolean isVisible(@NonNull Context context) {
        return isVisibleAsUser(Process.myUserHandle(), context);
    }

    /**
     * Get the {@link Intent} to manage this role, or {@code null} to use the default UI.
     *
     * @param user the user to manage this role for
     * @param context the {@code Context} to retrieve system services
     *
     * @return the {@link Intent} to manage this role, or {@code null} to use the default UI.
     */
    @Nullable
    public Intent getManageIntentAsUser(@NonNull UserHandle user, @NonNull Context context) {
        if (mBehavior != null) {
            return mBehavior.getManageIntentAsUser(this, user, context);
        }
        return null;
    }

    /**
     * Prepare a {@link Preference} for this role.
     *
     * @param preference the {@link Preference} for this role
     * @param user the user for this role
     * @param context the {@code Context} to retrieve system services
     */
    public void preparePreferenceAsUser(@NonNull TwoTargetPreference preference,
            @NonNull UserHandle user, @NonNull Context context) {
        if (mBehavior != null) {
            mBehavior.preparePreferenceAsUser(this, preference, user, context);
        }
    }

    /**
     * Check whether a qualifying application should be visible to user.
     *
     * @param applicationInfo the {@link ApplicationInfo} for the application
     * @param user the user for the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether the qualifying application should be visible to user
     */
    public boolean isApplicationVisibleAsUser(@NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {
        if (mBehavior != null) {
            return mBehavior.isApplicationVisibleAsUser(this, applicationInfo, user, context);
        }
        return true;
    }

    /**
     * Prepare a {@link Preference} for an application.
     *
     * @param preference the {@link Preference} for the application
     * @param applicationInfo the {@link ApplicationInfo} for the application
     * @param user the user for the application
     * @param context the {@code Context} to retrieve system services
     */
    public void prepareApplicationPreferenceAsUser(@NonNull Preference preference,
            @NonNull ApplicationInfo applicationInfo, @NonNull UserHandle user,
            @NonNull Context context) {
        if (mBehavior != null) {
            mBehavior.prepareApplicationPreferenceAsUser(this, preference, applicationInfo, user,
                    context);
        }
    }

    /**
     * Get the confirmation message for adding an application as a holder of this role.
     *
     * @param packageName the package name of the application to get confirmation message for
     * @param context the {@code Context} to retrieve system services
     *
     * @return the confirmation message, or {@code null} if no confirmation is needed
     */
    @Nullable
    public CharSequence getConfirmationMessage(@NonNull String packageName,
            @NonNull Context context) {
        if (mBehavior != null) {
            return mBehavior.getConfirmationMessage(this, packageName, context);
        }
        return null;
    }

    /**
     * Check whether a package is qualified for this role, i.e. whether it contains all the required
     * components (plus meeting some other general restrictions).
     *
     * @param packageName the package name to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether the package is qualified for a role
     */
    public boolean isPackageQualified(@NonNull String packageName, @NonNull Context context) {
        if (!isPackageMinimallyQualifiedAsUser(packageName, Process.myUserHandle(), context)) {
            return false;
        }

        if (mBehavior != null) {
            Boolean isPackageQualified = mBehavior.isPackageQualified(this, packageName, context);
            if (isPackageQualified != null) {
                return isPackageQualified;
            }
        }

        int requiredComponentsSize = mRequiredComponents.size();
        for (int i = 0; i < requiredComponentsSize; i++) {
            RequiredComponent requiredComponent = mRequiredComponents.get(i);
            if (requiredComponent.getQualifyingComponentForPackage(packageName, context) == null) {
                Log.w(LOG_TAG, packageName + " not qualified for " + mName
                        + " due to missing " + requiredComponent);
                return false;
            }
        }

        return true;
    }

    /**
     * Get the list of packages that are qualified for this role, i.e. packages containing all the
     * required components (plus meeting some other general restrictions).
     *
     * @param user the user to get the qualifying packages.
     * @param context the {@code Context} to retrieve system services
     *
     * @return the list of packages that are qualified for this role
     */
    @NonNull
    public List<String> getQualifyingPackagesAsUser(@NonNull UserHandle user,
            @NonNull Context context) {
        List<String> qualifyingPackages = null;

        if (mBehavior != null) {
            qualifyingPackages = mBehavior.getQualifyingPackagesAsUser(this, user, context);
        }

        if (qualifyingPackages == null) {
            ArrayMap<String, Integer> packageComponentCountMap = new ArrayMap<>();
            int requiredComponentsSize = mRequiredComponents.size();
            for (int requiredComponentsIndex = 0; requiredComponentsIndex < requiredComponentsSize;
                    requiredComponentsIndex++) {
                RequiredComponent requiredComponent = mRequiredComponents.get(
                        requiredComponentsIndex);

                // This returns at most one component per package.
                List<ComponentName> qualifyingComponents =
                        requiredComponent.getQualifyingComponentsAsUser(user, context);
                int qualifyingComponentsSize = qualifyingComponents.size();
                for (int qualifyingComponentsIndex = 0;
                        qualifyingComponentsIndex < qualifyingComponentsSize;
                        ++qualifyingComponentsIndex) {
                    ComponentName componentName = qualifyingComponents.get(
                            qualifyingComponentsIndex);

                    String packageName = componentName.getPackageName();
                    Integer componentCount = packageComponentCountMap.get(packageName);
                    packageComponentCountMap.put(packageName, componentCount == null ? 1
                            : componentCount + 1);
                }
            }

            qualifyingPackages = new ArrayList<>();
            int packageComponentCountMapSize = packageComponentCountMap.size();
            for (int i = 0; i < packageComponentCountMapSize; i++) {
                int componentCount = packageComponentCountMap.valueAt(i);

                if (componentCount != requiredComponentsSize) {
                    continue;
                }
                String packageName = packageComponentCountMap.keyAt(i);
                qualifyingPackages.add(packageName);
            }
        }

        int qualifyingPackagesSize = qualifyingPackages.size();
        for (int i = 0; i < qualifyingPackagesSize; ) {
            String packageName = qualifyingPackages.get(i);

            if (!isPackageMinimallyQualifiedAsUser(packageName, user, context)) {
                qualifyingPackages.remove(i);
                qualifyingPackagesSize--;
            } else {
                i++;
            }
        }

        return qualifyingPackages;
    }

    private boolean isPackageMinimallyQualifiedAsUser(
            @NonNull String packageName, @NonNull UserHandle user, @NonNull Context context) {
        if (Objects.equals(packageName, PACKAGE_NAME_ANDROID_SYSTEM)) {
            return false;
        }

        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName, user,
                context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Cannot get ApplicationInfo for package: " + packageName + ", user: "
                    + user.getIdentifier());
            return false;
        }

        if (mSystemOnly && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            return false;
        }

        if (!applicationInfo.enabled) {
            return false;
        }

        if (applicationInfo.isInstantApp()) {
            return false;
        }

        PackageManager userPackageManager = UserUtils.getUserContext(context, user)
                .getPackageManager();
        if (!userPackageManager.getDeclaredSharedLibraries(packageName, 0).isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Grant this role to an application.
     *
     * @param packageName the package name of the application to be granted this role to
     * @param dontKillApp whether this application should not be killed despite changes
     * @param overrideUserSetAndFixedPermissions whether to override user set and fixed flags on
     *                                           permissions
     * @param context the {@code Context} to retrieve system services
     */
    public void grant(@NonNull String packageName, boolean dontKillApp,
            boolean overrideUserSetAndFixedPermissions, @NonNull Context context) {
        boolean permissionOrAppOpChanged = Permissions.grant(packageName, mPermissions, true,
                overrideUserSetAndFixedPermissions, true, false, false, context);

        int appOpsSize = mAppOps.size();
        for (int i = 0; i < appOpsSize; i++) {
            AppOp appOp = mAppOps.get(i);
            appOp.grant(packageName, context);
        }

        int preferredActivitiesSize = mPreferredActivities.size();
        for (int i = 0; i < preferredActivitiesSize; i++) {
            PreferredActivity preferredActivity = mPreferredActivities.get(i);
            preferredActivity.configure(packageName, context);
        }

        if (mBehavior != null) {
            mBehavior.grant(this, packageName, context);
        }

        if (!dontKillApp && permissionOrAppOpChanged && !Permissions.isRuntimePermissionsSupported(
                packageName, context)) {
            killApp(packageName, context);
        }
    }

    /**
     * Revoke this role from an application.
     *
     * @param packageName the package name of the application to be granted this role to
     * @param dontKillApp whether this application should not be killed despite changes
     * @param overrideSystemFixedPermissions whether system-fixed permissions can be revoked
     * @param context the {@code Context} to retrieve system services
     */
    public void revoke(@NonNull String packageName, boolean dontKillApp,
            boolean overrideSystemFixedPermissions, @NonNull Context context) {
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        List<String> otherRoleNames = roleManager.getHeldRolesFromController(packageName);
        otherRoleNames.remove(mName);

        List<String> permissionsToRevoke = new ArrayList<>(mPermissions);
        ArrayMap<String, Role> roles = Roles.get(context);
        int otherRoleNamesSize = otherRoleNames.size();
        for (int i = 0; i < otherRoleNamesSize; i++) {
            String roleName = otherRoleNames.get(i);
            Role role = roles.get(roleName);
            permissionsToRevoke.removeAll(role.getPermissions());
        }
        boolean permissionOrAppOpChanged = Permissions.revoke(packageName, permissionsToRevoke,
                true, false, overrideSystemFixedPermissions, context);

        List<AppOp> appOpsToRevoke = new ArrayList<>(mAppOps);
        for (int i = 0; i < otherRoleNamesSize; i++) {
            String roleName = otherRoleNames.get(i);
            Role role = roles.get(roleName);
            appOpsToRevoke.removeAll(role.getAppOps());
        }
        int appOpsSize = appOpsToRevoke.size();
        for (int i = 0; i < appOpsSize; i++) {
            AppOp appOp = appOpsToRevoke.get(i);
            appOp.revoke(packageName, context);
        }

        // TODO: Revoke preferred activities? But this is unnecessary for most roles using it as
        //  they have fallback holders. Moreover, clearing the preferred activity might result in
        //  other system components listening to preferred activity change get notified for the
        //  wrong thing when we are removing a exclusive role holder for adding another.

        if (mBehavior != null) {
            mBehavior.revoke(this, packageName, context);
        }

        if (!dontKillApp && permissionOrAppOpChanged) {
            killApp(packageName, context);
        }
    }

    private void killApp(@NonNull String packageName, @NonNull Context context) {
        if (DEBUG) {
            Log.i(LOG_TAG, "Killing " + packageName + " due to "
                    + Thread.currentThread().getStackTrace()[3].getMethodName()
                    + "(" + mName + ")");
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Cannot get ApplicationInfo for package: " + packageName);
            return;
        }
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        activityManager.killUid(applicationInfo.uid, "Permission or app op changed");
    }

    /**
     * Check whether the "none" role holder is selected.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether the "none" role holder is selected
     */
    private boolean isNoneHolderSelected(@NonNull Context context) {
        return Utils.getDeviceProtectedSharedPreferences(context).getBoolean(
                Constants.IS_NONE_ROLE_HOLDER_SELECTED_KEY + mName, false);
    }

    /**
     * Callback when a role holder (other than "none") was added.
     *
     * @param packageName the package name of the role holder
     * @param user the user for the role
     * @param context the {@code Context} to retrieve system services
     */
    public void onHolderAddedAsUser(@NonNull String packageName, @NonNull UserHandle user,
            @NonNull Context context) {
        Utils.getDeviceProtectedSharedPreferences(UserUtils.getUserContext(context, user)).edit()
                .remove(Constants.IS_NONE_ROLE_HOLDER_SELECTED_KEY + mName)
                .apply();
    }

    /**
     * Callback when a role holder (other than "none") was selected in the UI and added
     * successfully.
     *
     * @param packageName the package name of the role holder
     * @param user the user for the role
     * @param context the {@code Context} to retrieve system services
     */
    public void onHolderSelectedAsUser(@NonNull String packageName, @NonNull UserHandle user,
            @NonNull Context context) {
        if (mBehavior != null) {
            mBehavior.onHolderSelectedAsUser(this, packageName, user, context);
        }
    }

    /**
     * Callback when a role holder changed.
     *
     * @param user the user for the role
     * @param context the {@code Context} to retrieve system services
     */
    public void onHolderChangedAsUser(@NonNull UserHandle user,
            @NonNull Context context) {
        if (mBehavior != null) {
            mBehavior.onHolderChangedAsUser(this, user, context);
        }
    }

    /**
     * Callback when the "none" role holder was selected in the UI.
     *
     * @param user the user for the role
     * @param context the {@code Context} to retrieve system services
     */
    public void onNoneHolderSelectedAsUser(@NonNull UserHandle user, @NonNull Context context) {
        Utils.getDeviceProtectedSharedPreferences(UserUtils.getUserContext(context, user)).edit()
                .putBoolean(Constants.IS_NONE_ROLE_HOLDER_SELECTED_KEY + mName, true)
                .apply();
    }

    @Override
    public String toString() {
        return "Role{"
                + "mName='" + mName + '\''
                + ", mBehavior=" + mBehavior
                + ", mExclusive=" + mExclusive
                + ", mLabelResource=" + mLabelResource
                + ", mShowNone=" + mShowNone
                + ", mSystemOnly=" + mSystemOnly
                + ", mRequiredComponents=" + mRequiredComponents
                + ", mPermissions=" + mPermissions
                + ", mAppOps=" + mAppOps
                + ", mPreferredActivities=" + mPreferredActivities
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        Role that = (Role) object;
        return mExclusive == that.mExclusive
                && mLabelResource == that.mLabelResource
                && mShowNone == that.mShowNone
                && mSystemOnly == that.mSystemOnly
                && mName.equals(that.mName)
                && Objects.equals(mBehavior, that.mBehavior)
                && mRequiredComponents.equals(that.mRequiredComponents)
                && mPermissions.equals(that.mPermissions)
                && mAppOps.equals(that.mAppOps)
                && mPreferredActivities.equals(that.mPreferredActivities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mBehavior, mExclusive, mLabelResource, mShowNone, mSystemOnly,
                mRequiredComponents, mPermissions, mAppOps, mPreferredActivities);
    }
}
