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

import android.app.Application
import android.content.pm.PackageItemInfo
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import com.android.permissioncontroller.permission.utils.Utils

/**
 * A light version of a PackageItemInfo, representing information about a permission group.
 *
 * @param name The name of this group
 * @param packageName The name of the package which defines this group
 * @param labelRes The resource ID of this group's label
 * @param icon The resource ID of this group's icon
 * @param descriptionRes The resource ID of this group's desctiption
 * @param isSinglePermGroup Whether or not this is a group with a single permission in it
 */
data class LightPermGroupInfo(
    val name: String,
    val packageName: String,
    val labelRes: Int,
    val icon: Int,
    val descriptionRes: Int,
    val isSinglePermGroup: Boolean
) {

    constructor(pII: PackageItemInfo): this(pII.name, pII.packageName, pII.labelRes, pII.icon,
        0, pII is PermissionInfo)

    constructor(pGI: PermissionGroupInfo): this(pGI.name, pGI.packageName, pGI.labelRes, pGI.icon,
        pGI.descriptionRes, pGI is PermissionInfo)

    /**
     * Gets the PackageItemInfo for this permission group from the system.
     *
     * @param app The current application, which will be used to get the PackageItemInfo
     *
     * @return The PackageItemInfo corresponding to this permission group, or null, if no
     * such group exists
     */
    fun toPackageItemInfo(app: Application): PackageItemInfo? {
        return Utils.getGroupInfo(name, app.applicationContext)
    }
}