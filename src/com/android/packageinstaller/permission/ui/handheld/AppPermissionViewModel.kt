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

package com.android.packageinstaller.permission.ui.handheld

import android.app.Application
import android.content.Context
import android.os.UserHandle
import android.util.Log
import androidx.lifecycle.ViewModel
import com.android.packageinstaller.PermissionControllerStatsLog
import com.android.packageinstaller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED
import com.android.packageinstaller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_VIEWED
import com.android.packageinstaller.permission.data.AppPermissionGroupRepository
import com.android.packageinstaller.permission.ui.handheld.AppPermissionFragment.ChangeTarget
import com.android.packageinstaller.permission.ui.handheld.AppPermissionFragment.CHANGE_FOREGROUND
import com.android.packageinstaller.permission.ui.handheld.AppPermissionFragment.CHANGE_BACKGROUND
import com.android.packageinstaller.permission.utils.LocationUtils
import com.android.packageinstaller.permission.utils.SafetyNetLogger
import java.util.Random

class AppPermissionViewModel(
    app: Application,
    packageName: String,
    permissionGroupName: String,
    user: UserHandle,
    private val sessionId: Long
) : ViewModel() {

    companion object {
        private val LOG_TAG = AppPermissionViewModel::class.java.simpleName
        const val REQUEST_CHANGE_TRUE = 0
        const val REQUEST_CHANGE_FALSE = 1
        const val SHOW_LOCATION_DIALOGUE = 2
        const val SHOW_DEFAULT_DENY_DIALOGUE = 3
    }

    val liveData =
        AppPermissionGroupRepository.getAppPermissionGroupLiveData(app,
            packageName, permissionGroupName, user)
    private var hasConfirmedRevoke = false

    /**
     * Request to grant/revoke permissions group.
     *
     *
     * Does <u>not</u> handle:
     *
     *  * Individually granted permissions
     *  * Permission groups with background permissions
     *
     *
     * <u>Does</u> handle:
     *
     *  * Default grant permissions
     *
     *
     * @param requestGrant If this group should be granted
     * @param changeTarget Which permission group (foreground/background/both) should be changed
     *
     * @return The dialogue to show, if applicable, or if the request was processed.
     */
    fun requestChange(
        requestGrant: Boolean,
        context: Context,
        @ChangeTarget changeTarget: Int
    ): Int {
        val group = liveData.value ?: return REQUEST_CHANGE_FALSE
        if (LocationUtils.isLocationGroupAndProvider(context, group.name,
                group.app.packageName)) {
            return SHOW_LOCATION_DIALOGUE
        }

        if (requestGrant) {
            val stateBefore = createPermissionSnapshot()!!
            if (changeTarget and CHANGE_FOREGROUND != 0) {
                val runtimePermissionsGranted = group.areRuntimePermissionsGranted()
                group.grantRuntimePermissions(false)

                if (!runtimePermissionsGranted) {
                    SafetyNetLogger.logPermissionToggled(group)
                }
            }
            if (changeTarget and CHANGE_BACKGROUND != 0 && group.backgroundPermissions != null) {
                val runtimePermissionsGranted =
                        group.backgroundPermissions.areRuntimePermissionsGranted()
                group.backgroundPermissions.grantRuntimePermissions(false)

                if (!runtimePermissionsGranted) {
                    SafetyNetLogger.logPermissionToggled(group.backgroundPermissions)
                }
            }
            logPermissionChanges(stateBefore)
        } else {
            var showDefaultDenyDialog = false

            if (changeTarget and CHANGE_FOREGROUND != 0 && group.areRuntimePermissionsGranted()) {
                showDefaultDenyDialog = (group.hasGrantedByDefaultPermission() ||
                    !group.doesSupportRuntimePermissions() ||
                    group.hasInstallToRuntimeSplit())
            }

            if (changeTarget and CHANGE_BACKGROUND != 0 &&
                group.backgroundPermissions != null &&
                group.backgroundPermissions.areRuntimePermissionsGranted()) {
                val bgPerm = group.backgroundPermissions
                showDefaultDenyDialog = showDefaultDenyDialog ||
                    bgPerm.hasGrantedByDefaultPermission() ||
                    !bgPerm.doesSupportRuntimePermissions() ||
                    bgPerm.hasInstallToRuntimeSplit()
            }

            if (showDefaultDenyDialog && !hasConfirmedRevoke) {
                return SHOW_DEFAULT_DENY_DIALOGUE
            } else {
                val stateBefore = createPermissionSnapshot()!!
                if (changeTarget and CHANGE_FOREGROUND != 0 &&
                    group.areRuntimePermissionsGranted()) {
                    group.revokeRuntimePermissions(false)

                    SafetyNetLogger.logPermissionToggled(group)
                }
                if (changeTarget and CHANGE_BACKGROUND != 0 &&
                    group.backgroundPermissions != null &&
                    group.backgroundPermissions.areRuntimePermissionsGranted()) {
                    group.backgroundPermissions.revokeRuntimePermissions(false)

                    SafetyNetLogger.logPermissionToggled(group.backgroundPermissions)
                }
                logPermissionChanges(stateBefore)
            }
        }
        return REQUEST_CHANGE_TRUE
    }

    /**
     * Once the user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @param changeTarget whether to change foreground, background, or both.
     *
     */
    fun onDenyAnyWay(@ChangeTarget changeTarget: Int) {
        val group = liveData.value ?: return
        var hasDefaultPermissions = false
        val stateBefore = createPermissionSnapshot()
        if (changeTarget and CHANGE_FOREGROUND != 0) {
            val runtimePermissionsGranted = group.areRuntimePermissionsGranted()
            group.revokeRuntimePermissions(false)

            if (runtimePermissionsGranted) {
                SafetyNetLogger.logPermissionToggled(group)
            }
            hasDefaultPermissions = group.hasGrantedByDefaultPermission()
        }
        if (changeTarget and CHANGE_BACKGROUND != 0 && group.backgroundPermissions != null) {
            val runtimePermissionsGranted =
                    group.backgroundPermissions.areRuntimePermissionsGranted()
            group.backgroundPermissions.revokeRuntimePermissions(false)

            if (runtimePermissionsGranted) {
                SafetyNetLogger.logPermissionToggled(group.backgroundPermissions)
            }
            hasDefaultPermissions = hasDefaultPermissions ||
                group.backgroundPermissions.hasGrantedByDefaultPermission()
        }
        logPermissionChanges(stateBefore!!)

        if (hasDefaultPermissions || !group.doesSupportRuntimePermissions()) {
            hasConfirmedRevoke = true
        }
    }

    private fun createPermissionSnapshot(): List<PermissionState>? {
        val group = liveData.value ?: return null
        val permissionSnapshot = ArrayList<PermissionState>()

        for (permission in group.permissions) {
            permissionSnapshot.add(PermissionState(permission.name,
                permission.isGrantedIncludingAppOp))
        }

        val permissionGroup = group.backgroundPermissions ?: return permissionSnapshot

        for (permission in permissionGroup.permissions) {
            permissionSnapshot.add(PermissionState(permission.name,
                permission.isGrantedIncludingAppOp))
        }

        return permissionSnapshot
    }

    private fun logPermissionChanges(previousPermissionSnapshot: List<PermissionState>) {
        val group = liveData.value ?: return

        val changeId = Random().nextLong()

        for ((permissionName, wasGranted) in previousPermissionSnapshot) {
            val permission = group.getPermission(permissionName)
                ?: group.backgroundPermissions?.getPermission(permissionName)
                ?: continue

            val isGranted = permission.isGrantedIncludingAppOp

            if (wasGranted != isGranted) {
                logAppPermissionFragmentActionReported(changeId, permissionName, isGranted)
            }
        }
    }

    private fun logAppPermissionFragmentActionReported(
        changeId: Long,
        permissionName: String,
        isGranted: Boolean
    ) {

        val group = liveData.value ?: return
        PermissionControllerStatsLog.write(APP_PERMISSION_FRAGMENT_ACTION_REPORTED, sessionId,
            changeId, group.getApp().applicationInfo.uid, group.getApp().packageName,
            permissionName, isGranted)
        Log.v(LOG_TAG, "Permission changed via UI with sessionId=$sessionId changeId=" +
            "$changeId uid=${group.app.applicationInfo.uid} packageName=" +
            "${group.app.packageName} permission=$permissionName isGranted=$isGranted")
    }

    /**
     * Logs information about this AppPermissionGroup and view session
     */
    fun logAppPermissionFragmentViewed() {
        val group = liveData.value ?: return
        PermissionControllerStatsLog.write(APP_PERMISSION_FRAGMENT_VIEWED, sessionId,
            group.app.applicationInfo.uid, group.app.packageName, group.name)
        Log.v(LOG_TAG, "AppPermission fragment viewed with sessionId=$sessionId uid=" +
            "${group.app.applicationInfo.uid} packageName=${group.app.packageName}" +
            "permissionGroupName=${group.name}")
    }

    data class PermissionState(val permissionName: String, val permissionGranted: Boolean)
}