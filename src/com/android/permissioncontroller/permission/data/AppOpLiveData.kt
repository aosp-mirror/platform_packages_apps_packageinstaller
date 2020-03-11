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

import android.app.AppOpsManager
import android.app.Application
import com.android.permissioncontroller.PermissionControllerApplication

/**
 * A LiveData which represents the appop state
 *
 * @param app The current application
 * @param packageName The name of the package
 * @param op The name of the appop
 * @param uid The uid of the package
 *
 * @see AppOpsManager
 */
//TODO eugenesusla: observe appops
//TODO eugenesusla: use for external storage
class AppOpLiveData private constructor(
    private val app: Application,
    private val packageName: String,
    private val op: String,
    private val uid: Int
) : SmartUpdateMediatorLiveData<Int>() {

    val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!

    override fun onUpdate() {
        value = appOpsManager.unsafeCheckOpNoThrow(op, uid, packageName)
    }

    /**
     * Repository for AppOpLiveData.
     * <p> Key value is a triple of string package name, string appop, and
     * package uid, value is its corresponding LiveData.
     */
    companion object : DataRepository<Triple<String, String, Int>, AppOpLiveData>() {
        override fun newValue(key: Triple<String, String, Int>): AppOpLiveData {
            return AppOpLiveData(PermissionControllerApplication.get(),
                key.first, key.second, key.third)
        }
    }
}