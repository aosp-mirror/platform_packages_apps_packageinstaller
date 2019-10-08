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

package com.android.packageinstaller.permission.utils

import android.app.Application
import android.content.Context
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.TextUtils
import com.android.permissioncontroller.R

/**
 * A set of util functions designed to work with kotlin, though they can work with java, as well.
 */
object KotlinUtils {

    /**
     * Given a Map, and a List, determines which elements are in the list, but not the map, and
     * vice versa. Used primarily for determining which liveDatas are already being watched, and
     * which need to be removed or added
     *
     * @param oldValues: A map of key type K, with any value type
     * @param newValues: A list of type K
     *
     * @return A pair, where the first value is all items in the list, but not the map, and the
     * second is all keys in the map, but not the list
     */
    fun <K> getMapAndListDifferences(
        newValues: List<K>,
        oldValues: Map<K, *>
    ): Pair<List<K>, List<K>> {
        val mapHas = oldValues.keys.toMutableList()
        val listHas = newValues.toMutableList()
        for (newVal in newValues) {
            if (oldValues.containsKey(newVal)) {
                mapHas.remove(newVal)
                listHas.remove(newVal)
            }
        }
        return listHas to mapHas
    }

    /**
     * Gets a permission group's icon from the system.
     *
     * @param context: The context from which to get the icon
     * @param groupName: The name of the permission group whose icon we want
     *
     * @return The permission group's icon, the ic_perm_device_info icon if the group has no icon,
     * or null if the group does not exist
     */
    fun getPermGroupIcon(context: Context, groupName: String): Drawable? {
        val groupInfo = Utils.getGroupInfo(groupName, context) ?: return null
        var icon: Drawable? = null
        if (groupInfo.icon != 0) {
            icon = Utils.loadDrawable(context.packageManager, groupInfo.packageName,
                groupInfo.icon)
        }
        if (icon == null) {
            icon = context.getDrawable(R.drawable.ic_perm_device_info)
        }
        return Utils.applyTint(context, icon, android.R.attr.colorControlNormal)
    }

    /**
     * Gets a permission group's label from the system.
     *
     * @param context: The context from which to get the label
     * @param groupName: The name of the permission group whose label we want
     *
     * @return The permission group's label, or the group name, if the group is invalid
     */
    fun getPermGroupLabel(context: Context, groupName: String): CharSequence {
        val groupInfo = Utils.getGroupInfo(groupName, context) ?: return groupName
        return groupInfo.loadSafeLabel(context.packageManager, 0f,
            TextUtils.SAFE_STRING_FLAG_FIRST_LINE or TextUtils.SAFE_STRING_FLAG_TRIM)
    }

    /**
     * Gets a permission group's description from the system.
     *
     * @param context: The context from which to get the description
     * @param groupName: The name of the permission group whose label we want
     *
     * @return The permission group's description, or the group name, if the group is invalid, or
     * its description does not exist
     */
    fun getPermGroupDescription(context: Context, groupName: String): CharSequence {
        val groupInfo = Utils.getGroupInfo(groupName, context)
        var description: CharSequence = groupName
        if (groupInfo is PermissionGroupInfo) {
            description = groupInfo.loadDescription(context.packageManager) ?: groupName
        } else if (groupInfo is PermissionInfo) {
            description = groupInfo.loadDescription(context.packageManager) ?: groupName
        }
        return description
    }

    /**
     * Gets a package's badged icon from the system.
     *
     * @param app: The current application
     * @param packageName: The name of the package whose icon we want
     * @param user: The user for whom we want the package icon
     *
     * @return The package's icon, or null, if the package does not exist
     */
    fun getBadgedPackageIcon(app: Application, packageName: String, user: UserHandle): Drawable? {
        val userContext = Utils.getUserContext(app, user)
        val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
        return Utils.getBadgedIcon(app, appInfo)
    }

    /**
     * Gets a package's badged label from the system.
     *
     * @param app: The current application
     * @param packageName: The name of the package whose label we want
     * @param user: The user for whom we want the package label
     *
     * @return The package's label
     */
    fun getPackageLabel(app: Application, packageName: String, user: UserHandle): String {
        val userContext = Utils.getUserContext(app, user)
        val appInfo = userContext.packageManager.getApplicationInfo(packageName, 0)
        return Utils.getFullAppLabel(appInfo, app)
    }
}