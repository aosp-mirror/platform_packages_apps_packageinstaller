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

package com.android.permissioncontroller.permission.ui.model

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.util.Log
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKED_APP_INTERACTION
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKED_APP_INTERACTION__ACTION__REMOVE
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_FRAGMENT_APP_VIEWED
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__NEWER_BUCKET
import com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__OLDER_BUCKET
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.UnusedAutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import com.android.permissioncontroller.permission.utils.IPC
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.DAYS

/**
 * ViewModel for the AutoRevokeFragment. Has a livedata which provides all auto revoked apps,
 * organized by how long they have been unused.
 */
class AutoRevokeViewModel(private val app: Application, private val sessionId: Long) : ViewModel() {

    companion object {
        private val SIX_MONTHS_MILLIS = DAYS.toMillis(180)
        private val LOG_TAG = AppPermissionViewModel::class.java.simpleName
    }

    enum class Months(val value: String) {
        THREE("three_months"),
        SIX("six_months");

        companion object {
            @JvmStatic
            fun allMonths(): List<Months> {
                return listOf(THREE, SIX)
            }
        }
    }

    data class RevokedPackageInfo(
        val packageName: String,
        val user: UserHandle,
        val shouldDisable: Boolean,
        val revokedGroups: Set<String>
    )

    val autoRevokedPackageCategoriesLiveData = object
        : SmartAsyncMediatorLiveData<Map<Months, List<RevokedPackageInfo>>>() {
        private val usageStatsLiveData = UsageStatsLiveData[SIX_MONTHS_MILLIS]

        init {
            addSource(UnusedAutoRevokedPackagesLiveData) {
                onUpdate()
            }

            addSource(AllPackageInfosLiveData) {
                onUpdate()
            }

            addSource(usageStatsLiveData) {
                onUpdate()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (!UnusedAutoRevokedPackagesLiveData.isInitialized ||
                !usageStatsLiveData.isInitialized || !AllPackageInfosLiveData.isInitialized) {
                return
            }

            val unusedApps = UnusedAutoRevokedPackagesLiveData.value!!
            val overSixMonthApps = unusedApps.keys.toMutableSet()
            val categorizedApps = mutableMapOf<Months, MutableList<RevokedPackageInfo>>()
            categorizedApps[Months.THREE] = mutableListOf()
            categorizedApps[Months.SIX] = mutableListOf()

            // Get all packages which should be disabled, instead of uninstalled
            val disableActionApps = mutableListOf<Pair<String, UserHandle>>()
            for ((user, packageList) in AllPackageInfosLiveData.value!!) {
                disableActionApps.addAll(packageList.mapNotNull { packageInfo ->
                    val key = packageInfo.packageName to user
                    if (unusedApps.contains(key) &&
                        (packageInfo.appFlags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                        key
                    } else {
                        null
                    }
                })
            }

            val now = System.currentTimeMillis()
            for ((user, stats) in usageStatsLiveData.value!!) {
                for (stat in stats) {
                    val statPackage = stat.packageName to user
                    if (!unusedApps.contains(statPackage)) {
                        continue
                    }

                    categorizedApps[Months.THREE]!!.add(
                        RevokedPackageInfo(stat.packageName, user,
                            disableActionApps.contains(statPackage), unusedApps[statPackage]!!))
                    overSixMonthApps.remove(statPackage)
                }
            }

            // If we didn't find the stat for a package in our six month search, it is more than
            // 6 months old, or the app has never been opened.
            overSixMonthApps.forEach { (packageName, user) ->
                var installTime: Long = 0
                for (pI in AllPackageInfosLiveData.value!![user]!!) {
                    if (pI.packageName == packageName) {
                        installTime = pI.firstInstallTime
                    }
                }

                // Check if the app was installed less than six months ago, and never opened
                val months = if (now - installTime <= SIX_MONTHS_MILLIS) {
                    Months.THREE
                } else {
                    Months.SIX
                }
                val canOpen = Utils.getUserContext(app, user).packageManager
                    .getLaunchIntentForPackage(packageName) != null
                val userPackage = packageName to user
                categorizedApps[months]!!.add(
                    RevokedPackageInfo(packageName, user, disableActionApps.contains(userPackage),
                        unusedApps[userPackage]!!))
            }

            postValue(categorizedApps)
        }
    }

    fun areAutoRevokedPackagesLoaded(): Boolean {
        return UnusedAutoRevokedPackagesLiveData.isInitialized
    }

    fun navigateToAppInfo(packageName: String, user: UserHandle, sessionId: Long) {
        val userContext = Utils.getUserContext(app, user)
        val packageUri = Uri.parse("package:$packageName")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        intent.putExtra(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, sessionId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        userContext.startActivityAsUser(intent, user)
    }

    fun requestUninstallApp(fragment: Fragment, packageName: String, user: UserHandle) {
        Log.i(LOG_TAG, "sessionId: $sessionId, Requesting uninstall of $packageName, $user")
        logAppInteraction(packageName, user, AUTO_REVOKED_APP_INTERACTION__ACTION__REMOVE)
        val packageUri = Uri.parse("package:$packageName")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        uninstallIntent.putExtra(Intent.EXTRA_USER, user)
        fragment.startActivity(uninstallIntent)
    }

    fun disableApp(packageName: String, user: UserHandle) {
        Log.i(LOG_TAG, "sessionId: $sessionId, Disabling $packageName, $user")
        logAppInteraction(packageName, user, AUTO_REVOKED_APP_INTERACTION__ACTION__REMOVE)
        val userContext = Utils.getUserContext(app, user)
        userContext.packageManager.setApplicationEnabledSetting(packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)
    }

    private fun logAppInteraction(packageName: String, user: UserHandle, action: Int) {
        GlobalScope.launch(IPC) {
            // If we are logging an app interaction, then the AllPackageInfosLiveData is not stale.
            val uid = AllPackageInfosLiveData.value?.get(user)?.find {
                info -> info.packageName == packageName }?.uid

            if (uid != null) {
                PermissionControllerStatsLog.write(AUTO_REVOKED_APP_INTERACTION, sessionId,
                    uid, packageName, action)
            }
        }
    }

    fun logAppView(packageName: String, user: UserHandle, groupName: String, isNew: Boolean) {
        GlobalScope.launch(IPC) {
            val uid = AllPackageInfosLiveData.value!![user]!!.find {
                info -> info.packageName == packageName }?.uid

            if (uid != null) {
                val bucket = if (isNew) {
                    AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__NEWER_BUCKET
                } else {
                    AUTO_REVOKE_FRAGMENT_APP_VIEWED__AGE__OLDER_BUCKET
                }
                PermissionControllerStatsLog.write(AUTO_REVOKE_FRAGMENT_APP_VIEWED, sessionId,
                    uid, packageName, groupName, bucket)
            }
        }
    }
}

class AutoRevokeViewModelFactory(
    private val app: Application,
    private val sessionId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AutoRevokeViewModel(app, sessionId) as T
    }
}