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

/**
 * A triple of booleans that correspond to an App Permission Group, and give information about how
 * the UI should treat the App Permission Group
 *
 * @param shouldShow Whether or not this app perm group should be shown in the UI
 * @param permGrantState Whether this app perm group has granted permissions
 * @param isSystem Whether or not this app is a system app, which should be hidden by default
 */
data class AppPermGroupUiInfo(
    val shouldShow: Boolean,
    val permGrantState: PermGrantState,
    val isSystem: Boolean
) {
    enum class PermGrantState(private val grantState: Int) {
        PERMS_DENIED(0),
        PERMS_ALLOWED(1),
        PERMS_ALLOWED_FOREGROUND_ONLY(2),
        PERMS_ALLOWED_ALWAYS(3),
        PERMS_ASK(4)
    }
}
