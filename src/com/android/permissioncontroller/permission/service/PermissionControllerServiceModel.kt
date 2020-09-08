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

import android.content.pm.PackageManager
import android.os.Process
import android.permission.PermissionControllerManager.COUNT_ONLY_WHEN_GRANTED
import android.permission.PermissionControllerManager.COUNT_WHEN_SYSTEM
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.android.permissioncontroller.DumpableLog
import com.android.permissioncontroller.PermissionControllerProto.PermissionControllerDumpProto
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.UserPackageInfosLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.function.IntConsumer

/**
 * A model for the PermissionControllerServiceImpl. Handles the data gathering for some methods of
 * ServiceImpl, and supports retrieving data from LiveDatas.
 */
class PermissionControllerServiceModel(private val service: PermissionControllerServiceImpl) {

    private val observedLiveDatas = mutableListOf<LiveData<*>>()

    /**
     * *Must* be used instead of LiveData.observe, in order to allow the lifecycle state to
     * be set to "started" correctly. If the liveData was inactive, create a no op observer, which
     * will survive until the service goes inactive. Will remove the provided observer after one
     * update (one non-stale update, in the case of a SmartUpdateMediatorLiveData).
     *
     * @param liveData The livedata we wish to observe
     * @param onChangedFun The function we wish to be called upon livedata updates
     * @param <T> The type of the livedata and observer
     */
    fun <T> observeAndCheckForLifecycleState(
        liveData: LiveData<T>,
        onChangedFun: (t: T?) -> Unit
    ) {
        GlobalScope.launch(Main.immediate) {

            if (service.lifecycle.currentState != Lifecycle.State.STARTED) {
                service.setLifecycleToStarted()
            }

            if (!liveData.hasActiveObservers()) {
                observedLiveDatas.add(liveData)
                liveData.observe(service, Observer { })
            }

            var updated = false
            val observer = object : Observer<T> {
                override fun onChanged(data: T) {
                    if (updated) {
                        return
                    }
                    if ((liveData is SmartUpdateMediatorLiveData<T> && !liveData.isStale) ||
                        liveData !is SmartUpdateMediatorLiveData<T>) {
                        onChangedFun(data)
                        liveData.removeObserver(this)
                        updated = true
                    }
                }
            }

            if (liveData is SmartUpdateMediatorLiveData<T>) {
                liveData.observeStale(service, observer)
            } else {
                liveData.observe(service, observer)
            }
        }
    }

    /**
     * Stop observing all currently observed liveDatas
     */
    fun removeObservers() {
        GlobalScope.launch(Main.immediate) {
            for (liveData in observedLiveDatas) {
                liveData.removeObservers(service)
            }

            observedLiveDatas.clear()
        }
    }

    /**
     * Counts the number of apps that have at least one of a provided list of permissions, subject
     * to the options specified in flags. This data is gathered from a series of LiveData objects.
     *
     * @param permissionNames The list of permission names whose apps we want to count
     * @param flags Flags specifying if we want to count system apps, and count only granted apps
     * @param callback The callback our result will be returned to
     */
    fun onCountPermissionAppsLiveData(
        permissionNames: List<String>,
        flags: Int,
        callback: IntConsumer
    ) {
        val packageInfosLiveData = UserPackageInfosLiveData[Process.myUserHandle()]
        observeAndCheckForLifecycleState(packageInfosLiveData) { packageInfos ->
            onPackagesLoadedForCountPermissionApps(permissionNames, flags, callback,
                packageInfos)
        }
    }

