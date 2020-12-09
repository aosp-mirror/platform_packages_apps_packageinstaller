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

import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.UserSensitivityLiveData
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

    for ((uid, uidState) in uidsUserSensitivity) {
            for (pkg in uidState.packages) {
                for (perm in pkg.requestedPermissions) {
                    var flags = uidState.permStates[perm] ?: continue

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
