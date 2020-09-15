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

package com.android.permissioncontroller.permission.debug

import android.content.Context
import android.icu.util.Calendar
import android.provider.DeviceConfig
import android.text.format.DateFormat.getMediumDateFormat
import android.text.format.DateFormat.getTimeFormat
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.model.AppPermissionUsage.GroupUsage
import java.util.Locale

/** Whether to show the Permissions Hub.  */
private const val PROPERTY_PERMISSIONS_HUB_2_ENABLED = "permissions_hub_2_enabled"

/** Whether to show the mic and camera icons.  */
const val PROPERTY_CAMERA_MIC_ICONS_ENABLED = "camera_mic_icons_enabled"

/**
 * Whether the Permissions Hub 2 flag is enabled
 *
 * @return whether the flag is enabled
 */
fun isPermissionsHub2FlagEnabled(): Boolean {
    return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_PERMISSIONS_HUB_2_ENABLED, false)
}
/**
 * Whether to show the Permissions Dashboard
 *
 * @return whether to show the Permissions Dashboard.
 */
fun shouldShowPermissionsDashboard(): Boolean {
    return isPermissionsHub2FlagEnabled()
}

/**
 * Whether the Camera and Mic Icons are enabled by flag.
 *
 * @return whether the Camera and Mic Icons are enabled.
 */
fun isCameraMicIconsFlagEnabled(): Boolean {
    return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
        PROPERTY_CAMERA_MIC_ICONS_ENABLED, true)
}

/**
 * Whether to show Camera and Mic Icons. They should be shown if the permission hub, or the icons
 * specifically, are enabled.
 *
 * @return whether to show the icons.
 */
fun shouldShowCameraMicIndicators(): Boolean {
    return isCameraMicIconsFlagEnabled() || isPermissionsHub2FlagEnabled()
}

/**
 * Build a string representing the given time if it happened on the current day and the date
 * otherwise.
 *
 * @param context the context.
 * @param lastAccessTime the time in milliseconds.
 *
 * @return a string representing the time or date of the given time or null if the time is 0.
 */
fun getAbsoluteTimeString(context: Context, lastAccessTime: Long): String? {
    if (lastAccessTime == 0L) {
        return null
    }
    return if (isToday(lastAccessTime)) {
        getTimeFormat(context).format(lastAccessTime)
    } else {
        getMediumDateFormat(context).format(lastAccessTime)
    }
}

/**
 * Build a string representing the time of the most recent permission usage if it happened on
 * the current day and the date otherwise.
 *
 * @param context the context.
 * @param groupUsage the permission usage.
 *
 * @return a string representing the time or date of the most recent usage or null if there are
 * no usages.
 */
fun getAbsoluteLastUsageString(context: Context, groupUsage: GroupUsage?): String? {
    return if (groupUsage == null) {
        null
    } else getAbsoluteTimeString(context, groupUsage.lastAccessTime)
}

/**
 * Build a string representing the duration of a permission usage.
 *
 * @return a string representing the duration of this app's usage or null if there are no
 * usages.
 */
fun getUsageDurationString(context: Context, groupUsage: GroupUsage?): String? {
    return if (groupUsage == null) {
        null
    } else getTimeDiffStr(context, groupUsage.accessDuration)
}

/**
 * Build a string representing the number of milliseconds passed in.  It rounds to the nearest
 * unit.  For example, given a duration of 3500 and an English locale, this can return
 * "3 seconds".
 * @param context The context.
 * @param duration The number of milliseconds.
 * @return a string representing the given number of milliseconds.
 */
fun getTimeDiffStr(context: Context, duration: Long): String {
    val seconds = Math.max(1, duration / 1000)
    if (seconds < 60) {
        return context.resources.getQuantityString(R.plurals.seconds, seconds.toInt(),
                seconds)
    }
    val minutes = seconds / 60
    if (minutes < 60) {
        return context.resources.getQuantityString(R.plurals.minutes, minutes.toInt(),
                minutes)
    }
    val hours = minutes / 60
    if (hours < 24) {
        return context.resources.getQuantityString(R.plurals.hours, hours.toInt(), hours)
    }
    val days = hours / 24
    return context.resources.getQuantityString(R.plurals.days, days.toInt(), days)
}

/**
 * Check whether the given time (in milliseconds) is in the current day.
 *
 * @param time the time in milliseconds
 *
 * @return whether the given time is in the current day.
 */
private fun isToday(time: Long): Boolean {
    val today: Calendar = Calendar.getInstance(Locale.getDefault())
    today.setTimeInMillis(System.currentTimeMillis())
    today.set(Calendar.HOUR_OF_DAY, 0)
    today.set(Calendar.MINUTE, 0)
    today.set(Calendar.SECOND, 0)
    today.set(Calendar.MILLISECOND, 0)
    val date: Calendar = Calendar.getInstance(Locale.getDefault())
    date.setTimeInMillis(time)
    return !date.before(today)
}