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

@file:JvmName("UserSensitiveFlagsUtils")

package com.android.permissioncontroller.permission.utils

import android.Manifest
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.app.role.RoleManager.ROLE_ASSISTANT
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.Constants.ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY
import com.android.permissioncontroller.Constants.PREFERENCES_FILE
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.UserSensitivityLiveData
import com.android.permissioncontroller.permission.debug.shouldShowCameraMicIndicators
import com.android.permissioncontroller.permission.model.livedatatypes.UidSensitivityState
import com.android.permissioncontroller.permission.utils.Utils.FLAGS_ALWAYS_USER_SENSITIVE
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

private const val LOG_TAG = "UserSensitiveFlagsUtils"

/**
 * Update the [PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED] and
 * [PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED] for all apps of this user.
 *
 * @see UserSensitivityLiveData.loadDataAndPostValue
 *
 * @param user The user for whom packages will be updated
 * @param callback A callback which will be executed when finished
 */
fun updateUserSensitiveForUser(user: UserHandle, callback: Runnable) {
    GlobalScope.launch(IPC) {
        // a map of <uid, uid state>
        val uidUserSensitivity = UserSensitivityLiveData[user].getInitializedValue()
        if (uidUserSensitivity == null) {
            callback.run()
            throw IllegalStateException(
                "All uids sensitivity liveData should not be null if initialized")
        }
        updateUserSensitiveForUidsInternal(uidUserSensitivity, user, callback)
    }
}

private fun updateUserSensitiveForUidsInternal(
    uidsUserSensitivity: Map<Int, UidSensitivityState>,
    user: UserHandle,
    callback: Runnable?
) {
    val userContext = Utils.getUserContext(PermissionControllerApplication.get(), user)
    val pm = userContext.packageManager
    val assistantPkg = userContext.getSystemService(RoleManager::class.java)!!
        .getRoleHolders(ROLE_ASSISTANT).getOrNull(0)

    for ((uid, uidState) in uidsUserSensitivity) {
            for (pkg in uidState.packages) {
                for (perm in pkg.requestedPermissions) {
                    var flags = uidState.permStates[perm] ?: continue

                    // If this package is the current assistant, its microphone permission is not
                    // user sensitive
                    if (perm == Manifest.permission.RECORD_AUDIO &&
                        pkg.packageName == assistantPkg && shouldShowCameraMicIndicators()) {
                        val showMic = PermissionControllerApplication.get().getSharedPreferences(
                            PREFERENCES_FILE, MODE_PRIVATE).getBoolean(
                            ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY, false)
                        if (!showMic) {
                            flags = 0
                        }
                    }

                    try {
                        val oldFlags = pm.getPermissionFlags(perm, pkg.packageName, user) and
                            FLAGS_ALWAYS_USER_SENSITIVE
                        if (flags != oldFlags) {
                            pm.updatePermissionFlags(perm, pkg.packageName,
                                FLAGS_ALWAYS_USER_SENSITIVE, flags, user)
                        }
                    } catch (e: IllegalArgumentException) {
                        if (e.message?.startsWith("Unknown permission: ") == false) {
                            Log.e(LOG_TAG, "Unexpected exception while updating flags for " +
                                "${pkg.packageName} (uid $uid) permission $perm", e)
                        } else {
                            // Unknown permission - ignore
                        }
                    }
                }
            }
        }
    callback?.run()
}

/**
 * [updateUserSensitiveForUser] for a single [uid]
 *
 * @param uid The uid to be updated
 * @param callback A callback which will be executed when finished
 */
@JvmOverloads
fun updateUserSensitiveForUid(uid: Int, callback: Runnable? = null) {
    GlobalScope.launch(IPC) {
        val uidSensitivityState = UserSensitivityLiveData[uid].getInitializedValue()
        if (uidSensitivityState != null) {
            updateUserSensitiveForUidsInternal(uidSensitivityState,
                UserHandle.getUserHandleForUid(uid), callback)
        } else {
            Log.e(LOG_TAG, "No packages associated with uid $uid, not updating flags")
            callback?.run()
        }
    }
}

/**
 * Notifies the Permission Controller that the assistant role is about to change. If the change has
 * already happened, recompute the user sensitive flags for the assistant role. If the change has
 * not happened yet, register a listener for role changes, and recompute when it does change.
 *
 * @param packageName The package which will be added or removed from the assistant role
 * @param isAssistant {@code true} if the package is being added as the assistant, {@code false}
 * if it is being removed
 */
fun setMicUserSensitiveWhenReady(packageName: String, isAssistant: Boolean) {
    val context = PermissionControllerApplication.get().applicationContext

    val uid: Int
    try {
        uid = context.packageManager.getPackageUid(packageName,
            PackageManager.MATCH_ALL)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(LOG_TAG, "Can't find uid for packageName " + packageName + " user " +
            Process.myUserHandle() + " not setting microphone user sensitive flags")
        return
    }

    val user = Process.myUserHandle()
    val roleManager = context.getSystemService(RoleManager::class.java)

    val listener = object : OnRoleHoldersChangedListener {
        override fun onRoleHoldersChanged(roleName: String, user: UserHandle) {
            if (roleName == ROLE_ASSISTANT && user == Process.myUserHandle()) {
                if (setMicUserSensitive(context, packageName, uid, isAssistant)) {
                    roleManager!!.removeOnRoleHoldersChangedListenerAsUser(this, user)
                }
            }
        }
    }

    roleManager!!.addOnRoleHoldersChangedListenerAsUser(context.mainExecutor, listener,
        user)
    // If the role holder list is already updated (though this is unlikely, and we successfully
    // set the flags, remove the change listener
    if (setMicUserSensitive(context, packageName, uid, isAssistant)) {
        roleManager.removeOnRoleHoldersChangedListenerAsUser(listener, user)
    }
}

private fun setMicUserSensitive(
    context: Context,
    packageName: String,
    uid: Int,
    isAssistant: Boolean
): Boolean {
    val roleManager = context.getSystemService(RoleManager::class.java)
    val currentHolders = roleManager!!.getRoleHolders(ROLE_ASSISTANT)
    if (currentHolders.contains(packageName) == isAssistant) {
        updateUserSensitiveForUid(uid, null)
        return true
    }
    return false
}