    /**
     * Called upon receiving a list of packages which we want to filter by a list of permissions
     * and flags. Observes the AppPermGroupUiInfoLiveData for every app, and, upon receiving a
     * non-stale update, adds it to the count if it matches the permission list and flags. Will
     * only use the first non-stale update, so if an app is updated after this update, but before
     * execution is complete, the changes will not be reflected until the method is called again.
     *
     * @param permissionNames The list of permission names whose apps we want to count
     * @param flags Flags specifying if we want to count system apps, and count only granted apps
     * @param callback The callback our result will be returned to
     * @param packageInfos The list of LightPackageInfos we want to filter and count
     */
    private fun onPackagesLoadedForCountPermissionApps(
        permissionNames: List<String>,
        flags: Int,
        callback: IntConsumer,
        packageInfos: List<LightPackageInfo>?
    ) {
        if (packageInfos == null) {
            callback.accept(0)
            return
        }

        val countSystem = flags and COUNT_WHEN_SYSTEM != 0
        val countOnlyGranted = flags and COUNT_ONLY_WHEN_GRANTED != 0

        // Store the group of all installed, runtime permissions in permissionNames
        val permToGroup = mutableMapOf<String, String?>()
        for (permName in permissionNames) {
            val permInfo = try {
                service.packageManager.getPermissionInfo(permName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }

            if (Utils.isPermissionDangerousInstalledNotRemoved(permInfo)) {
                permToGroup[permName] = Utils.getGroupOfPermission(permInfo)
            }
        }

        val uiLiveDatasPerPackage = mutableListOf<MutableSet<AppPermGroupUiInfoLiveData>>()
        var numLiveDatas = 0
        for ((packageName, _, requestedPermissions) in packageInfos) {
            val packageUiLiveDatas = mutableSetOf<AppPermGroupUiInfoLiveData>()
            for (permName in permToGroup.keys) {
                if (requestedPermissions.contains(permName)) {
                    packageUiLiveDatas.add(AppPermGroupUiInfoLiveData[packageName,
                        permToGroup[permName]!!, Process.myUserHandle()])
                }
            }
            if (packageUiLiveDatas.isNotEmpty()) {
                uiLiveDatasPerPackage.add(packageUiLiveDatas)
                numLiveDatas += packageUiLiveDatas.size
            }
        }

        if (numLiveDatas == 0) {
            callback.accept(0)
        }

        var packagesWithPermission = 0
        var numPermAppsChecked = 0

        for (packageUiInfoLiveDatas in uiLiveDatasPerPackage) {
            var packageAdded = false
            // We don't need to check for new packages in between the updates of the ui info live
            // datas, because this method is used primarily for UI, and there is inherent delay
            // when calling this method, due to binder calls, so some staleness is acceptable
            for (packageUiInfoLiveData in packageUiInfoLiveDatas) {
                observeAndCheckForLifecycleState(packageUiInfoLiveData) { uiInfo ->
                    numPermAppsChecked++

                    if (uiInfo != null && uiInfo.shouldShow && (!uiInfo.isSystem || countSystem)) {
                        val granted = uiInfo.permGrantState != PermGrantState.PERMS_DENIED &&
                            uiInfo.permGrantState != PermGrantState.PERMS_ASK
                        if (granted || !countOnlyGranted && !packageAdded) {
                            // The permission might not be granted, but some permissions of the
                            // group are granted. In this case the permission is granted silently
                            // when the app asks for it.
                            // Hence this is as-good-as-granted and we count it.
                            packageAdded = true
                            packagesWithPermission++
                        }
                    }

                    if (numPermAppsChecked == numLiveDatas) {
                        callback.accept(packagesWithPermission)
                    }
                }
            }
        }
    }

    /**
     * Gets a list of the runtime permission groups which a package requests, and the UI information
     * about those groups. Will only use the first non-stale data for each group, so if an app is
     * updated after this update, but before execution is complete, the changes will not be
     * reflected until the method is called again.
     *
     * @param packageName The package whose permission information we want
     * @param callback The callback which will accept the list of <group name, group UI info> pairs
     */
    fun onGetAppPermissions(
        packageName: String,
        callback: Consumer<List<Pair<String, AppPermGroupUiInfo>>>
    ) {
        val packageGroupsLiveData = PackagePermissionsLiveData[packageName,
            Process.myUserHandle()]
        observeAndCheckForLifecycleState(packageGroupsLiveData) { groups ->
            val groupNames = groups?.keys?.toMutableList() ?: mutableListOf()
            groupNames.remove(PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS)
            val uiInfos = mutableListOf<Pair<String, AppPermGroupUiInfo>>()
            if (groupNames.isEmpty()) {
                callback.accept(uiInfos)
            }
            var numLiveDatasUpdated = 0

            for (groupName in groupNames) {
                // We don't need to check for new packages in between the updates of the ui info
                // live datas, because this method is used primarily for UI, and there is inherent
                // delay when calling this method, due to binder calls, so some staleness is
                // acceptable
                val uiInfoLiveData = AppPermGroupUiInfoLiveData[packageName, groupName,
                    Process.myUserHandle()]
                observeAndCheckForLifecycleState(uiInfoLiveData) { uiInfo ->
                    numLiveDatasUpdated++

                    uiInfo?.let {
                        if (uiInfo.shouldShow) {
                            uiInfos.add(groupName to uiInfo)
                        }
                    }

                    if (numLiveDatasUpdated == groupNames.size) {
                        callback.accept(uiInfos)
                    }
                }
            }
        }
    }

    /**
     * Dump state of the permission controller service
     *
     * @return the dump state as a proto
     */
    suspend fun onDump(): PermissionControllerDumpProto {
        // Timeout is less than the timeout used by dumping (10 s)
        return withTimeout(9000) {
            val autoRevokeDump = GlobalScope.async(IPC) { dumpAutoRevokePermissions(service) }
            val dumpedLogs = GlobalScope.async(IO) { DumpableLog.get() }

            PermissionControllerDumpProto.newBuilder()
                    .setAutoRevoke(autoRevokeDump.await())
                    .addAllLogs(dumpedLogs.await())
                    .build()
        }
    }
}
