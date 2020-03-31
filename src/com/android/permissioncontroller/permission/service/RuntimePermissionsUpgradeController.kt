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

package com.android.permissioncontroller.permission.service

import com.android.permissioncontroller.PermissionControllerStatsLog.RUNTIME_PERMISSIONS_UPGRADE_RESULT

import android.Manifest.permission_group
import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
import android.content.pm.PermissionInfo
import android.os.Process
import android.permission.PermissionManager
import android.util.ArrayMap
import android.util.Log

import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.UserPackageInfosLiveData
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * This class handles upgrading the runtime permissions database
 */
internal object RuntimePermissionsUpgradeController {
    private val LOG_TAG = RuntimePermissionsUpgradeController::class.java.simpleName

    // The latest version of the runtime permissions database
    private val LATEST_VERSION = 8

    fun upgradeIfNeeded(context: Context, onComplete: Runnable) {
        val permissionManager = context.getSystemService(PermissionManager::class.java)
        val currentVersion = permissionManager!!.runtimePermissionsVersion

        GlobalScope.launch(IPC) {
            whitelistAllSystemAppPermissions(context)

            val upgradedVersion = onUpgradeLocked(context, currentVersion)
            if (upgradedVersion != LATEST_VERSION) {
                Log.wtf("PermissionControllerService", "warning: upgrading permission database" +
                    " to version " + LATEST_VERSION + " left it at " + currentVersion +
                    " instead; this is probably a bug. Did you update " +
                    "LATEST_VERSION?", Throwable())
                throw RuntimeException("db upgrade error")
            }

            if (currentVersion != upgradedVersion) {
                permissionManager!!.runtimePermissionsVersion = LATEST_VERSION
            }
            onComplete.run()
        }
    }

