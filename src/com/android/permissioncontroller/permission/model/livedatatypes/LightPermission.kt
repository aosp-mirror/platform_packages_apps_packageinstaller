/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.permission.model.livedatatypes

import android.content.pm.PackageManager

/**
 * Represents a single permission, and its state
 *
 * @param permInfo The permissionInfo this represents
 * @param grantedIncludingAppOp Whether or not this permission is functionally granted.
 * A non-granted app op but granted permission is counted as not granted
 * @param flags The PermissionController flags for this permission
 */
data class LightPermission(
    val permInfo: LightPermInfo,
    val grantedIncludingAppOp: Boolean,
    val flags: Int
) {

    constructor(permInfo: LightPermInfo, permState: PermState):
        this(permInfo, permState.granted, permState.permFlags)

    /** The name of this permission */
    val name = permInfo.name
    /** The background permission name of this permission, if it exists */
    val backgroundPermission: String? = permInfo.backgroundPermission
    /** Whether this permission is fixed by policy */
    val isPolicyFixed = flags and PackageManager.FLAG_PERMISSION_POLICY_FIXED != 0
    /** Whether this permission is fixed by the system */
    val isSystemFixed = flags and PackageManager.FLAG_PERMISSION_SYSTEM_FIXED != 0
    /** Whether this permission is fixed by the system */
    val isUserFixed = flags and PackageManager.FLAG_PERMISSION_USER_FIXED != 0
    /** Whether this permission is one-time */
    val isOneTime = flags and PackageManager.FLAG_PERMISSION_ONE_TIME != 0
}