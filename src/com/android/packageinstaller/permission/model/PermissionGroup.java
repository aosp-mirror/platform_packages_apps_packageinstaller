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

package com.android.packageinstaller.permission.model;

import android.graphics.drawable.Drawable;

import java.util.List;

/**
 * A permission group with runtime permission as defined in an app's manifest as
 * {@code android:permission-group}.
 *
 * <p>For individual permissions that are not part of any group a {@link PermissionGroup} is created
 * dynamically with the name and icon of the individual permission.
 */
public final class PermissionGroup implements Comparable<PermissionGroup> {
    private final String mName;
    private final String mDeclaringPackage;
    private final CharSequence mLabel;
    private final Drawable mIcon;
    private final int mTotal;
    private final int mGranted;
    private final PermissionApps mPermApps;
    private final List<AppPermissionUsage> mAppPermissionUsages;

    PermissionGroup(String name, String declaringPackage, CharSequence label, Drawable icon,
            int total, int granted, PermissionApps permApps,
            List<AppPermissionUsage> appPermissionUsages) {
        mDeclaringPackage = declaringPackage;
        mName = name;
        mLabel = label;
        mIcon = icon;
        mTotal = total;
        mGranted = granted;
        mPermApps = permApps;
        mAppPermissionUsages = appPermissionUsages;
    }

    public String getName() {
        return mName;
    }

    public String getDeclaringPackage() {
        return mDeclaringPackage;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    /**
     * @return The number of apps that might request permissions of this group
     */
    public int getTotal() {
        return mTotal;
    }

    /**
     * @return The number of apps that were granted permissions of this group
     */
    public int getGranted() {
        return mGranted;
    }

    /**
     * @return The PermissionApps object for this permission group.
     */
    public PermissionApps getPermissionApps() {
        return mPermApps;
    }

    /**
     * @return The list of app permission usages of this permission group.
     */
    public List<AppPermissionUsage> getAppPermissionUsage() {
        return mAppPermissionUsages;
    }

    @Override
    public int compareTo(PermissionGroup another) {
        return mLabel.toString().compareTo(another.mLabel.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        PermissionGroup other = (PermissionGroup) obj;

        if (mName == null) {
            if (other.mName != null) {
                return false;
            }
        } else if (!mName.equals(other.mName)) {
            return false;
        }

        if (mTotal != other.mTotal) {
            return false;
        }

        if (mGranted != other.mGranted) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return mName != null ? mName.hashCode() + mTotal + mGranted : mTotal + mGranted;
    }
}
