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

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * An app op to be granted or revoke by a {@link Role}.
 */
public class AppOp {

    /**
     * The name of this app op.
     */
    @NonNull
    private final String mName;

    /**
     * The mode of this app op when granted.
     */
    private final int mMode;

    public AppOp(@NonNull String name, int mode) {
        mName = name;
        mMode = mode;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public int getMode() {
        return mMode;
    }

    /**
     * Grant this app op to an application.
     *
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app mode has changed
     */
    public boolean grant(@NonNull String packageName, @NonNull Context context) {
        return Permissions.setAppOpMode(packageName, mName, mMode, context);
    }

    /**
     * Revoke this app op from an application.
     *
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether any app mode has changed
     */
    public boolean revoke(@NonNull String packageName, @NonNull Context context) {
        int defaultMode = Permissions.getDefaultAppOpMode(mName);
        return Permissions.setAppOpMode(packageName, mName, defaultMode, context);
    }

    @Override
    public String toString() {
        return "AppOp{"
                + "mName='" + mName + '\''
                + ", mMode=" + mMode
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
        AppOp appOp = (AppOp) object;
        return mMode == appOp.mMode
                && Objects.equals(mName, appOp.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mMode);
    }
}
