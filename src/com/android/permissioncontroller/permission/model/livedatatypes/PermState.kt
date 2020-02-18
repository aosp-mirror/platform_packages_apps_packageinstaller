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
 * A class representing the state of one permission for one package.
 *
 * @param permFlags The system flags of the permission
 * @param granted whether or not the permission is granted
 */
data class PermState(val permFlags: Int, val granted: Boolean) {

    override fun toString(): String {
        return "granted: $granted, " +
            "user fixed: ${permFlags and PackageManager.FLAG_PERMISSION_USER_FIXED != 0} " +
            "user set: ${permFlags and PackageManager.FLAG_PERMISSION_USER_SET != 0} " +
            "one time: ${permFlags and PackageManager.FLAG_PERMISSION_ONE_TIME != 0} " +
            "review required: " +
            "${permFlags and PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED != 0} " +
            "revoked compat: ${permFlags and PackageManager.FLAG_PERMISSION_REVOKED_COMPAT != 0}"
    }
}
