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
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.TextUtils
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.packageinstaller.permission.ui.handheld.SettingsWithLargeHeader
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
     * Sort a given PreferenceGroup by the given comparison function.
     *
     * @param group: The group to be sorted
     * @param hasHeader: Whether the group contains a LargeHeaderPreference, which will be kept at
     * the top of the list
     * @param compare: The function comparing two preferences, which will be used to sort
     */
    fun sortPreferenceGroup(
        group: PreferenceGroup,
        hasHeader: Boolean,
        compare: (lhs: Preference, rhs: Preference) -> Int
    ) {
        val preferences = mutableListOf<Preference>()
        for (i in 0 until group.preferenceCount) {
            preferences.add(group.getPreference(i))
        }

        if (hasHeader) {
            preferences.sortWith(Comparator { lhs, rhs ->
                if (lhs is SettingsWithLargeHeader.LargeHeaderPreference) {
                    -1
                } else if (rhs is SettingsWithLargeHeader.LargeHeaderPreference) {
                    1
                } else {
                    compare(lhs, rhs)
                }
            })
        } else {
            preferences.sortWith(Comparator(compare))
        }

        for (i in 0 until preferences.size) {
            preferences[i].order = i
        }
    }

    /**
     * Gets a permission group's icon from the system.
     *
     * @param context: The context from which to get the icon
     * @param groupName: The name of the permission group whose icon we want
     *
     * @return The permission group's icon, the ic_perm_device_info icon if the group has no icon,
     * or the group does not exist
     */
    fun getPermGroupIcon(context: Context, groupName: String): Drawable? {
        val groupInfo = Utils.getGroupInfo(groupName, context)
        var icon: Drawable? = null
        if (groupInfo != null && groupInfo.icon != 0) {
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
     * @param groupName: The name of the permission group whose description we want
     *
     * @return The permission group's description, or an empty string, if the group is invalid, or
     * its description does not exist
     */
    fun getPermGroupDescription(context: Context, groupName: String): CharSequence {
        val groupInfo = Utils.getGroupInfo(groupName, context)
        var description: CharSequence = ""

        if (groupInfo is PermissionGroupInfo) {
            description = groupInfo.loadDescription(context.packageManager) ?: groupName
        } else if (groupInfo is PermissionInfo) {
            description = groupInfo.loadDescription(context.packageManager) ?: groupName
        }
        return description
    }

    /**
     * Gets a permission's label from the system.
     * @param context: The context from which to get the label
     * @param permName: The name of the permission whose label we want
     *
     * @return The permission's label, or the permission name, if the permission is invalid
     */
    fun getPermInfoLabel(context: Context, permName: String): CharSequence {
        return try {
            context.packageManager.getPermissionInfo(permName, 0).loadSafeLabel(
                context.packageManager, 20000.toFloat(), TextUtils.SAFE_STRING_FLAG_TRIM)
        } catch (e: PackageManager.NameNotFoundException) {
            permName
        }
    }

    /**
     * Gets a permission's icon from the system.
     * @param context: The context from which to get the icon
     * @param permName: The name of the permission whose icon we want
     *
     * @return The permission's icon, or the permission's group icon if the icon isn't set, or
     * the ic_perm_device_info icon if the permission is invalid.
     */
    fun getPermInfoIcon(context: Context, permName: String): Drawable? {
        return try {
            val permInfo = context.packageManager.getPermissionInfo(permName, 0)
            var icon: Drawable? = null
            if (permInfo.icon != 0) {
                icon = Utils.applyTint(context, permInfo.loadUnbadgedIcon(context.packageManager),
                    android.R.attr.colorControlNormal)
            }

            if (icon == null) {
                val groupName = Utils.getGroupOfPermission(permInfo) ?: permInfo.name
                icon = getPermGroupIcon(context, groupName)
            }

            icon
        } catch (e: PackageManager.NameNotFoundException) {
            Utils.applyTint(context, context.getDrawable(R.drawable.ic_perm_device_info),
                android.R.attr.colorControlNormal)
        }
    }

    /**
     * Gets a permission's description from the system.
     *
     * @param context: The context from which to get the description
     * @param permName: The name of the permission whose description we want
     *
     * @return The permission's description, or an empty string, if the group is invalid, or
     * its description does not exist
     */
    fun getPermInfoDescription(context: Context, permName: String): CharSequence {
        return try {
            val permInfo = context.packageManager.getPermissionInfo(permName, 0)
            permInfo.loadDescription(context.packageManager) ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
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