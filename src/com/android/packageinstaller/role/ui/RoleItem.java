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

package com.android.packageinstaller.role.ui;

import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import com.android.packageinstaller.role.model.Role;

import java.util.List;

/**
 * Information about a role to be displayed in a list of roles.
 */
public class RoleItem {

    /**
     * The {@link Role} for this role.
     */
    @NonNull
    private final Role mRole;

    /**
     * The list of {@link ApplicationInfo} of applications holding this role.
     */
    @NonNull
    private final List<ApplicationInfo> mHolderApplicationInfos;

    public RoleItem(@NonNull Role role, @NonNull List<ApplicationInfo> holderApplicationInfos) {
        mRole = role;
        mHolderApplicationInfos = holderApplicationInfos;
    }

    @NonNull
    public Role getRole() {
        return mRole;
    }

    @NonNull
    public List<ApplicationInfo> getHolderApplicationInfos() {
        return mHolderApplicationInfos;
    }
}
