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
 * Represents the UI information of a permission group as a whole- the number of granted non-system
 * apps, and the total number of non-system apps.
 *
 * @param name The name of the permission group whose UI data this represents
 * @param nonSystemTotal The total number of non-system applications that request permissions in
 * this group
 * @param nonSystemGranted The total number of non-system applications that request permissions in
 * this group, and have at least one permission in this group granted.
 */
data class PermGroupPackagesUiInfo(
    val name: String,
    val nonSystemTotal: Int,
    val nonSystemGranted: Int
)
