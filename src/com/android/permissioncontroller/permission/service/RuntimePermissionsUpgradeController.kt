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

import android.Manifest.permission
import android.Manifest.permission_group
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT
import android.content.pm.PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
import android.content.pm.PermissionInfo
import android.os.Process.myUserHandle
import android.permission.PermissionManager
import android.util.Log
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.RUNTIME_PERMISSIONS_UPGRADE_RESULT
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.LightPermInfoLiveData
import com.android.permissioncontroller.permission.data.PreinstalledUserPackageInfosLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UserPackageInfosLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils.grantBackgroundRuntimePermissions
import com.android.permissioncontroller.permission.utils.KotlinUtils.grantForegroundRuntimePermissions
import com.android.permissioncontroller.permission.utils.Utils.getPlatformPermissionNamesOfGroup
import com.android.permissioncontroller.permission.utils.Utils.getRuntimePlatformPermissionNames
import com.android.permissioncontroller.permission.utils.application
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
     * Create whitelistings for select permissions of select apps.
     *
     * @param permissionInfos permissions to whitelist
     * @param pkgs packages to whitelist
     *
     * @return the whitelistings to apply
     */
    private fun getWhitelistings(
        permissions: Set<String>,
        pkgs: List<LightPackageInfo>
    ): List<Whitelisting> {
        val whitelistings = mutableListOf<Whitelisting>()

        for (pkg in pkgs) {
            for (permission in permissions intersect pkg.requestedPermissions) {
                whitelistings.add(Whitelisting(pkg.packageName, permission))
            }
        }

        return whitelistings
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
        var sdkUpgradedFromP = false
        var isNewUser = false

        if (currentVersion <= -1) {
            sdkUpgradedFromP = true
        } else if (currentVersion == 0) {
            isNewUser = true
        }

        val needBackgroundAppPermGroups = sdkUpgradedFromP && currentVersion <= 6
        val needAccessMediaAppPermGroups = !isNewUser && currentVersion <= 7

        // All data needed by this method.
        //
        // All data is loaded once and then not updated.
        val upgradeDataProvider = object : SmartUpdateMediatorLiveData<UpgradeData>() {
            /** Provides all preinstalled packages in the system */
            private val preinstalledPkgInfoProvider =
                    PreinstalledUserPackageInfosLiveData[myUserHandle()]

            /** Provides all platform runtime permission infos */
            private val platformRuntimePermissionInfoProviders =
                    mutableListOf<LightPermInfoLiveData>()

            /** {@link #platformRuntimePermissionInfoProvider} that already provided a result */
            private val platformRuntimePermissionInfoProvidersDone =
                    mutableSetOf<LightPermInfoLiveData>()

            /** Provides all packages in the system */
            private val pkgInfoProvider = UserPackageInfosLiveData[myUserHandle()]

            /** Provides all {@link LightAppPermGroup} this upgrade needs */
            private var permGroupProviders: MutableList<LightAppPermGroupLiveData>? = null

            /** {@link #permGroupProviders} that already provided a result */
            private val permGroupProvidersDone = mutableSetOf<LightAppPermGroupLiveData>()

            init {
                // First step: Load packages + perm infos

                // TODO ntmyren: remove once b/154796729 is fixed
                Log.i("RuntimePermissions", "observing UserPackageInfoLiveData for " +
                    "${myUserHandle().identifier} in RuntimePermissionsUpgradeController")
                addSource(pkgInfoProvider) { pkgInfos ->
                    if (pkgInfos != null) {
                        removeSource(pkgInfoProvider)

                        // TODO ntmyren: remove once b/154796729 is fixed
                        Log.i("RuntimePermissions", "observing " +
                            "PreinstalledUserPackageInfoLiveData for ${myUserHandle().identifier}" +
                            " in RuntimePermissionsUpgradeController")
                        addSource(preinstalledPkgInfoProvider) { preinstalledPkgInfos ->
                            if (preinstalledPkgInfos != null) {
                                removeSource(preinstalledPkgInfoProvider)

                                update()
                            }
                        }
                    }
                }

                for (platformRuntimePermission in getRuntimePlatformPermissionNames()) {
                    val permProvider = LightPermInfoLiveData[platformRuntimePermission]
                    platformRuntimePermissionInfoProviders.add(permProvider)

                    addSource(permProvider) { permInfo ->
                        if (permInfo != null) {
                            platformRuntimePermissionInfoProvidersDone.add(permProvider)
                            removeSource(permProvider)

                            update()
                        }
                    }
                }
            }

            override fun onUpdate() {
                if (permGroupProviders == null && pkgInfoProvider.value != null) {
                    // Second step: Trigger load of app-perm-groups

                    permGroupProviders = mutableListOf()

                    // Only load app-perm-groups needed for this upgrade
                    if (needBackgroundAppPermGroups || needAccessMediaAppPermGroups) {
                        for ((pkgName, _, requestedPerms, requestedPermFlags) in
                                pkgInfoProvider.value!!) {
                            var hasAccessMedia = false
                            var hasGrantedExternalStorage = false

                            for ((perm, flags) in requestedPerms.zip(requestedPermFlags)) {
                                if (needBackgroundAppPermGroups &&
                                        perm == permission.ACCESS_BACKGROUND_LOCATION) {
                                    permGroupProviders!!.add(LightAppPermGroupLiveData[pkgName,
                                            permission_group.LOCATION, myUserHandle()])
                                }

                                if (needAccessMediaAppPermGroups) {
                                    if (perm == permission.ACCESS_MEDIA_LOCATION) {
                                        hasAccessMedia = true
                                    }

                                    if (perm == permission.READ_EXTERNAL_STORAGE &&
                                            flags and PackageInfo.REQUESTED_PERMISSION_GRANTED
                                            != 0) {
                                        hasGrantedExternalStorage = true
                                    }
                                }
                            }

                            if (hasAccessMedia && hasGrantedExternalStorage) {
                                permGroupProviders!!.add(LightAppPermGroupLiveData[pkgName,
                                        permission_group.STORAGE, myUserHandle()])
                            }
                        }
                    }

                    // Wait until groups are loaded and then trigger third step
                    for (permGroupProvider in permGroupProviders!!) {
                        addSource(permGroupProvider) { group ->
                            if (group != null) {
                                permGroupProvidersDone.add(permGroupProvider)
                                removeSource(permGroupProvider)

                                update()
                            }
                        }
                    }

                    // If no group need to be loaded, directly switch to third step
                    if (permGroupProviders!!.isEmpty()) {
                        update()
                    }
                } else if (permGroupProviders != null &&
                        permGroupProvidersDone.size == permGroupProviders!!.size &&
                        preinstalledPkgInfoProvider.value != null &&
                        platformRuntimePermissionInfoProviders.size
                        == platformRuntimePermissionInfoProvidersDone.size) {
                    // Third step: All packages, perm infos and perm groups are loaded, set value

                    val bgGroups = mutableListOf<LightAppPermGroup>()
                    val storageGroups = mutableListOf<LightAppPermGroup>()

                    for (group in permGroupProviders!!.mapNotNull { it.value }) {
                        when (group.permGroupName) {
                            permission_group.LOCATION -> {
                                bgGroups.add(group)
                            }
                            permission_group.STORAGE -> {
                                storageGroups.add(group)
                            }
                        }
                    }

                    val restrictedPermissions = mutableSetOf<String>()
                    for (permInfoLiveDt in platformRuntimePermissionInfoProviders) {
                        val permInfo = permInfoLiveDt.value!!

                        if (permInfo.flags and (PermissionInfo.FLAG_HARD_RESTRICTED or
                                        PermissionInfo.FLAG_SOFT_RESTRICTED) == 0) {
                            continue
                        }

                        restrictedPermissions.add(permInfo.name)
                    }

                    value = UpgradeData(preinstalledPkgInfoProvider.value!!, restrictedPermissions,
                            pkgInfoProvider.value!!, bgGroups, storageGroups)
                }
            }
        }

        // Trigger loading of data and wait until data is loaded
        val upgradeData = upgradeDataProvider.getInitializedValue(forceUpdate = true)

        // Only whitelist permissions that are in the OTA. Apps that are updated via OTAs are never
        // installed. Hence their permission are never whitelisted. This code replaces that by
        // always whitelisting them. For non-OTA updates the installer should do the white-listing
        val preinstalledAppWhitelistings = getWhitelistings(
                upgradeData.restrictedPermissions,
                upgradeData.preinstalledPkgs)

        val (newVersion, upgradeWhitelistings, grants) = onUpgradeLockedDataLoaded(currentVersion,
                upgradeData.pkgs, upgradeData.restrictedPermissions, upgradeData.bgGroups,
                upgradeData.storageGroups)

        // Do not run in parallel. Measurements have shown that this is slower than sequential
        for (whitelisting in (preinstalledAppWhitelistings union upgradeWhitelistings)) {
            whitelisting.applyToPlatform(context)
        }

        for (grant in grants) {
            grant.applyToPlatform(context)
        }

        return newVersion
    }

    private fun onUpgradeLockedDataLoaded(
        currVersion: Int,
        pkgs: List<LightPackageInfo>,
        restrictedPermissions: Set<String>,
        bgApps: List<LightAppPermGroup>,
        accessMediaApps: List<LightAppPermGroup>
    ): Triple<Int, List<Whitelisting>, List<Grant>> {
        val whitelistings = mutableListOf<Whitelisting>()
        val grants = mutableListOf<Grant>()

        var currentVersion = currVersion
        var sdkUpgradedFromP = false
        var isNewUser = false
        val bgAppsWithWhitelisting = bgApps.map { it.packageName to it }.toMap().toMutableMap()

        if (currentVersion <= -1) {
            Log.i(LOG_TAG, "Upgrading from Android P")

            sdkUpgradedFromP = true

            currentVersion = 0
        } else {
            // If the initial version is 0 the permission state was just created
            if (currentVersion == 0) {
                isNewUser = true
            }
        }

        if (currentVersion == 0) {
            Log.i(LOG_TAG, "Grandfathering SMS and CallLog permissions")

            val permissions = restrictedPermissions intersect
                    (getPlatformPermissionNamesOfGroup(permission_group.SMS) +
                    getPlatformPermissionNamesOfGroup(permission_group.CALL_LOG))

            whitelistings.addAll(getWhitelistings(permissions, pkgs))

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

            val bgLocWhitelistings = getWhitelistings(setOf(permission.ACCESS_BACKGROUND_LOCATION),
                    pkgs)

            // Adjust bgApps as if the whitelisting was applied
            for ((pkgName, _) in bgLocWhitelistings) {
                val bgApp = bgAppsWithWhitelisting[pkgName] ?: continue
                val perm = bgApp.allPermissions[permission.ACCESS_BACKGROUND_LOCATION] ?: continue

                val allPermissionsWithWhitelisting = bgApp.allPermissions.toMutableMap()
                allPermissionsWithWhitelisting[permission.ACCESS_BACKGROUND_LOCATION] =
                        LightPermission(perm.pkgInfo, perm.permInfo, perm.isGrantedIncludingAppOp,
                        perm.flags or FLAG_PERMISSION_RESTRICTION_UPGRADE_EXEMPT,
                        perm.foregroundPerms)

                bgAppsWithWhitelisting[pkgName] = LightAppPermGroup(bgApp.packageInfo,
                        bgApp.permGroupInfo, allPermissionsWithWhitelisting,
                        bgApp.hasInstallToRuntimeSplit, bgApp.specialLocationGrant)
            }

            whitelistings.addAll(bgLocWhitelistings)

            currentVersion = 4
        }

        if (currentVersion == 4) {
            // moved to step 5->6 to clean up broken permission state during beta 4->5 upgrade
            currentVersion = 5
        }

        if (currentVersion == 5) {
            Log.i(LOG_TAG, "Grandfathering Storage permissions")

            val permissions = restrictedPermissions intersect
                    getPlatformPermissionNamesOfGroup(permission_group.STORAGE)

            // We don't want to allow modification of storage post install, so put it
            // on the internal system whitelist to prevent the installer changing it.
            whitelistings.addAll(getWhitelistings(permissions, pkgs))

            currentVersion = 6
        }

        if (currentVersion == 6) {
            if (sdkUpgradedFromP) {
                Log.i(LOG_TAG, "Expanding location permissions")
                for (appPermGroup in bgAppsWithWhitelisting.values) {
                    if (appPermGroup.foreground.isGranted &&
                        appPermGroup.hasBackgroundGroup &&
                        !appPermGroup.background.isUserSet &&
                        !appPermGroup.background.isSystemFixed &&
                        !appPermGroup.background.isPolicyFixed &&
                        !appPermGroup.background.isUserFixed) {
                        grants.add(Grant(true, appPermGroup))
                    }
                }
            } else {
                Log.i(LOG_TAG, "Not expanding location permissions as this is not an upgrade " +
                    "from Android P")
            }

            currentVersion = 7
        }

        if (currentVersion == 7) {
            if (!isNewUser) {
                Log.i(LOG_TAG, "Expanding read storage to access media location")

                for (appPermGroup in accessMediaApps) {
                    val perm = appPermGroup.permissions[permission.ACCESS_MEDIA_LOCATION]
                            ?: continue

                    if (!perm.isUserSet && !perm.isSystemFixed && !perm.isPolicyFixed &&
                            !perm.isGrantedIncludingAppOp) {
                        grants.add(Grant(false, appPermGroup,
                                listOf(permission.ACCESS_MEDIA_LOCATION)))
                    }
                }
            } else {
                Log.i(LOG_TAG, "Not expanding read storage to access media location as this is " +
                        "a new user")
            }

            currentVersion = 8
        }

        // XXX: Add new upgrade steps above this point.

        return Triple(currentVersion, whitelistings, grants)
    }

    /**
     * All data needed by {@link #onUpgradeLocked}
     */
    private data class UpgradeData(
        /** Preinstalled packages */
        val preinstalledPkgs: List<LightPackageInfo>,
        /** Restricted permissions */
        val restrictedPermissions: Set<String>,
        /** Currently installed packages */
        val pkgs: List<LightPackageInfo>,
        /**
         * Background Location groups that need to be inspected by
         * {@link #onUpgradeLockedDataLoaded}
         */
        val bgGroups: List<LightAppPermGroup>,
        /**
         * Storage groups that need to be inspected by {@link #onUpgradeLockedDataLoaded}
         */
        val storageGroups: List<LightAppPermGroup>
    )

    /**
     * A permission of an app that should be whitelisted
     */
    private data class Whitelisting(
        /** Name of package to whitelist */
        val pkgName: String,
        /** Name of permissions to whitelist */
        val permission: String
    ) {
        /**
         * Whitelist the permission by updating the platform state.
         *
         * @param context context to use when calling the platform
         */
        fun applyToPlatform(context: Context) {
            context.packageManager.addWhitelistedRestrictedPermission(pkgName, permission,
                    FLAG_PERMISSION_WHITELIST_UPGRADE)
        }
    }

    /**
     * A permission group of an app that should get granted
     */
    private data class Grant(
        /** Should the grant be for the foreground or background permissions */
        private val isBackground: Boolean,
        /** Group to be granted */
        private val group: LightAppPermGroup,
        /** Which of th permissions in the group should be granted */
        private val permissions: List<String> = group.permissions.keys.toList()
    ) {
        /**
         * Grant the permission by updating the platform state.
         *
         * @param context context to use when calling the platform
         */
        fun applyToPlatform(context: Context) {
            if (isBackground) {
                val newGroup = grantBackgroundRuntimePermissions(context.application, group,
                        permissions)

                logRuntimePermissionUpgradeResult(newGroup,
                        permissions intersect newGroup.backgroundPermNames)
            } else {
                val newGroup = grantForegroundRuntimePermissions(context.application, group,
                        permissions)

                logRuntimePermissionUpgradeResult(newGroup,
                        permissions intersect newGroup.foregroundPermNames)
            }
        }

        /**
         * Log to the platform that permissions were granted due to an update
         *
         * @param permissionGroup The group that was granted
         * @param filterPermissions Out of the group which permissions were granted
         */
        private fun logRuntimePermissionUpgradeResult(
            permissionGroup: LightAppPermGroup,
            filterPermissions: Iterable<String>
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
    }
} /* do nothing - hide constructor */
