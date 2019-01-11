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

/**
 * Interface for behavior of a role.
 */
public interface RoleBehavior {

    /**
     * @see Role#isAvailableAsUser(UserHandle, Context)
     */
    default boolean isAvailableAsUser(@NonNull UserHandle user, @NonNull Context context) {
        return true;
    }

    /**
     * @see Role#getConfirmationMessage(String, Context)
     */
    @Nullable
    default CharSequence getConfirmationMessage(@NonNull String packageName,
            @NonNull Context context) {
        return null;
    }
}
