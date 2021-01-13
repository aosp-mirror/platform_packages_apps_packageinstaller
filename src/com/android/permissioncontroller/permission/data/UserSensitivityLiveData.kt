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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.Process.INVALID_UID
import android.os.UserHandle

import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.model.livedatatypes.UidSensitivityState
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Job
import java.lang.IllegalArgumentException

/**
 * Live data of the user sensitivity of either one uid, or all uids that belong to a user.
 * Maps <uid, user sensitive state>
 *
 * @param app The current application
 * @param uid The uid whose user sensitivity we would like to observer, or INVALID_UID if we want
 * all uids for a user
 * @param user The user for whom we want the uid/s
 */
class UserSensitivityLiveData private constructor(
    private val app: Application,
    private val uid: Int,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<Map<Int, UidSensitivityState>?>() {

    private val context: Context
    private val packageLiveDatas = mutableMapOf<String, LightPackageInfoLiveData>()
    private val userPackageInfosLiveData = UserPackageInfosLiveData[user]
    private val getAllUids = uid == INVALID_UID

    init {
        try {
            context = Utils.getUserContext(app, user)
        } catch (cannotHappen: PackageManager.NameNotFoundException) {
            throw IllegalStateException(cannotHappen)
        }

        if (getAllUids) {
            addSource(userPackageInfosLiveData) {
                update()
            }
            addSource(LauncherPackagesLiveData) {
                update()
            }
        } else {
            update()
        }
    }

    override suspend fun loadDataAndPostValue(job: Job) {
        val pm = context.packageManager
        if (!getAllUids) {
            val uidHasPackages = getAndObservePackageLiveDatas()

            if (!uidHasPackages || packageLiveDatas.all {
                    it.value.isInitialized &&
                        it.value.value == null
                }) {
                packageLiveDatas.clear()
                invalidateSingle(uid to user)
                postValue(null)
                return
            } else if (!packageLiveDatas.all { it.value.isInitialized }) {
                return
            }
        }
        val pkgs = if (getAllUids) {
            userPackageInfosLiveData.value ?: return
        } else {
            packageLiveDatas.mapNotNull { it.value.value }
        }
        if (job.isCancelled) {
            return
        }

        // map of <uid, userSensitiveState>
        val sensitiveStatePerUid = mutableMapOf<Int, UidSensitivityState>()

        // TODO ntmyren: Figure out how to get custom runtime permissions in a less costly manner
        val runtimePerms = Utils.getRuntimePlatformPermissionNames()

        for (pkg in pkgs) {
            // sensitivityState for one uid
            val userSensitiveState = sensitiveStatePerUid.getOrPut(pkg.uid) {
                UidSensitivityState(mutableSetOf(), mutableMapOf())
            }
            userSensitiveState.packages.add(pkg)

            val pkgHasLauncherIcon = if (getAllUids) {
                // The launcher packages set will only be null when it is uninitialized.
                LauncherPackagesLiveData.value?.contains(pkg.packageName) ?: return
            } else {
                KotlinUtils.packageHasLaunchIntent(context, pkg.packageName)
            }
            val pkgIsSystemApp = pkg.appFlags and ApplicationInfo.FLAG_SYSTEM != 0
            // Iterate through all runtime perms, setting their keys
            for (perm in pkg.requestedPermissions.intersect(runtimePerms)) {
                /*
                 * Permissions are considered user sensitive for a package, when
                 * - the package has a launcher icon, or
                 * - the permission is not pre-granted, or
                 * - the package is not a system app (i.e. not preinstalled)
                 */
                var flags = if (pkgIsSystemApp && !pkgHasLauncherIcon) {
                    val permGrantedByDefault = pm.getPermissionFlags(perm, pkg.packageName,
                        user) and PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT != 0

                    if (permGrantedByDefault) {
                        0
                    } else {
                        PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
                    }
                } else {
                    Utils.FLAGS_ALWAYS_USER_SENSITIVE
                }

                /*
                 * If two packages share a UID there can be two cases:
                 * - for well known UIDs: if the permission for any package is non-user sensitive,
                 *                        it is non-sensitive. I.e. prefer to hide
                 * - for non system UIDs: if the permission for any package is user sensitive, it is
                 *                        user sensitive. I.e. prefer to show
                 */
                val previousFlags = userSensitiveState.permStates[perm]
                if (previousFlags != null) {
                    flags = if (pkg.uid < Process.FIRST_APPLICATION_UID) {
                        flags and previousFlags
                    } else {
                        flags or previousFlags
                    }
                }

                userSensitiveState.permStates[perm] = flags
            }

            if (job.isCancelled) {
                return
            }
        }
        postValue(sensitiveStatePerUid)
    }

    private fun getAndObservePackageLiveDatas(): Boolean {
        val packageNames = app.packageManager.getPackagesForUid(uid)?.toList() ?: emptyList()
        val getLiveData = { packageName: String -> LightPackageInfoLiveData[packageName, user] }
        setSourcesToDifference(packageNames, packageLiveDatas, getLiveData)
        return packageNames.isNotEmpty()
    }

    /**
     * Repository for a UserSensitivityLiveData
     * <p> Key value is a pair of int uid (INVALID_UID for all uids), and UserHandle,
     * value is its corresponding LiveData.
     */
    companion object : DataRepository<Pair<Int, UserHandle>, UserSensitivityLiveData>() {
        override fun newValue(key: Pair<Int, UserHandle>): UserSensitivityLiveData {
            return UserSensitivityLiveData(PermissionControllerApplication.get(), key.first,
                key.second)
        }

        /**
         * Gets a liveData for a uid, automatically generating the UserHandle from the uid. Will
         * throw an exception if the uid is INVALID_UID.
         *
         * @param uid The uid for which we want the liveData
         *
         * @return The liveData associated with the given UID
         */
        operator fun get(uid: Int): UserSensitivityLiveData {
            if (uid == INVALID_UID) {
                throw IllegalArgumentException("Cannot get single uid livedata without a valid uid")
            }
            return get(uid, UserHandle.getUserHandleForUid(uid))
        }

        /**
         * Gets a liveData for a user, which will track all uids under
         *
         * @param user The user for whom we want the liveData
         *
         * @return The liveData associated with that user, for all uids
         */
        operator fun get(user: UserHandle): UserSensitivityLiveData {
            return get(INVALID_UID, user)
        }
    }
}
