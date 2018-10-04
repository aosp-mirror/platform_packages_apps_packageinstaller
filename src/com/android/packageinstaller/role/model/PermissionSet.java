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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * A set of permissions with a name to be granted by a {@link Role}.
 */
public class PermissionSet {

    /**
     * The name of this permission set. Must be unique.
     */
    @NonNull
    private final String mName;

    /**
     * The permissions of this permission set.
     */
    @NonNull
    private final List<String> mPermissions;

    public PermissionSet(@NonNull String name, @NonNull List<String> permissions) {
        mName = name;
        mPermissions = permissions;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public List<String> getPermissions() {
        return mPermissions;
    }

    @Override
    public String toString() {
        return "PermissionSet{"
                + "mName='" + mName + '\''
                + ", mPermissions=" + mPermissions
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
        PermissionSet that = (PermissionSet) object;
        return Objects.equals(mName, that.mName)
                && Objects.equals(mPermissions, that.mPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mPermissions);
    }
}
