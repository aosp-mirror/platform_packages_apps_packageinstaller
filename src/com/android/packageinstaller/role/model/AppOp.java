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
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * An app op to be granted or revoke by a {@link Role}.
 */
public class AppOp {

    private static final String LOG_TAG = AppOp.class.getSimpleName();

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
     * Grant this app op to an application by setting it to a mode based on what specified in this
     * object as well as the application's granted permissions.
     *
     * @param packageName the package name of the application
     * @param overrideUser whether user's permission settings can be overridden
     * @param context the {@code Context} to retrieve system services
     *
     * @see #computeGrantedMode(String, boolean, Context) for how the mode is adjusted
     */
    public void grant(@NonNull String packageName, boolean overrideUser, @NonNull Context context) {
        Integer grantedMode = computeGrantedMode(packageName, overrideUser, context);
        if (grantedMode == null) {
            return;
        }
        Permissions.setAppOpMode(packageName, mName, grantedMode, context);
    }

    /**
     * Compute the app op mode for granting based on the application's current permission state.
     * <p>
     * The app op mode returned will respect the permission's fixed flags, and reflect the
     * background permission state if any.
     *
     * @param packageName the package name of the application
     * @param overrideUser whether user's permission settings can be overridden
     * @param context the {@code Context} to retrieve system services
     *
     * @return the adjusted app op mode for granting, or {@code null} to indicate that the app op
     *         mode should not be granted
     */
    @Nullable
    private Integer computeGrantedMode(@NonNull String packageName, boolean overrideUser,
            @NonNull Context context) {
        String permission = Permissions.getPermissionAppOp(mName);
        if (permission == null) {
            // No permission associated with this app op, just return our mode.
            return mMode;
        }

        boolean isPermissionFixed = Permissions.isPermissionFixed(packageName, permission, false,
                overrideUser, context);
        String backgroundPermission = Permissions.getBackgroundPermission(permission, context);

        if (isAppOpModePermissive(mMode)) {
            if (!Permissions.isPermissionGrantedWithoutCheckingAppOp(packageName, permission,
                    context)) {
                // The permission isn't granted, we can't set the mode to permissive.
                return null;
            }

            Integer currentMode = Permissions.getAppOpMode(packageName, mName, context);
            boolean isCurrentModePermissive = currentMode != null && isAppOpModePermissive(
                    currentMode);
            if (isPermissionFixed && !isCurrentModePermissive) {
                // The permission is fixed to a non-permissive mode, we can't set it to a permissive
                // mode.
                return null;
            }

            if (backgroundPermission == null) {
                if (isPermissionFixed) {
                    // The permission doesn't have a background permission and is fixed, we can't
                    // change anything.
                    return null;
                } else {
                    // The permission doesn't have a background permission and isn't fixed, just
                    // return our mode.
                    return mMode;
                }
            }

            if (Permissions.isRuntimePermissionsSupported(packageName, context)) {
                // Foreground permission is granted, derive the mode from whether the background
                // permission is granted.
                if (Permissions.isPermissionGrantedWithoutCheckingAppOp(packageName,
                        backgroundPermission, context)) {
                    return AppOpsManager.MODE_ALLOWED;
                } else {
                    return AppOpsManager.MODE_FOREGROUND;
                }
            } else {
                if (Permissions.isPermissionFixed(packageName, backgroundPermission, false,
                        overrideUser, context)) {
                    if (isCurrentModePermissive) {
                        // The background permission is fixed to a permissive mode, we can't change
                        // anything.
                        return null;
                    }
                    // The background permission is fixed with a non-permissive mode, and we have
                    // checked that the foreground permission is not fixed to a non-permissive mode,
                    // so set the mode to MODE_FOREGROUND as our mode is permissive.
                    return AppOpsManager.MODE_FOREGROUND;
                }
                if (currentMode != null && currentMode == AppOpsManager.MODE_ALLOWED) {
                    // The current mode is already the stronger mode, no need to change anything.
                    return null;
                }
                // Return our mode which can be stronger.
                return mMode;
            }
        } else {
            if (isPermissionFixed) {
                // The permission is fixed, we can't set the mode to another non-permissive mode.
                return null;
            }

            if (backgroundPermission == null) {
                // The permission doesn't have a background permission and isn't fixed, just return
                // our mode.
                return mMode;
            }

            if (Permissions.isPermissionFixed(packageName, backgroundPermission, false,
                    overrideUser, context)) {
                // The background permission is fixed, we can't change anything.
                return null;
            }

            // Just return our mode.
            return mMode;
        }
    }

    /**
     * Revoke this app op to an application by resetting it to a mode based on its default state as
     * well as the application's current permission state.
     *
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @see #computeRevokedMode(String, Context) for how the mode is revoked
     */
    public void revoke(@NonNull String packageName, @NonNull Context context) {
        Integer revokedMode = computeRevokedMode(packageName, context);
        if (revokedMode == null) {
            return;
        }
        Permissions.setAppOpMode(packageName, mName, revokedMode, context);
    }

