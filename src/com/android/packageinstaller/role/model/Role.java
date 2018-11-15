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
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.android.packageinstaller.role.utils.PackageUtils;

import java.util.ArrayList;
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

    /**
     * The name of this role. Must be unique.
     */
    @NonNull
    private final String mName;

    /**
     * The string resource for the label of this role.
     */
    @StringRes
    private final int mLabelResource;

    /**
     * Whether this role is exclusive, i.e. allows at most one holder.
     */
    private final boolean mExclusive;

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

    public Role(@NonNull String name, @StringRes int labelResource, boolean exclusive,
            @NonNull List<RequiredComponent> requiredComponents, @NonNull List<String> permissions,
            @NonNull List<AppOp> appOps, @NonNull List<PreferredActivity> preferredActivities) {
        mName = name;
        mLabelResource = labelResource;
        mExclusive = exclusive;
        mRequiredComponents = requiredComponents;
        mPermissions = permissions;
        mAppOps = appOps;
        mPreferredActivities = preferredActivities;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @StringRes
    public int getLabelResource() {
        return mLabelResource;
    }

    public boolean isExclusive() {
        return mExclusive;
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
     * Check whether a package is qualified for this role, i.e. whether it contains all the required
     * components.
     *
     * @param packageName the package name to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether the package is qualified for a role
     */
    public boolean isPackageQualified(@NonNull String packageName, @NonNull Context context) {
        int requiredComponentsSize = mRequiredComponents.size();
        for (int i = 0; i < requiredComponentsSize; i++) {
            RequiredComponent requiredComponent = mRequiredComponents.get(i);
            if (requiredComponent.getQualifyingComponentForPackage(packageName, context) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the set of packages that are qualified for this role, i.e. packages containing all the
     * required components.
     *
     * @param context the {@code Context} to retrieve system services
     *
     * @return the set of packages that are qualified for this role
     */
    @NonNull
    public List<String> getQualifyingPackages(@NonNull Context context) {
        ArrayMap<String, Integer> packageComponentCountMap = new ArrayMap<>();
        int requiredComponentsSize = mRequiredComponents.size();
        for (int requiredComponentsIndex = 0; requiredComponentsIndex < requiredComponentsSize;
                requiredComponentsIndex++) {
            RequiredComponent requiredComponent = mRequiredComponents.get(requiredComponentsIndex);

            // This returns at most one component per package.
            List<ComponentName> qualifyingComponents = requiredComponent.getQualifyingComponents(
                    context);
            int qualifyingComponentsSize = qualifyingComponents.size();
            for (int qualifyingComponentsIndex = 0;
                    qualifyingComponentsIndex < qualifyingComponentsSize;
                    ++qualifyingComponentsIndex) {
                ComponentName componentName = qualifyingComponents.get(qualifyingComponentsIndex);

                String packageName = componentName.getPackageName();
                Integer componentCount = packageComponentCountMap.get(packageName);
                packageComponentCountMap.put(packageName, componentCount == null ? 1
                        : componentCount + 1);
            }
        }

        List<String> qualifyingPackages = new ArrayList<>();
        int packageComponentCountMapSize = packageComponentCountMap.size();
        for (int i = 0; i < packageComponentCountMapSize; i++) {
            int componentCount = packageComponentCountMap.valueAt(i);
            if (componentCount != requiredComponentsSize) {
                continue;
            }
            String packageName = packageComponentCountMap.keyAt(i);
            qualifyingPackages.add(packageName);
        }

        return qualifyingPackages;
    }

    /**
     * Grant this role to an application.
     *
     * @param packageName the package name of the application to be granted this role to
     * @param mayKillApp whether this application may be killed due to changes
     * @param overrideDisabledSystemPackageAndUserSetAndFixedPermissions whether to ignore the
     *                                                                   permissions of a disabled
     *                                                                   system package (if this
     *                                                                   package is an updated
     *                                                                   system package), and
     *                                                                   whether to override user
     *                                                                   set and fixed flags on the
     *                                                                   permission
     * @param setPermissionsSystemFixed whether the permissions will be granted as system-fixed
     * @param context the {@code Context} to retrieve system services
     */
    public void grant(@NonNull String packageName, boolean mayKillApp,
            boolean overrideDisabledSystemPackageAndUserSetAndFixedPermissions,
            boolean setPermissionsSystemFixed, @NonNull Context context) {
        boolean permissionOrAppOpChanged = Permissions.grant(packageName, mPermissions,
                overrideDisabledSystemPackageAndUserSetAndFixedPermissions,
                setPermissionsSystemFixed, context);

        int appOpsSize = mAppOps.size();
        for (int i = 0; i < appOpsSize; i++) {
            AppOp appOp = mAppOps.get(i);
            permissionOrAppOpChanged |= appOp.grant(packageName, context);
        }

        int preferredActivitiesSize = mPreferredActivities.size();
        for (int i = 0; i < preferredActivitiesSize; i++) {
            PreferredActivity preferredActivity = mPreferredActivities.get(i);
            preferredActivity.configure(packageName, context);
        }

        if (mayKillApp && !Permissions.isRuntimePermissionsSupported(packageName, context)
                && permissionOrAppOpChanged) {
            killApp(packageName, context);
        }
    }

    /**
     * Revoke this role to an application.
     *
     * @param packageName the package name of the application to be granted this role to
     * @param mayKillApp whether this application may be killed due to changes
     * @param overrideSystemFixedPermissions whether system-fixed permissions can be revoked
     * @param context the {@code Context} to retrieve system services
     */
    public void revoke(@NonNull String packageName, boolean mayKillApp,
            boolean overrideSystemFixedPermissions, @NonNull Context context) {
        boolean permissionOrAppOpChanged = Permissions.revoke(packageName, mPermissions,
                overrideSystemFixedPermissions, context);

        int appOpsSize = mAppOps.size();
        for (int i = 0; i < appOpsSize; i++) {
            AppOp appOp = mAppOps.get(i);
            permissionOrAppOpChanged |= appOp.revoke(packageName, context);
        }

        // TODO: STOPSHIP: Revoke preferred activities?

        if (mayKillApp && permissionOrAppOpChanged) {
            killApp(packageName, context);
        }
    }

    private void killApp(@NonNull String packageName, @NonNull Context context) {
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(packageName, context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Cannot get ApplicationInfo for package: " + packageName);
            return;
        }
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        activityManager.killUid(applicationInfo.uid, "Permission or app op changed");
    }

    @Override
    public String toString() {
        return "Role{"
                + "mName='" + mName + '\''
                + ", mExclusive=" + mExclusive
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
        Role role = (Role) object;
        return mExclusive == role.mExclusive
                && Objects.equals(mName, role.mName)
                && Objects.equals(mRequiredComponents, role.mRequiredComponents)
                && Objects.equals(mPermissions, role.mPermissions)
                && Objects.equals(mAppOps, role.mAppOps)
                && Objects.equals(mPreferredActivities, role.mPreferredActivities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mExclusive, mRequiredComponents, mPermissions, mAppOps,
                mPreferredActivities);
    }
}
