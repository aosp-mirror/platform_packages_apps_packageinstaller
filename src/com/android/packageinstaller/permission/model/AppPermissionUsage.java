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

package com.android.packageinstaller.permission.model;

import android.app.AppOpsManager;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A single instance of an app accessing a permission.
 */
public final class AppPermissionUsage {
    private final @NonNull AppOpsManager.PackageOps mPkgOp;
    private final @NonNull AppOpsManager.OpEntry mOp;
    private final @NonNull String mPermissionGroupName;
    private final @NonNull CharSequence mPermissionGroupLabel;

    AppPermissionUsage(@NonNull AppOpsManager.PackageOps pkgOp, @NonNull AppOpsManager.OpEntry op,
            @NonNull String permissionGroupName, @NonNull CharSequence permissionGroupLabel) {
        mPkgOp = pkgOp;
        mOp = op;
        mPermissionGroupName = permissionGroupName;
        mPermissionGroupLabel = permissionGroupLabel;
    }

    public @NonNull String getPackageName() {
        return mPkgOp.getPackageName();
    }

    public int getUid() {
        return mPkgOp.getUid();
    }

    public long getTime() {
        return mOp.getLastAccessTime();
    }

    public @NonNull String getPermissionGroupName() {
        return mPermissionGroupName;
    }

    public @NonNull CharSequence getPermissionGroupLabel() {
        return mPermissionGroupLabel;
    }

    /**
     * Get the name of the permission (not the group) this represents.
     *
     * @return the name of the permission this represents.
     */
    public String getPermissionName() {
        // TODO: Replace reflection with a proper API (probably in AppOpsManager).
        try {
            Method getOpMethod = AppOpsManager.OpEntry.class.getMethod("getOp");
            Method opToPermissionMethod = AppOpsManager.class.getMethod("opToPermission",
                    int.class);
            return (String) opToPermissionMethod.invoke(null, (int) getOpMethod.invoke(mOp));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }
}