    /**
     * Whitelist permissions of system-apps.
     *
     * Apps that are updated via OTAs are never installed. Hence their permission are never
     * whitelisted. This code replaces that by always whitelisting them.
     *
     * @param context A context to talk to the platform
     */
    private fun whitelistAllSystemAppPermissions(context: Context) {
        // Only whitelist permissions that are in the OTA. For non-OTA updates the installer should
        // do the white-listing
        val apps = context.packageManager
            .getInstalledPackages(PackageManager.GET_PERMISSIONS
                or PackageManager.MATCH_UNINSTALLED_PACKAGES
                or PackageManager.MATCH_FACTORY_ONLY)

        // Cache permissionInfos
        val permissionInfos = ArrayMap<String, PermissionInfo>()

        for (app in apps) {
            if (app.requestedPermissions == null) {
                continue
            }

            for (requestedPermission in app.requestedPermissions) {
                var permInfo = permissionInfos[requestedPermission]
                if (permInfo == null) {
                    try {
                        permInfo = context.packageManager.getPermissionInfo(
                            requestedPermission, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        continue
                    }

                    permissionInfos[requestedPermission] = permInfo
                }

                if (permInfo!!.flags and (PermissionInfo.FLAG_HARD_RESTRICTED or
                        PermissionInfo.FLAG_SOFT_RESTRICTED) == 0) {
                    continue
                }

                context.packageManager.addWhitelistedRestrictedPermission(
                    app.packageName, requestedPermission,
                    PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE)
            }
        }
    }

    /**
     * You must perform all necessary mutations to bring the runtime permissions
     * database from the old to the new version. When you add a new upgrade step
     * you *must* update LATEST_VERSION.
     *
     * <p> NOTE: Relies upon the fact that the system will attempt to upgrade every version after
     * currentVersion in order, without skipping any versions. Should this become the case, this
     * method MUST be updated.
     *
     * @param context The current context
     * @param currentVersion The current version of the permission database
     */
    private suspend fun onUpgradeLocked(
        context: Context,
        currentVersion: Int
    ): Int {
        val needBackgroundAppPermGroups = currentVersion <= 6
        val needAccessMediaAppPermGroups = currentVersion <= 7

        val packageInfos = UserPackageInfosLiveData[Process.myUserHandle()].getInitializedValue()
        if (!needAccessMediaAppPermGroups) {
            // We need neither access media permission, nor background app perm groups
            return onUpgradeLockedDataLoaded(context, currentVersion, packageInfos, emptyList(),
                emptyList())
        }

        val bgApps = mutableListOf<LightAppPermGroup>()
        val storageApps = mutableListOf<LightAppPermGroup>()
        for ((packageName, _, requestedPerms, requestedPermFlags) in packageInfos) {
            var hasAccessMedia = false
            var hasDeniedExternalStorage = false

            for ((requestedPerm, permFlags) in requestedPerms.zip(requestedPermFlags)) {
                if (needBackgroundAppPermGroups &&
                    requestedPerm == permission.ACCESS_BACKGROUND_LOCATION) {
                    LightAppPermGroupLiveData[packageName, permission_group.LOCATION,
                        Process.myUserHandle()].getInitializedValue()?.let { group ->
                        bgApps.add(group)
                    }
                }

                if (requestedPerm == permission.ACCESS_MEDIA_LOCATION) {
                    hasAccessMedia = true
                }

                if (requestedPerm == permission.READ_EXTERNAL_STORAGE &&
                    permFlags and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0) {
                    hasDeniedExternalStorage = true
                }
            }

            if (hasAccessMedia && !hasDeniedExternalStorage) {
                LightAppPermGroupLiveData[packageName, permission_group.STORAGE,
                    Process.myUserHandle()].getInitializedValue()?.let { group ->
                    storageApps.add(group)
                }
            }
        }
        return onUpgradeLockedDataLoaded(context, currentVersion,
            packageInfos, bgApps, storageApps)
    }

    private fun onUpgradeLockedDataLoaded(
        context: Context,
        currVersion: Int,
        pkgs: List<LightPackageInfo>,
        bgApps: List<LightAppPermGroup>,
        accessMediaApps: List<LightAppPermGroup>
    ): Int {
        var currentVersion = currVersion
        val app = PermissionControllerApplication.get()
        val sdkUpgradedFromP: Boolean
        if (currentVersion <= -1) {
            Log.i(LOG_TAG, "Upgrading from Android P")

            sdkUpgradedFromP = true

            currentVersion = 0
        } else {
            sdkUpgradedFromP = false
        }
        if (currentVersion == 0) {
            Log.i(LOG_TAG, "Grandfathering SMS and CallLog permissions")

            val smsPermissions = Utils.getPlatformPermissionNamesOfGroup(permission_group.SMS)
            val callLogPermissions = Utils.getPlatformPermissionNamesOfGroup(
                permission_group.CALL_LOG)

            for ((packageName, _, requestedPermissions) in pkgs) {
                for (requestedPerm in requestedPermissions) {
                    if (requestedPerm in smsPermissions || requestedPerm in callLogPermissions) {
                        context.packageManager.addWhitelistedRestrictedPermission(packageName,
                            requestedPerm, PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE)
                    }
                }
            }

            currentVersion = 1
        }

        if (currentVersion == 1) {
            // moved to step 4->5 as it has to be after the grandfathering of loc bg perms
            currentVersion = 2
        }

        if (currentVersion == 2) {
            // moved to step 5->6 to clean up broken permission state during dogfooding
            currentVersion = 3
        }

        if (currentVersion == 3) {
            Log.i(LOG_TAG, "Grandfathering location background permissions")

            for (appPermGroup in bgApps) {
                context.packageManager.addWhitelistedRestrictedPermission(appPermGroup.packageName,
                    permission.ACCESS_BACKGROUND_LOCATION, FLAG_PERMISSION_WHITELIST_UPGRADE)
            }

            currentVersion = 4
        }

        if (currentVersion == 4) {
            // moved to step 5->6 to clean up broken permission state during beta 4->5 upgrade
            currentVersion = 5
        }

        if (currentVersion == 5) {
            Log.i(LOG_TAG, "Grandfathering Storage permissions")

            val storagePermissions = Utils.getPlatformPermissionNamesOfGroup(
                permission_group.STORAGE)

            for ((packageName, _, requestedPermissions) in pkgs) {
                // We don't want to allow modification of storage post install, so put it
                // on the internal system whitelist to prevent the installer changing it.
                for (requestedPerm in requestedPermissions) {
                    if (requestedPerm in storagePermissions) {
                        context.packageManager.addWhitelistedRestrictedPermission(packageName,
                            requestedPerm, FLAG_PERMISSION_WHITELIST_UPGRADE)
                    }
                }
            }

            currentVersion = 6
        }

        if (currentVersion == 6) {
            if (sdkUpgradedFromP) {
                Log.i(LOG_TAG, "Expanding location permissions")
                for (appPermGroup in bgApps) {
                    if (appPermGroup.foreground.isGranted &&
                        appPermGroup.hasBackgroundGroup &&
                        !appPermGroup.background.isUserSet &&
                        !appPermGroup.background.isSystemFixed &&
                        !appPermGroup.background.isPolicyFixed &&
                        !appPermGroup.background.isUserFixed) {
                        val newGroup = KotlinUtils
                            .grantBackgroundRuntimePermissions(app, appPermGroup)
                        logRuntimePermissionUpgradeResult(newGroup,
                            newGroup.backgroundPermNames)
                    }
                }
            } else {
                Log.i(LOG_TAG, "Not expanding location permissions as this is not an upgrade " +
                    "from Android P")
            }

            currentVersion = 7
        }

        if (currentVersion == 7) {
            Log.i(LOG_TAG, "Expanding read storage to access media location")

            for (appPermGroup in accessMediaApps) {
                val perm = appPermGroup.permissions[permission.ACCESS_MEDIA_LOCATION] ?: continue

                if (!perm.isUserSet && !perm.isSystemFixed && !perm.isPolicyFixed &&
                    !perm.isGrantedIncludingAppOp) {
                    val newGroup = KotlinUtils
                        .grantForegroundRuntimePermissions(app, appPermGroup,
                            listOf(permission.ACCESS_MEDIA_LOCATION))

                    logRuntimePermissionUpgradeResult(newGroup,
                        newGroup.foregroundPermNames)
                }
            }

            currentVersion = 8
        }

        // XXX: Add new upgrade steps above this point.

        return currentVersion
    }

    private fun logRuntimePermissionUpgradeResult(
        permissionGroup: LightAppPermGroup,
        filterPermissions: List<String>
    ) {
        val uid = permissionGroup.packageInfo.uid
        val packageName = permissionGroup.packageName
        for (permName in filterPermissions) {
            val permission = permissionGroup.permissions[permName] ?: continue
            PermissionControllerStatsLog.write(RUNTIME_PERMISSIONS_UPGRADE_RESULT,
                permission.name, uid, packageName)
            Log.v(LOG_TAG, "Runtime permission upgrade logged for permissionName=" +
                permission.name + " uid=" + uid + " packageName=" + packageName)
        }
    }
} /* do nothing - hide constructor */
