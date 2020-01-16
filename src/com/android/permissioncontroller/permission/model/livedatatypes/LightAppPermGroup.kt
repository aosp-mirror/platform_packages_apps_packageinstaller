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

import android.os.Build
import android.os.UserHandle

/**
 * A lightweight version of the AppPermissionGroup data structure. Represents information about a
 * package, and all permissions in a particular permission group this package requests.
 *
 * @param packageInfo Information about the package
 * @param permGroupInfo Information about the permission group
 * @param permissions The permissions in the permission group that the package requests
 * @param hasInstallToRuntimeSplit If this group contains a permission that was previously an
 * install permission, but is currently a runtime permission
 * @param specialLocationGrant If this package is the location provider, or the extra location
 * package, then the grant state of the group is not determined by the grant state of individual
 * permissions, but by other system properties
 */
data class LightAppPermGroup(
    val packageInfo: LightPackageInfo,
    val permGroupInfo: LightPermGroupInfo,
    val permissions: Map<String, LightPermission>,
    val hasInstallToRuntimeSplit: Boolean,
    val specialLocationGrant: Boolean?
) {
    constructor(pI: LightPackageInfo, pGI: LightPermGroupInfo, perms: Map<String, LightPermission>):
        this(pI, pGI, perms, false, null)
    /**
     * The current userHandle of this AppPermGroup.
     */
    val userHandle: UserHandle = UserHandle.getUserHandleForUid(packageInfo.uid)

    /**
     * The names of all background permissions in the permission group which are requested by the
     * package.
     */
    val backgroundPermNames = permissions.mapNotNull { it.value.backgroundPermission }

    /**
     * The names of all foreground permissions in the permission group which are requested by the
     * package.
     */
    val foregroundPermNames = permissions.mapNotNull {
        if (!backgroundPermNames.contains(it.key)) it.key else null
    }

    /**
     * Whether or not this App Permission Group has a permission which has a background mode
     */
    val hasPermWithBackground = backgroundPermNames.isNotEmpty()

    /**
     * Whether or not this App Permission Group requests a background permission
     */
    val hasBackgroundPerms = backgroundPermNames.any { permissions.contains(it) }

    /**
     * Whether any of this App Permission Group's foreground permissions are fixed by policy
     */
    val isForegroundPolicyFixed = permissions.any {
        !backgroundPermNames.contains(it.key) && it.value.isPolicyFixed
    }

    /**
     * Whether any of this App Permission Group's background permissions are fixed by policy
     */
    val isBackgroundPolicyFixed = permissions.any {
        backgroundPermNames.contains(it.key) && it.value.isPolicyFixed
    }

    /**
     * Whether this App Permission Group's background and foreground permissions are fixed by policy
     */
    val isPolicyFullyFixed = isForegroundPolicyFixed && (!hasBackgroundPerms ||
        isBackgroundPolicyFixed)

    /**
     * Whether this App Permission Group's foreground permissions are fixed by the system
     */
    val isForegroundSystemFixed = permissions.any {
        !backgroundPermNames.contains(it.key) && it.value.isSystemFixed
    }

    /**
     * Whether this App Permission Group's background permissions are fixed by the system
     */
    val isBackgroundSystemFixed = permissions.any {
        backgroundPermNames.contains(it.key) && it.value.isSystemFixed
    }

    /**
     * Whether any of this App Permission Group's foreground permissions are granted
     */
    val isForegroundGranted = specialLocationGrant ?: permissions.any {
        !backgroundPermNames.contains(it.key) && it.value.isGrantedIncludingAppOp
    }

    /**
     * Whether any of this App Permission Group's background permissions are granted
     */
    val isBackgroundGranted = specialLocationGrant ?: permissions.any {
        backgroundPermNames.contains(it.key) && it.value.isGrantedIncludingAppOp
    }

    val isForegroundGrantedByDefault = permissions.any { !backgroundPermNames.contains(it.key) &&
        it.value.isGrantedByDefault
    }

    val isBackgroundGrantedByDefault = permissions.any { backgroundPermNames.contains(it.key) &&
        it.value.isGrantedByDefault
    }

    val supportsRuntimePerms = packageInfo.targetSdkVersion >= Build.VERSION_CODES.M

    /**
     * Whether this App Permission Group's permissions are fixed by the user
     */
    val isUserFixed = permissions.any { it.value.isUserFixed }

    /**
     * Whether this App Permission Group contains any one-time permission
     */
    val isOneTime = permissions.any { it.value.isOneTime }
}