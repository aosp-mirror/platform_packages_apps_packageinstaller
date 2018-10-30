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

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;

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

    public AppOp(String name, int mode) {
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
     * Grant this app op to an application by setting it to the mode specified in this object.
     *
     * @param applicationInfo the {@code ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve the {@code AppOpManager}
     */
    public void grant(@NonNull ApplicationInfo applicationInfo, @NonNull Context context) {
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        appOpsManager.setUidMode(mName, applicationInfo.uid, mMode);
    }

    /**
     * Revoke this app op from an application by resetting it back to its default state.
     *
     * @param applicationInfo the {@code ApplicationInfo} of the application
     * @param context the {@code Context} to retrieve the {@code AppOpManager}
     */
    public void revoke(@NonNull ApplicationInfo applicationInfo, @NonNull Context context) {
        AppOpsManager appOpsManager = context.getSystemService(AppOpsManager.class);
        appOpsManager.resetUidMode(mName, applicationInfo.uid, true);
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
