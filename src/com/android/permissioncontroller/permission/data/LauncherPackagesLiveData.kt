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

import android.content.Intent
import android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE
import android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import kotlinx.coroutines.Job

/**
 * A livedata which stores a list of package names of packages which have launcher icons.
 */
object LauncherPackagesLiveData : SmartAsyncMediatorLiveData<Set<String>>(),
    PackageBroadcastReceiver.PackageBroadcastListener {

    private val LAUNCHER_INTENT = Intent(Intent.ACTION_MAIN, null)
        .addCategory(Intent.CATEGORY_LAUNCHER)

    override suspend fun loadDataAndPostValue(job: Job) {
        // TODO ntmyren: remove once b/154796729 is fixed
        Log.i("LancherPackagesLiveData", "updating LauncherPackageLiveData")
        val launcherPkgs = mutableSetOf<String>()
        for (info in PermissionControllerApplication.get().packageManager.queryIntentActivities(
            LAUNCHER_INTENT, MATCH_DIRECT_BOOT_AWARE or MATCH_DIRECT_BOOT_UNAWARE)) {
            launcherPkgs.add(info.activityInfo.packageName)
        }

        postValue(launcherPkgs)
    }

    override fun onPackageUpdate(packageName: String) {
        update()
    }

    override fun onActive() {
        super.onActive()
        update()
        PackageBroadcastReceiver.addAllCallback(this)
    }

    override fun onInactive() {
        super.onInactive()
        PackageBroadcastReceiver.removeAllCallback(this)
    }
}