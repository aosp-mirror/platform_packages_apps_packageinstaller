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

package com.android.permissioncontroller.ui.handheld

import android.content.Intent
import android.content.Intent.ACTION_MANAGE_PERMISSION_APPS
import android.content.Intent.EXTRA_PERMISSION_NAME
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.navigation.Navigation.findNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.permissioncontroller.DisableAnimationsRule
import com.android.permissioncontroller.R
import com.android.permissioncontroller.assertDoesNotHavePreference
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.scrollToPreference
import com.android.permissioncontroller.wakeUpScreen
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link PermissionAppsFragment}
 */
@RunWith(AndroidJUnit4::class)
class PermissionAppsFragmentTest {
    private val ADDITIONAL_DEFINER_APK =
            "/data/local/tmp/permissioncontroller/tests/unit/AppThatDefinesAdditionalPermission.apk"
    private val ADDITIONAL_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/unit/" +
                    "AppThatUsesAdditionalPermission.apk"
    private val ADDITIONAL_DEFINER_PKG =
            "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val ADDITIONAL_USER_PKG =
            "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val PERM = "com.android.permissioncontroller.tests.A"

    @get:Rule
    val disableAnimations = DisableAnimationsRule()

    @get:Rule
    val managePermissionsActivity = object : ActivityTestRule<ManagePermissionsActivity>(
            ManagePermissionsActivity::class.java) {
        override fun getActivityIntent() = Intent(ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(EXTRA_PERMISSION_NAME, PERM)

        override fun beforeActivityLaunched() {
            install(ADDITIONAL_DEFINER_APK)
        }
    }

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Test
    fun appAppearsWhenInstalled() {
        assertDoesNotHavePreference(ADDITIONAL_USER_PKG)

        install(ADDITIONAL_USER_APK)
        eventually {
            scrollToPreference(ADDITIONAL_USER_PKG)
        }
    }

    @Test
    fun appDisappearsWhenUninstalled() {
        assertDoesNotHavePreference(ADDITIONAL_USER_PKG)

        install(ADDITIONAL_USER_APK)
        eventually {
            scrollToPreference(ADDITIONAL_USER_PKG)
        }

        uninstallApp(ADDITIONAL_USER_PKG)
        eventually {
            assertDoesNotHavePreference(ADDITIONAL_USER_PKG)
        }
    }

    @Test
    fun fragmentIsClosedWhenPermissionIsRemoved() {
        uninstallApp(ADDITIONAL_DEFINER_APK)
        eventually {
            assertThat(findNavController(managePermissionsActivity.activity, R.id.nav_host_fragment)
                    .currentDestination).isNotEqualTo(R.id.permission_apps)
        }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(ADDITIONAL_DEFINER_PKG)
        uninstallApp(ADDITIONAL_USER_PKG)
    }
}