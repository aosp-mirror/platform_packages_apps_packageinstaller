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
 * Tracks the packages associated with a uid, and the user sensitivity state of the permissions for
 * those packages.
 *
 * @param packages A LightPackageInfo for every package with this uid
 * @param permStates A map <requested permission name, use sensitive state>, with the state being a
 * combination of FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED and
 * FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
 */
data class UidSensitivityState(
    val packages: MutableSet<LightPackageInfo>,
    val permStates: MutableMap<String, Int>
)
