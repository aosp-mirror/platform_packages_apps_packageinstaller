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
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Interface for behavior of a role.
 */
public interface RoleBehavior {

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
     * @see Role#getConfirmationMessage(String, Context)
     */
    @Nullable
    default CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return null;
    }

    /**
     * Gets the packages that qualify to hold the role.
     *
     * @param user The user the package belong to
     * @param context A context to use
     *
     * @return the list of qualifying packages (might be empty) or {@code null} if the role
     *         description is enough to find the qualified packages
     */
    @Nullable
    default List<String> getQualifyingPackagesAsUser(@NonNull UserHandle user,
            @NonNull Context context) {
        return null;
    }

    /**
     * Check whether a package is qualified for this role, i.e. whether it contains all the required
     * components (plus meeting some other general restrictions).
     *
     * @param packageName the package name to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether the package is qualified for a role of {@code null} if the role description
     *         is enough to find the qualified packages
     */
    @Nullable
    default Boolean isPackageQualified(@NonNull String packageName, @NonNull Context context) {
        return null;
    }
}
