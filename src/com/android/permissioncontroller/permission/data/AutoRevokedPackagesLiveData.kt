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
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData.Companion.NON_RUNTIME_NORMAL_PERMS
import com.android.permissioncontroller.permission.utils.KotlinUtils
import kotlinx.coroutines.Job

/**
 * Tracks which packages have been auto-revoked, and which groups have been auto revoked for those
 * packages.
 */
object AutoRevokedPackagesLiveData
    : SmartAsyncMediatorLiveData<Map<Pair<String, UserHandle>, Set<String>>>() {

    init {
        addSource(AllPackageInfosLiveData) {
            updateIfActive()
        }
    }

    private val permStateLiveDatas =
        mutableMapOf<Triple<String, String, UserHandle>, PermStateLiveData>()
    private val packagePermGroupsLiveDatas =
        mutableMapOf<Pair<String, UserHandle>, PackagePermissionsLiveData>()
    private val packageAutoRevokedPermsList =
        mutableMapOf<Pair<String, UserHandle>, MutableSet<String>>()

    override suspend fun loadDataAndPostValue(job: Job) {
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
        postValue(packageAutoRevokedPermsList.toMap())

        toAdd.forEach { packagePermGroupsLiveDatas[it] = PackagePermissionsLiveData[it] }

        toAdd.forEach { userPackage ->
            addSource(packagePermGroupsLiveDatas[userPackage]!!) {
                if (packagePermGroupsLiveDatas.all { it.value.isInitialized }) {
                    observePermStateLiveDatas()
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

        toAdd.forEach { permStateLiveDatas[it] = PermStateLiveData[it] }

        toAdd.forEach { packagePermGroup ->
            val liveData = permStateLiveDatas[packagePermGroup]!!
            addSource(liveData) { permState ->
                val packageUser = packagePermGroup.first to packagePermGroup.third
                for ((_, state) in permState) {
                    if (state.permFlags and FLAG_PERMISSION_AUTO_REVOKED != 0) {
                        packageAutoRevokedPermsList.getOrPut(packageUser) { mutableSetOf() }
                            .add(packagePermGroup.second)
                        break
                    }
                }
                if (permStateLiveDatas.all { it.value.isInitialized }) {
                    postValue(packageAutoRevokedPermsList.toMap())
                }
            }
        }
    }
}