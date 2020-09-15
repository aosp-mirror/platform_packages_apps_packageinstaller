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

package com.android.permissioncontroller.permission

import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.OPSTR_CAMERA
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_PRIVACY
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.BeforeClass

/**
 * Super class with utilities for testing permission hub 2 code
 */
open class PermissionHub2Test {
    private val APP = "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    companion object {
        private const val PROPERTY_PERMISSIONS_HUB_2_ENABLED = "permissions_hub_2_enabled"

        private var wasPermissionHubEnabled = false

        @JvmStatic
        @BeforeClass
        fun enablePermissionHub2() {
            wasPermissionHubEnabled = DeviceConfig.getBoolean(NAMESPACE_PRIVACY,
                    PROPERTY_PERMISSIONS_HUB_2_ENABLED, false)

            if (!wasPermissionHubEnabled) {
                runShellCommand(
                        "device_config put privacy $PROPERTY_PERMISSIONS_HUB_2_ENABLED true")
            }
        }

        @JvmStatic
        @AfterClass
        fun disablePermissionHub2() {
            if (!wasPermissionHubEnabled) {
                runShellCommand(
                        "device_config put privacy $PROPERTY_PERMISSIONS_HUB_2_ENABLED false")
            }
        }
    }

    /**
     * Make {@value #APP} access the camera
     */
    protected fun accessCamera() {
        // App needs to be in foreground to be able to access camera
        context.startActivity(
                Intent().setComponent(ComponentName.createRelative(APP, ".DummyActivity"))
                        .setFlags(FLAG_ACTIVITY_NEW_TASK))

        eventually {
            assertThat(
                    SystemUtil.callWithShellPermissionIdentity {
                        context.getSystemService(AppOpsManager::class.java).startOp(
                                OPSTR_CAMERA, context.packageManager.getPackageUid(APP, 0),
                                APP, null, null)
                    }).isEqualTo(MODE_ALLOWED)
        }
    }
}