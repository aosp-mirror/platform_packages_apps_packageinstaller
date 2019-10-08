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

package com.android.packageinstaller.permission.model.livedatatypes

import android.graphics.drawable.Drawable
import android.os.UserHandle

const val DATA_NOT_LOADED = -1
/**
 * Represents the UI information about a permission group and the packages in it.
 *
 * @param name: The name of the permission group whose UI data this represents
 * @param packages: A map representing all packages in this group, and whether they should
 * be shown on the UI. Key is package name and user, value is the whether the package should be
 * shown, if it is a system package, and the granted state of the package permissions for this
 * particular group.
 * @param label: The label of this permission group
 * @param icon: The Icon of this permission group
 *
 * TODO ntmyren: revisit to see if icons and labels are necessary
 */
data class PermGroupPackagesUiInfo(
    val name: String,
    val packages: Map<Pair<String, UserHandle>, AppPermGroupUiInfo>?,
    val label: CharSequence,
    val icon: Drawable?
) {
    fun getNonSystemTotal(): Int {
        if (packages == null) {
            return DATA_NOT_LOADED
        }
        var shownNonSystem = 0
        for ((_, appPermGroup) in packages) {
            if (appPermGroup.shouldShow && !appPermGroup.isSystem) {
                shownNonSystem++
            }
        }
        return shownNonSystem
    }

    fun getNonSystemGranted(): Int {
        if (packages == null) {
            return DATA_NOT_LOADED
        }
        var granted = 0
        for ((_, appPermGroup) in packages) {
            if (appPermGroup.shouldShow && !appPermGroup.isSystem &&
                appPermGroup.isGranted != AppPermGroupUiInfo.PermGrantState.PERMS_DENIED) {
                granted++
            }
        }
        return granted
    }
}
