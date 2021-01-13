/*
 * Copyright (C) 2020 The Android Open Source Project
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

/**
 * Tracks the state of auto revoke for a package
 *
 * @param isEnabledGlobal Whether or not the Auto Revoke feature is enabled globally
 * @param isEnabledForApp Whether or not the OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED is set to
 * MODE_ALLOWED for this package
 * @param revocableGroupNames A list of which permission groups of this package are eligible for
 * auto-revoke. A permission group is auto-revocable if it does not contain a default granted
 * permission.
 */
class AutoRevokeState(
    val isEnabledGlobal: Boolean,
    val isEnabledForApp: Boolean,
    val revocableGroupNames: List<String>
) {

    /**
     * If the auto revoke switch should be provided for the user to control.
     */
    val shouldAllowUserToggle = revocableGroupNames.isNotEmpty()
}
