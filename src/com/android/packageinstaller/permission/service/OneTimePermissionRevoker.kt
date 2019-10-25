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

package com.android.packageinstaller.permission.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.annotation.GuardedBy
import com.android.packageinstaller.Constants
import com.android.packageinstaller.permission.utils.Utils

/**
 * Singleton class responsible for revoking one-time permissions
 */
class OneTimePermissionRevoker private constructor(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val packageManager = context.packageManager
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    @GuardedBy("lock")
    private val packagePermissionsToRevoke = mutableMapOf<String, Pair<Long, MutableSet<String>>>()
    private val lock = Any()

    init {
        readOneTimePermissions()
        scheduleAllRevokes()
    }

    /**
     * Adds a package-permission to be revoked on
     * Settings.Secure.ONE_TIME_PERMISSIONS_TIMEOUT_MILLIS milliseconds of inactivity of the package
     *
     * @param packageName the package
     * @param permission the permission
     */
    fun addPackagePermission(packageName: String, permission: String) {
        val revokeTime = Utils.getOneTimePermissionsTimeout() + System.currentTimeMillis()

        synchronized(lock) {
            val revokeTimeAndPermissions = packagePermissionsToRevoke
                    .getOrPut(packageName) { Pair(revokeTime, mutableSetOf()) }
            revokeTimeAndPermissions.second.add(permission)

            schedulePackageRevokeLocked(packageName, revokeTime)
        }
    }

    private fun readOneTimePermissions() {
        val sharedPreferences = context.getSharedPreferences(
                Constants.ONE_TIME_NEED_TO_REVOKE_FILE_NAME, Context.MODE_PRIVATE)

        val persistedPackagePermissions = mutableMapOf<String, Pair<Long, MutableSet<String>>>()
        val packages = sharedPreferences.getStringSet("packages", null) ?: return
        for (packageName in packages) {
            val permissions = mutableSetOf<String>()
            val currentPermissions = sharedPreferences
                    .getStringSet(getPackageKey(packageName, "permissions"), emptySet())!!
            permissions.addAll(currentPermissions)
            if (permissions.size == 0) {
                continue
            }
            val revokeTime = sharedPreferences.getLong(
                    getPackageKey(packageName, "revokeTime"), 0)
            val pair = Pair(revokeTime, permissions)
            persistedPackagePermissions[packageName] = pair
        }

        synchronized(lock) {
            for (entry in persistedPackagePermissions) {
                val packageName = entry.key
                val currentPair = packagePermissionsToRevoke[packageName]
                val currentPermissions = currentPair?.second

                val newPermissions = entry.value.second
                newPermissions.addAll(currentPermissions ?: emptySet())
                val newPair = Pair(currentPair?.first ?: entry.value.first, newPermissions)
                packagePermissionsToRevoke[packageName] = newPair
            }
        }
    }

    private fun writeOneTimePermissionsLocked() {
        val sharedPreferences = context.getSharedPreferences(
                Constants.ONE_TIME_NEED_TO_REVOKE_FILE_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("packages", packagePermissionsToRevoke.keys)

        for (entry in packagePermissionsToRevoke) {
            val packageName = entry.key
            editor.putStringSet(getPackageKey(packageName, "permissions"),
                    entry.value.second)
            editor.putLong(getPackageKey(packageName, "revokeTime"),
                    entry.value.first)
        }
        editor.apply()
    }

    private fun getPackageKey(packageName: String, key: String): String {
        return "$packageName:$key"
    }

    private fun scheduleAllRevokes() {
        synchronized(lock) {
            for (entry in packagePermissionsToRevoke) {
                val packageName = entry.key
                val revokeTime = entry.value.first
                schedulePackageRevokeLocked(packageName, revokeTime)
            }
        }
    }

    private fun getIntentForRevokingPackagePermissions(packageName: String): Intent {
        val intent = Intent(context, RevokePackageReceiver::class.java)
        intent.data = Uri.Builder().path(packageName).build()
        return intent
    }

    private fun schedulePackageRevokeLocked(packageName: String, revokeTime: Long) {
        Log.v(LOG_TAG, "Scheduling to revoke one-time permissions for " + packageName + " in " +
            (revokeTime - System.currentTimeMillis()) + " ms")

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, revokeTime,
                PendingIntent.getBroadcast(context, 0,
                        getIntentForRevokingPackagePermissions(packageName), 0))

        synchronized(lock) {
            val oldRevokePermissionsPair = packagePermissionsToRevoke[packageName]!!
            val newRevokePermissionsPair = Pair(revokeTime, oldRevokePermissionsPair.second)
            packagePermissionsToRevoke[packageName] = newRevokePermissionsPair
            writeOneTimePermissionsLocked()
        }
    }

    private fun schedulePackageRevoke(packageName: String, revokeTime: Long) {
        synchronized(lock) {
            schedulePackageRevokeLocked(packageName, revokeTime)
        }
    }

    /**
     * Cancels the alarm scheduled to revoke a package
     *
     * @param packageName name of the package whose revocation will be cancelled
     */
    fun cancelAlarm(packageName: String) {
        alarmManager.cancel(PendingIntent.getBroadcast(context, 0,
                getIntentForRevokingPackagePermissions(packageName), 0))
    }

    private fun revokePackageLocked(packageName: String) {
        Log.v(LOG_TAG, "Revoking one-time permissions for $packageName")

        cancelAlarm(packageName)

        val permissions = packagePermissionsToRevoke[packageName]?.second ?: return
        for (permission in permissions) {
            packageManager.revokeRuntimePermission(packageName, permission,
                    Process.myUserHandle())
        }
        packagePermissionsToRevoke.remove(packageName)
        writeOneTimePermissionsLocked()
    }

    private fun revokePackage(packageName: String) {
        synchronized(lock) {
            revokePackageLocked(packageName)
        }
    }

    private fun revokeAll() {
        synchronized(lock) {
            val packagesToRevoke = mutableSetOf<String>()
            packagesToRevoke.addAll(packagePermissionsToRevoke.keys)
            for (packageName in packagesToRevoke) {
                revokePackageLocked(packageName)
            }
        }
    }

    private fun checkAndRevoke(packageName: String) {
        val permissionTimeout = Utils.getOneTimePermissionsTimeout()
        val now = System.currentTimeMillis()

        Log.v(LOG_TAG, "Checking if need to revoke one-time permissions for $packageName")
        if (isInForeground(packageName)) {
            schedulePackageRevoke(packageName, now + permissionTimeout)
            return
        }

        val usages = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, now - permissionTimeout, now)
        val usage = usages.find { u -> u.packageName == packageName }
        if (usage == null) {
            // No usage stats means app hasn't been active for a long time
            revokePackage(packageName)
            return
        }

        // TODO(evanseverson) verify that this is the correct value to look at
        val lastVisible = usage.lastTimeVisible
        if (lastVisible + permissionTimeout <= now) {
            revokePackage(packageName)
        } else {
            schedulePackageRevoke(packageName,
                    lastVisible + permissionTimeout)
        }
    }

    private fun isInForeground(packageName: String): Boolean {
        return activityManager.getPackageImportance(packageName) ==
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /**
     * Class which receives broadcasts to check/perform a revocation
     */
    class RevokePackageReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val revoker = OneTimePermissionRevoker.getInstance(context)
            if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                // STOPSHIP security issue
                // TODO(evanseverson) Do not use boot broadcast, all should be revoked with system
                //  server init
                // FIXME for more visibility
                revoker.revokeAll()
                return
            }

            revoker.checkAndRevoke(intent.data!!.path!!)
        }
    }

    companion object {
        private val LOG_TAG = OneTimePermissionRevoker::class.java.getSimpleName()

        private val DEFAULT_PERMISSION_TIMEOUT = (5 * 60 * 1000).toLong() // 5 minutes

        /**
         * @return the instance if has been instantiated before, otherwise null
         */
        @GuardedBy("lock")
        var instance: OneTimePermissionRevoker? = null
            private set

        private val lock = Any()

        /**
         * @return the instance if has been instantiated before, otherwise will create new one
         */
        fun getInstance(context: Context): OneTimePermissionRevoker {
            synchronized(lock) {
                instance = instance ?: OneTimePermissionRevoker(context)
                return instance as OneTimePermissionRevoker
            }
        }
    }
}