    /**
     * Compute the app op mode for revoking based on the application's current permission state.
     * <p>
     * The app op mode returned will respect the permission's fixed flags, and reflect the
     * background permission state if any.
     *
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return the app op mode for revoking, or {@code null} to indicate that the app op mode should
     *         not be revoked
     */
    @Nullable
    private Integer computeRevokedMode(@NonNull String packageName, @NonNull Context context) {
        String permission = AppOpsManager.opToPermission(mName);
        if (permission == null) {
            // No permission associated with this app op, just return the default mode.
            return Permissions.getDefaultAppOpMode(mName);
        }

        String backgroundPermission = Permissions.getBackgroundPermission(permission, context);
        boolean isPermissionFixed = Permissions.isPermissionFixed(packageName, permission, false,
                false, context);
        boolean isRuntimePermissionsSupported = Permissions.isRuntimePermissionsSupported(
                packageName, context);
        if (backgroundPermission == null) {
            if (isPermissionFixed) {
                // The permission doesn't have a background permission and is fixed, we can't change
                // anything.
                return null;
            } else {
                // The permission doesn't have a background permission and isn't fixed, return the
                // default mode.
                int defaultMode = Permissions.getDefaultAppOpMode(mName);
                if (!isRuntimePermissionsSupported) {
                    // The app doesn't support runtime permissions, let the user decide whether it
                    // gets the permission if we reset it to a permissive mode.
                    addPermissionReviewRequiredFlagForPermissiveAppOpMode(packageName, permission,
                            defaultMode, context);
                }
                return defaultMode;
            }
        }

        if (isRuntimePermissionsSupported) {
            if (Permissions.isPermissionGrantedWithoutCheckingAppOp(packageName,
                    permission, context)) {
                // Foreground permission is granted, derive the mode from whether the background
                // permission is granted.
                if (Permissions.isPermissionGrantedWithoutCheckingAppOp(packageName,
                        backgroundPermission, context)) {
                    return AppOpsManager.MODE_ALLOWED;
                } else {
                    return AppOpsManager.MODE_FOREGROUND;
                }
            } else {
                if (isPermissionFixed) {
                    // Foreground permission is fixed to revoked, we can't change anything.
                    return null;
                }
                // Return the default mode.
                return Permissions.getDefaultAppOpMode(mName);
            }
        } else {
            Integer currentMode = Permissions.getAppOpMode(packageName, mName, context);
            boolean backgroundPermissionFixed = Permissions.isPermissionFixed(packageName,
                    backgroundPermission, false, false, context);
            if (backgroundPermissionFixed) {
                if (currentMode != null && currentMode == AppOpsManager.MODE_ALLOWED) {
                    // The background permission is fixed to MODE_ALLOWED, we can't change anything.
                    return null;
                }
                // The background permission is fixed with a mode other than MODE_ALLOWED, keep
                // going.
            }
            if (isPermissionFixed) {
                if (currentMode != null && currentMode == AppOpsManager.MODE_ALLOWED) {
                    // The foreground permission is fixed with MODE_ALLOWED, and since we got here
                    // the background permission is not fixed, so we set the mode to
                    // MODE_FOREGROUND.
                    return AppOpsManager.MODE_FOREGROUND;
                }
                // The foreground permission is fixed to a mode other then MODE_ALLOWED, we can't
                // change anything.
                return null;
            }
            // Return the default mode.
            int defaultMode = Permissions.getDefaultAppOpMode(mName);
            if (backgroundPermissionFixed) {
                if (defaultMode == AppOpsManager.MODE_ALLOWED) {
                    // The background permission is fixed with a mode other than MODE_ALLOWED, so
                    // don't set it to MODE_ALLOWED.
                    defaultMode = AppOpsManager.MODE_FOREGROUND;
                }
            }
            addPermissionReviewRequiredFlagForPermissiveAppOpMode(packageName, permission,
                    defaultMode, context);
            return defaultMode;
        }
    }

    /**
     * Add {@link PackageManager#FLAG_PERMISSION_REVIEW_REQUIRED} for the permission if the app op
     * mode is permissive.
     *
     * @param packageName the package name of the application
     * @param permission the name of the permission
     * @param appOpMode the mode of the app op
     * @param context the {@code Context} to retrieve system services
     *
     * @see #isAppOpModePermissive(int)
     */
    private static void addPermissionReviewRequiredFlagForPermissiveAppOpMode(
            @NonNull String packageName, @NonNull String permission, int appOpMode,
            @NonNull Context context) {
        if (isAppOpModePermissive(appOpMode)) {
            UserHandle user = UserHandle.of(UserHandle.myUserId());
            context.getPackageManager().updatePermissionFlags(permission, packageName,
                    PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED,
                    PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED, user);
        }
    }

    /**
     * Check if an app op mode is permissive, i.e. being {@code MODE_ALLOWED} or
     * {@code MODE_FOREGROUND}.
     *
     * @param mode the app op mode to check
     *
     * @return whether the app op mode is permissive
     */
    private static boolean isAppOpModePermissive(int mode) {
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND;
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
