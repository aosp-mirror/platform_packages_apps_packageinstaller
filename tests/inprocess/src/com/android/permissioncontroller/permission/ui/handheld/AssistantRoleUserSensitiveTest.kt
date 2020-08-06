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

package com.android.permissioncontroller.permission.ui.handheld

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
import android.os.Process
import android.provider.DeviceConfig
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.permissioncontroller.Constants.PREFERENCES_FILE
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.debug.PROPERTY_CAMERA_MIC_ICONS_ENABLED
import com.android.permissioncontroller.permission.debug.shouldShowCameraMicIndicators
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/**
 * Tests the User Sensitive behavior of the Assistant role
 */
@RunWith(AndroidJUnit4::class)
class AssistantRoleUserSensitiveTest {
    private val context: Context = PermissionControllerApplication.get()
    private val packageManager = context.packageManager
    private val roleManager = context.getSystemService(RoleManager::class.java)!!
    private val sharedPrefs = context
        .getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)

    companion object {
        private const val ROLE_NAME = RoleManager.ROLE_ASSISTANT
        private const val APP_APK_PATH = "/data/local/tmp/permissioncontroller/tests/inprocess/" +
            "AppThatUsesMicrophonePermission.apk"
        private const val APP_PACKAGE_NAME =
            "com.android.permissioncontroller.tests.appthatrequestmicrophonepermission"
        private const val ASSISTANT_USER_SENSITIVE_SETTING =
            "assistant_record_audio_is_user_sensitive_key"
        private const val ALWAYS_USER_SENSITIVE = FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED or
            FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED
        private const val TIMEOUT_MILLIS = 15 * 1000L
    }

    private var originalRoleHolder: String? = null
    private var originalShowAssistantSetting: Boolean = false
    private var cameraMicIconsWereDisabled: Boolean = false

    @Before
    fun setUp() {
        enableCameraMicIcons()
        installApp()
        saveRoleHolderAndShowAssistantSetting()
    }

    private fun enableCameraMicIcons() {
        if (!shouldShowCameraMicIndicators()) {
            cameraMicIconsWereDisabled = true
            runWithShellPermissionIdentity {
                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_CAMERA_MIC_ICONS_ENABLED, "true", false)
            }
        }
        assertThat(shouldShowCameraMicIndicators()).isTrue()
    }

    private fun installApp() {
        assertThat(runShellCommand("pm install -r --force-queryable " +
            "--user ${Process.myUserHandle().identifier} $APP_APK_PATH")).contains("Success")
        eventually {
            assertThat(micPermIsUserSensitive()).isTrue()
        }
    }

    private fun saveRoleHolderAndShowAssistantSetting() {
        originalShowAssistantSetting =
            sharedPrefs.getBoolean(ASSISTANT_USER_SENSITIVE_SETTING, false)

        val currentHolders = getRoleHolders()
        if (currentHolders.isNotEmpty()) {
            originalRoleHolder = currentHolders[0]
            removeRoleHolder(ROLE_NAME, currentHolders[0])
        }
    }

    @After
    fun tearDown() {
        restoreRoleHolderAndShowAssistantSetting()
        disableCameraMicIconsIfNeeded()
        uninstallApp()
    }

    private fun disableCameraMicIconsIfNeeded() {
        if (cameraMicIconsWereDisabled) {
            runWithShellPermissionIdentity {
                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_CAMERA_MIC_ICONS_ENABLED, "false", false)
            }
            cameraMicIconsWereDisabled = false
        }
    }

    private fun restoreRoleHolderAndShowAssistantSetting() {
        setShowAssistantMic(originalShowAssistantSetting)

        val roleHolder = originalRoleHolder
        if (roleHolder != null) {
            addRoleHolder(ROLE_NAME, roleHolder)
        } else {
            removeRoleHolder()
        }
    }

    private fun uninstallApp() {
        runShellCommand("pm uninstall --user ${Process.myUserHandle().identifier} " +
            APP_PACKAGE_NAME)
    }

    @Test
    fun appBecomesNonSensitiveWhenBecomingAssistant() {
        setShowAssistantMic(false)
        assertThat(micPermIsUserSensitive()).isTrue()
        addRoleHolder()
        eventually {
            assertThat(micPermIsUserSensitive()).isFalse()
        }
    }

    @Test
    fun appBecomesSensitiveWhenRemovedFromAssistant() {
        setShowAssistantMic(false)
        addRoleHolder()
        eventually {
            assertThat(micPermIsUserSensitive()).isFalse()
        }
        removeRoleHolder()
        eventually {
            assertThat(micPermIsUserSensitive()).isTrue()
        }
    }

    @Test
    fun appIsStillSensitiveIfShowAssistantEnabled() {
        setShowAssistantMic(true)
        addRoleHolder()
        Thread.sleep(500)
        assertThat(micPermIsUserSensitive()).isTrue()
    }

    @Test
    fun appIsNotSensitiveIfShowAssistantDisabled() {
        setShowAssistantMic(true)
        addRoleHolder()
        Thread.sleep(500)
        assertThat(micPermIsUserSensitive()).isTrue()
        setShowAssistantMic(false)

        // Required to have flags updated, the UI switch manually calls for an update
        removeRoleHolder()
        addRoleHolder()

        eventually {
            assertThat(micPermIsUserSensitive()).isFalse()
        }
    }

    private fun micPermIsUserSensitive(packageName: String = APP_PACKAGE_NAME): Boolean {
        return callWithShellPermissionIdentity {
            (packageManager.getPermissionFlags(Manifest.permission.RECORD_AUDIO,
                packageName, Process.myUserHandle()) and ALWAYS_USER_SENSITIVE) != 0
        }
    }

    private fun setShowAssistantMic(show: Boolean) {
        sharedPrefs.edit().putBoolean(ASSISTANT_USER_SENSITIVE_SETTING, show).apply()
    }

    @Throws(Exception::class)
    private fun addRoleHolder(
        roleName: String = ROLE_NAME,
        packageName: String = APP_PACKAGE_NAME,
        expectSuccess: Boolean = true
    ) {
        val future = CallbackFuture()
        roleManager.addRoleHolderAsUser(roleName,
            packageName, 0, Process.myUserHandle(), context.mainExecutor, future)
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess)
    }

    @Throws(Exception::class)
    private fun removeRoleHolder(
        roleName: String = ROLE_NAME,
        packageName: String = APP_PACKAGE_NAME,
        expectSuccess: Boolean = true
    ) {
        val future = CallbackFuture()
        roleManager.removeRoleHolderAsUser(roleName,
            packageName, 0, Process.myUserHandle(), context.mainExecutor, future)
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isEqualTo(expectSuccess)
    }

    @Throws(Exception::class)
    private fun getRoleHolders(roleName: String = ROLE_NAME): List<String> {
        return roleManager.getRoleHolders(roleName)
    }

    private inner class CallbackFuture : CompletableFuture<Boolean>(), Consumer<Boolean> {

        override fun accept(successful: Boolean) {
            complete(successful)
        }
    }
}
