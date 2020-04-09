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
import android.os.Bundle
import android.os.UserHandle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.permissioncontroller.permission.data.AutoRevokedPackagesLiveData
import com.android.permissioncontroller.permission.utils.Utils
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AllPackageInfosLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UsageStatsLiveData
import java.util.concurrent.TimeUnit.DAYS

/**
 * ViewModel for the AutoRevokeFragment. Has a livedata which provides all auto revoked apps,
 * organized by how long they have been unused.
 */
class AutoRevokeViewModel(private val app: Application) : ViewModel() {

    companion object {
        private val SIX_MONTHS_MILLIS = DAYS.toMillis(180)
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
        : SmartUpdateMediatorLiveData<Map<Months, List<RevokedPackageInfo>>>() {
        private val usageStatsLiveData = UsageStatsLiveData[SIX_MONTHS_MILLIS]

        init {
            addSource(AutoRevokedPackagesLiveData) {
                onUpdate()
            }

            addSource(AllPackageInfosLiveData) {
                onUpdate()
            }

            addSource(usageStatsLiveData) {
                onUpdate()
            }
        }

        override fun onUpdate() {
            if (!AutoRevokedPackagesLiveData.isInitialized || !usageStatsLiveData.isInitialized ||
                !AllPackageInfosLiveData.isInitialized) {
                return
            }

            val unusedApps = AutoRevokedPackagesLiveData.value!!
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
                val userPackage = packageName to user
                categorizedApps[months]!!.add(
                    RevokedPackageInfo(packageName, user, disableActionApps.contains(userPackage),
                        unusedApps[userPackage]!!))
            }

            postValue(categorizedApps)
        }
    }

    fun areAutoRevokedPackagesLoaded(): Boolean {
        return AutoRevokedPackagesLiveData.isInitialized
    }

    fun navigateToAppPermissions(fragment: Fragment, args: Bundle) {
        fragment.findNavController().navigate(R.id.auto_revoke_to_app_perms, args)
    }

    fun openApp(packageName: String, user: UserHandle) {
        val userContext = Utils.getUserContext(app, user)
        val intent = userContext.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            userContext.startActivity(intent)
        }
    }

    fun requestUninstallApp(packageName: String, user: UserHandle) {
        val userContext = Utils.getUserContext(app, user)
        val packageUri = Uri.parse("package:$packageName")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        userContext.startActivity(uninstallIntent)
    }

    fun disableApp(packageName: String, user: UserHandle) {
        val userContext = Utils.getUserContext(app, user)
        userContext.packageManager.setApplicationEnabledSetting(packageName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)
    }
}

class AutoRevokeViewModelFactory(private val app: Application) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AutoRevokeViewModel(app) as T
    }
}