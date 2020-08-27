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
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.revokePermission
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.navigation.Navigation.findNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.permissioncontroller.DisableAnimationsRule
import com.android.permissioncontroller.R
import com.android.permissioncontroller.getUsageCountsFromUi
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.scrollToPreference
import com.android.permissioncontroller.wakeUpScreen
import com.android.permissioncontroller.workAroundAppCompatCheckVectorDrawableSetup
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link ManageCustomPermissionsFragment}
 */
@RunWith(AndroidJUnit4::class)
class ManageCustomPermissionsFragmentTest {
    private val ONE_PERMISSION_DEFINER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatDefinesAdditionalPermission.apk"
    private val PERMISSION_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/" +
                    "AppThatUsesAdditionalPermission.apk"
    private val DEFINER_PKG = "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val USER_PKG = "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val PERM_LABEL = "Permission A"
    private val PERM = "com.android.permissioncontroller.tests.A"

    @get:Rule
    val disableAnimations = DisableAnimationsRule()

    @get:Rule
    val managePermissionsActivity = object : ActivityTestRule<ManagePermissionsActivity>(
            ManagePermissionsActivity::class.java) {
        override fun getActivityIntent() = Intent(Intent.ACTION_MANAGE_PERMISSIONS)

        override fun afterActivityLaunched() {
            runOnUiThread {
                findNavController(activity, R.id.nav_host_fragment)
                        .navigate(R.id.standard_to_custom,
                                ManageCustomPermissionsFragment.createArgs(0))
            }
        }
    }

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsGranted() {
        install(ONE_PERMISSION_DEFINER_APK)
        install(PERMISSION_USER_APK)
        eventually {
            scrollToPreference(PERM_LABEL)
        }
        val original = getUsageCountsFromUi(PERM_LABEL)

        grantPermission(USER_PKG, PERM)
        eventually {
            assertThat(getUsageCountsFromUi(PERM_LABEL).granted).isEqualTo(original.granted + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsRevoked() {
        install(ONE_PERMISSION_DEFINER_APK)
        install(PERMISSION_USER_APK)
        eventually {
            scrollToPreference(PERM_LABEL)
        }
        val original = getUsageCountsFromUi(PERM_LABEL)

        grantPermission(USER_PKG, PERM)
        eventually {
            assertThat(getUsageCountsFromUi(PERM_LABEL)).isNotEqualTo(original)
        }

        revokePermission(USER_PKG, PERM)
        eventually {
            assertThat(getUsageCountsFromUi(PERM_LABEL)).isEqualTo(original)
        }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(DEFINER_PKG)
        uninstallApp(USER_PKG)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun workAroundVectorDrawable() {
            workAroundAppCompatCheckVectorDrawableSetup()
        }
    }
}
