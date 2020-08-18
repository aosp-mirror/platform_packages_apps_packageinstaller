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
import android.app.AppOpsManager.OP_FLAGS_ALL_TRUSTED
import android.app.Application
import android.os.UserHandle
import com.android.permissioncontroller.PermissionControllerApplication
import kotlinx.coroutines.Job
import java.util.function.Consumer

/**
 * LiveData that loads the last usage of each of a list of app ops for every package.
 *
 * <p>For app-ops with duration the end of the access is considered.
 *
 * @param app The current application
 * @param opNames The names of the app ops we wish to search for
 * @param usageDurationMs how much ago can an access have happened to be considered
 */
// TODO: listen for updates
class OpUsageLiveData(
    private val app: Application,
    private val opNames: List<String>,
    private val usageDurationMs: Long
) : SmartAsyncMediatorLiveData<@JvmSuppressWildcards Map<String, List<OpAccess>>>(),
        Consumer<AppOpsManager.HistoricalOps> {
    val appOpsManager = app.getSystemService(AppOpsManager::class.java)!!

    override suspend fun loadDataAndPostValue(job: Job) {
        val now = System.currentTimeMillis()
        val opMap = mutableMapOf<String, MutableList<OpAccess>>()

        val packageOps = appOpsManager.getPackagesForOps(opNames.toTypedArray())
        for (packageOp in packageOps) {
            for (opEntry in packageOp.ops) {
                val user = UserHandle.getUserHandleForUid(packageOp.uid)
                val lastAccessTime: Long = opEntry.getLastAccessTime(OP_FLAGS_ALL_TRUSTED)

                if (lastAccessTime == -1L) {
                    // There was no access, so skip
                    continue
                }

                var lastAccessDuration = opEntry.getLastDuration(OP_FLAGS_ALL_TRUSTED)

                // Some accesses have no duration
                if (lastAccessDuration == -1L) {
                    lastAccessDuration = 0
                }

                if (opEntry.isRunning ||
                        lastAccessTime + lastAccessDuration > (now - usageDurationMs)) {
                    val accessList = opMap.getOrPut(opEntry.opStr) { mutableListOf() }
                    val accessTime = if (opEntry.isRunning) {
                        -1
                    } else {
                        lastAccessTime
                    }
                    accessList.add(OpAccess(packageOp.packageName, user, accessTime))
                }
            }
        }

        postValue(opMap)
    }

    override fun accept(historicalOps: AppOpsManager.HistoricalOps) {
        val opMap = mutableMapOf<String, MutableList<OpAccess>>()
        for (i in 0 until historicalOps.uidCount) {
            val historicalUidOps = historicalOps.getUidOpsAt(i)
            val user = UserHandle.getUserHandleForUid(historicalUidOps.uid)
            for (j in 0 until historicalUidOps.packageCount) {
                val historicalPkgOps = historicalUidOps.getPackageOpsAt(j)
                val pkgName = historicalPkgOps.packageName
                for (k in 0 until historicalPkgOps.opCount) {
                    val historicalAttributedOps = historicalPkgOps.getAttributedOpsAt(k)
                    for (l in 0 until historicalAttributedOps.opCount) {
                        val historicalOp = historicalAttributedOps.getOpAt(l)
                        val opName = historicalOp.opName

                        val accessList = opMap.getOrPut(opName) { mutableListOf() }
                        accessList.add(OpAccess(pkgName, user, -1))
                    }
                }
            }
        }
    }

    override fun onActive() {
        super.onActive()
        updateAsync()
    }

    companion object : DataRepository<Pair<List<String>, Long>, OpUsageLiveData>() {
        override fun newValue(key: Pair<List<String>, Long>): OpUsageLiveData {
            return OpUsageLiveData(PermissionControllerApplication.get(), key.first, key.second)
        }

        operator fun get(ops: List<String>, usageDurationMs: Long): OpUsageLiveData {
            return get(ops to usageDurationMs)
        }
    }
}

data class OpAccess(val packageName: String?, val user: UserHandle?, val lastAccessTime: Long) {
    companion object {
        const val IS_RUNNING = -1L
    }

    fun isRunning() = lastAccessTime == IS_RUNNING
}
