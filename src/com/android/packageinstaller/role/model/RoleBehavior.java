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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.packageinstaller.role.ui.TwoTargetPreference;

import java.util.Collections;
import java.util.List;

/**
 * Interface for behavior of a role.
 */
public interface RoleBehavior {

    /**
     * @see Role#onRoleAdded(Context)
     */
    default void onRoleAdded(@NonNull Role role, @NonNull Context context) {}

    /**
     * @see Role#isAvailableAsUser(UserHandle, Context)
     */
    default boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return true;
    }

    /**
     * @see Role#getDefaultHolders(Context)
     */
    @NonNull
    default List<String> getDefaultHolders(@NonNull Role role, @NonNull Context context) {
        return Collections.emptyList();
    }

    /**
     * @see Role#getFallbackHolder(Context)
     */
    @Nullable
    default String getFallbackHolder(@NonNull Role role, @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#isVisibleAsUser(UserHandle, Context)
     */
    default boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return true;
    }

    /**
     * @see Role#getManageIntentAsUser(UserHandle, Context)
     */
    @Nullable
    default Intent getManageIntentAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#preparePreferenceAsUser(TwoTargetPreference, UserHandle, Context)
     */
    default void preparePreferenceAsUser(@NonNull Role role,
            @NonNull TwoTargetPreference preference, @NonNull UserHandle user,
            @NonNull Context context) {}

    /**
     * @see Role#isApplicationVisibleAsUser(ApplicationInfo, UserHandle, Context)
     */
    default boolean isApplicationVisibleAsUser(@NonNull Role role,
            @NonNull ApplicationInfo applicationInfo, @NonNull UserHandle user,
            @NonNull Context context) {
        return true;
    }

    /**
     * @see Role#prepareApplicationPreferenceAsUser(Preference, ApplicationInfo, UserHandle,
     *      Context)
     */
    default void prepareApplicationPreferenceAsUser(@NonNull Role role,
            @NonNull Preference preference, @NonNull ApplicationInfo applicationInfo,
            @NonNull UserHandle user, @NonNull Context context) {}

    /**
     * @see Role#getConfirmationMessage(String, Context)
     */
    @Nullable
    default CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#getQualifyingPackagesAsUser(UserHandle, Context)
     */
    @Nullable
    default List<String> getQualifyingPackagesAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#isPackageQualified(String, Context)
     */
    @Nullable
    default Boolean isPackageQualified(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#grant(String, boolean, boolean, boolean, Context)
     */
    default void grant(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {}

    /**
     * @see Role#revoke(String, boolean, boolean, Context)
     */
    default void revoke(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {}

    /**
     * @see Role#onHolderSelectedAsUser(String, UserHandle, Context)
     */
    default void onHolderSelectedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {}

    /**
     * @see Role#onHolderChangedAsUser(String, UserHandle, Context)
     */
    default void onHolderChangedAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {}
}
