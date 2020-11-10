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

package com.android.permissioncontroller.permission.data

import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.service.getUnusedThresholdMs
import com.android.permissioncontroller.permission.utils.KotlinUtils
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Tracks which packages have been auto-revoked, and which groups have been auto revoked for those
 * packages.
 */
object AutoRevokedPackagesLiveData
    : SmartUpdateMediatorLiveData<Map<Pair<String, UserHandle>, Set<String>>>() {

    init {
        addSource(AllPackageInfosLiveData) {
            update()
        }
    }

    private val permStateLiveDatas =
        mutableMapOf<Triple<String, String, UserHandle>, PermStateLiveData>()
    private val packagePermGroupsLiveDatas =
        mutableMapOf<Pair<String, UserHandle>, PackagePermissionsLiveData>()
    private val packageAutoRevokedPermsList =
        mutableMapOf<Pair<String, UserHandle>, MutableSet<String>>()

    override fun onUpdate() {
        if (!AllPackageInfosLiveData.isInitialized) {
            return
        }

        val packageNames = mutableListOf<Pair<String, UserHandle>>()
        for ((user, packageList) in AllPackageInfosLiveData.value ?: emptyMap()) {
            packageNames.addAll(packageList.mapNotNull { pkg ->
                if (pkg.enabled) {
                    pkg.packageName to user
                } else {
                    null
                }
            })
        }

        GlobalScope.launch(Main.immediate) {
            val (toAdd, toRemove) =
                KotlinUtils.getMapAndListDifferences(packageNames, packagePermGroupsLiveDatas)

            for (pkg in toRemove) {
                val packagePermissionsLiveData = packagePermGroupsLiveDatas.remove(pkg) ?: continue
                removeSource(packagePermissionsLiveData)
                for ((groupName, _) in packagePermissionsLiveData.value ?: continue) {
                    removeSource(permStateLiveDatas.remove(Triple(pkg.first, groupName, pkg.second))
                        ?: continue)
                }
                packageAutoRevokedPermsList.remove(pkg)
            }
            if (toRemove.isNotEmpty()) {
                postCopyOfMap()
            }

            toAdd.forEach { packagePermGroupsLiveDatas[it] = PackagePermissionsLiveData[it] }

            toAdd.forEach { userPackage ->
                addSource(packagePermGroupsLiveDatas[userPackage]!!) {
                    if (packagePermGroupsLiveDatas.all { it.value.isInitialized }) {
                        observePermStateLiveDatas()
                    }
                }
            }
        }
    }

    private fun observePermStateLiveDatas() {
        val packageGroups = mutableListOf<Triple<String, String, UserHandle>>()
        packageGroups.addAll(packagePermGroupsLiveDatas.flatMap { (pkgPair, liveData) ->
            liveData.value?.keys?.toMutableSet()?.let { permGroups ->
                permGroups.remove(NON_RUNTIME_NORMAL_PERMS)
                permGroups.map { Triple(pkgPair.first, it, pkgPair.second) }
            } ?: emptyList()
        })

        GlobalScope.launch(Main.immediate) {

            val (toAdd, toRemove) =
                KotlinUtils.getMapAndListDifferences(packageGroups, permStateLiveDatas)

            for (packagePermGroup in toRemove) {
                removeSource(permStateLiveDatas.remove(packagePermGroup) ?: continue)
                val packageUser = packagePermGroup.first to packagePermGroup.third
                packageAutoRevokedPermsList[packageUser]?.remove(packagePermGroup.second)
                if (packageAutoRevokedPermsList[packageUser]?.isEmpty() == true) {
                    packageAutoRevokedPermsList.remove(packageUser)
                }
            }

            if (toRemove.isNotEmpty()) {
                postCopyOfMap()
            }

            for (packagePermGroup in toAdd) {
                permStateLiveDatas[packagePermGroup] = PermStateLiveData[packagePermGroup]
            }

            for (packagePermGroup in toAdd) {
                val permStateLiveData = permStateLiveDatas[packagePermGroup]!!
                val packageUser = packagePermGroup.first to packagePermGroup.third

                addSource(permStateLiveData) { permState ->
                    var added = false
                    if (permState == null && permStateLiveData.isInitialized) {
                        permStateLiveDatas.remove(packagePermGroup)
                        removeSource(permStateLiveData)
                    } else if (permState != null) {
                        for ((_, state) in permState) {
                            if (state.permFlags and FLAG_PERMISSION_AUTO_REVOKED != 0) {
                                packageAutoRevokedPermsList.getOrPut(packageUser) { mutableSetOf() }
                                    .add(packagePermGroup.second)
                                added = true
                                break
                            }
                        }
                    }

                    if (!added) {
                        packageAutoRevokedPermsList[packageUser]?.remove(packagePermGroup.second)
                        if (packageAutoRevokedPermsList[packageUser]?.isEmpty() == true) {
                            packageAutoRevokedPermsList.remove(packageUser)
                        }
                    }

                    if (permStateLiveDatas.all { it.value.isInitialized }) {
                        postCopyOfMap()
                    }
                }
            }
        }
    }

    private fun postCopyOfMap() {
        val autoRevokedCopy =
            mutableMapOf<Pair<String, UserHandle>, Set<String>>()
        for ((userPackage, permGroups) in packageAutoRevokedPermsList) {
            autoRevokedCopy[userPackage] = permGroups.toSet()
        }
        postValue(autoRevokedCopy)
    }
}

/**
 * Gets all Auto Revoked packages that have not been opened in a few months. This will let us remove
 * used apps from the Auto Revoke screen.
 */
object UnusedAutoRevokedPackagesLiveData
    : SmartUpdateMediatorLiveData<Map<Pair<String, UserHandle>, Set<String>>>() {
    private val unusedThreshold = getUnusedThresholdMs(PermissionControllerApplication.get())
    private val usageStatsLiveData = UsageStatsLiveData[unusedThreshold]

    init {
        addSource(usageStatsLiveData) {
            update()
        }
        addSource(AutoRevokedPackagesLiveData) {
            update()
        }
    }

    override fun onUpdate() {
        if (!usageStatsLiveData.isInitialized || !AutoRevokedPackagesLiveData.isInitialized) {
            return
        }

        val autoRevokedPackages = AutoRevokedPackagesLiveData.value!!

        val unusedPackages = mutableMapOf<Pair<String, UserHandle>, Set<String>>()
        for ((userPackage, perms) in autoRevokedPackages) {
            unusedPackages[userPackage] = perms.toSet()
        }

        val now = System.currentTimeMillis()
        for ((user, stats) in usageStatsLiveData.value!!) {
            for (stat in stats) {
                val userPackage = stat.packageName to user
                if (userPackage in autoRevokedPackages &&
                    (now - stat.lastTimeVisible) < unusedThreshold) {
                    unusedPackages.remove(userPackage)
                }
            }
        }

        value = unusedPackages
    }
}