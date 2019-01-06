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

/**
 * Interface for determining whether a role is available.
 */
public interface RoleAvailabilityProvider {

    /**
     * Check whether a role is available
     *
     * @param user the user to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return Whether the role is available
     */
    boolean isRoleAvailableAsUser(@NonNull UserHandle user, @NonNull Context context);
}
