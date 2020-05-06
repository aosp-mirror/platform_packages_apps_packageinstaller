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

import android.content.Intent
import android.content.Intent.ACTION_MANAGE_APP_PERMISSIONS
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.UserHandle
import android.os.UserHandle.myUserId
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
 * Simple tests for {@link AllAppPermissionsFragment}
 */
@RunWith(AndroidJUnit4::class)
class AllAppPermissionsFragmentTest {
    private val ONE_PERMISSION_DEFINER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatDefinesAdditionalPermission.apk"
    private val PERMISSION_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/" +
                    "AppThatUsesAdditionalPermission.apk"
    private val TWO_PERMISSION_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/" +
                    "AppThatUsesTwoAdditionalPermissions.apk"
    private val DEFINER_PKG = "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val USER_PKG = "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val PERM_LABEL = "Permission B"
    private val SECOND_PERM_LABEL = "Permission C"

    @get:Rule
    val disableAnimations = DisableAnimationsRule()

    @get:Rule
    val managePermissionsActivity = object : ActivityTestRule<ManagePermissionsActivity>(
            ManagePermissionsActivity::class.java) {
        override fun getActivityIntent() = Intent(ACTION_MANAGE_APP_PERMISSIONS)
                .putExtra(EXTRA_PACKAGE_NAME, USER_PKG)

        override fun beforeActivityLaunched() {
            install(ONE_PERMISSION_DEFINER_APK)
            install(PERMISSION_USER_APK)
        }

        override fun afterActivityLaunched() {
            runOnUiThread {
                findNavController(activity, R.id.nav_host_fragment)
                        .navigate(R.id.perm_groups_to_all_perms,
                                AllAppPermissionsFragment.createArgs(
                                        USER_PKG, UserHandle.of(myUserId())))
            }
        }
    }

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Test
    fun usedPermissionsAreListed() {
        scrollToPreference(PERM_LABEL)
    }

    @Test
    fun permissionsAreAddedWhenAppIsUpdated() {
        scrollToPreference(PERM_LABEL)

        install(TWO_PERMISSION_USER_APK)
        eventually {
            scrollToPreference(SECOND_PERM_LABEL)
        }
    }

    @Test
    fun permissionsAreRemovedWhenAppIsUpdated() {
        install(TWO_PERMISSION_USER_APK)
        eventually {
            scrollToPreference(SECOND_PERM_LABEL)
        }

        install(PERMISSION_USER_APK)
        eventually {
            assertDoesNotHavePreference(SECOND_PERM_LABEL)
        }
    }

    @Test
    fun activityIsClosedWhenUserIsUninstalled() {
        uninstallApp(USER_PKG)
        eventually {
            assertThat(findNavController(managePermissionsActivity.activity, R.id.nav_host_fragment)
                    .currentDestination?.id).isNotEqualTo(R.id.all_app_permissions)
        }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(DEFINER_PKG)
        uninstallApp(USER_PKG)
    }
}