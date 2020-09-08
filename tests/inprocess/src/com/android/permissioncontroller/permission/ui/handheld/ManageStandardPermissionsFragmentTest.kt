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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.Intent
import android.content.Intent.ACTION_MANAGE_PERMISSIONS
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.revokePermission
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.permissioncontroller.DisableAnimationsRule
import com.android.permissioncontroller.R
import com.android.permissioncontroller.getPreferenceSummary
import com.android.permissioncontroller.getUsageCountsFromUi
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.permissioncontroller.permission.utils.Utils.getGroupOfPlatformPermission
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
 * Simple tests for {@link ManageStandardPermissionsFragment}
 */
@RunWith(AndroidJUnit4::class)
class ManageStandardPermissionsFragmentTest {
    private val LOCATION_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatRequestsLocation.apk"
    private val ADDITIONAL_DEFINER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatDefinesAdditionalPermission.apk"
    private val ADDITIONAL_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/" +
                    "AppThatUsesAdditionalPermission.apk"
    private val LOCATION_USER_PKG = "android.permission.cts.appthatrequestpermission"
    private val ADDITIONAL_DEFINER_PKG =
            "com.android.permissioncontroller.tests.appthatdefinespermission"
    private val ADDITIONAL_USER_PKG =
            "com.android.permissioncontroller.tests.appthatrequestpermission"

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val locationGroupLabel = getPermGroupLabel(context,
            getGroupOfPlatformPermission(ACCESS_COARSE_LOCATION)!!).toString()

    @get:Rule
    val disableAnimations = DisableAnimationsRule()

    @get:Rule
    val managePermissionsActivity = object : ActivityTestRule<ManagePermissionsActivity>(
            ManagePermissionsActivity::class.java) {
        override fun getActivityIntent() = Intent(ACTION_MANAGE_PERMISSIONS)
    }

    /**
     * Read the number of additional permissions from the Ui.
     *
     * @return number of additional permissions
     */
    private fun getAdditionalPermissionCount(): Int {
        val additionalPermissionPrefTitle = context.getString(R.string.additional_permissions)

        scrollToPreference(additionalPermissionPrefTitle)

        // Matches a single number out of the summary line, i.e. "...3..." -> "3"
        return getEventually {
            Regex("^[^\\d]*(\\d+)[^\\d]*\$")
                    .find(getPreferenceSummary(additionalPermissionPrefTitle))!!.groupValues[1]
                    .toInt()
        }
    }

    @Before
    fun wakeScreenUp() {
        wakeUpScreen()
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsInstalled() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        eventually {
            val afterInstall = getUsageCountsFromUi(locationGroupLabel)
            assertThat(afterInstall.granted).isEqualTo(original.granted)
            assertThat(afterInstall.total).isEqualTo(original.total + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsUninstalled() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel)).isNotEqualTo(original)
        }

        uninstallApp(LOCATION_USER_PKG)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel)).isEqualTo(original)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsGranted() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).total)
                    .isEqualTo(original.total + 1)
        }

        grantPermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).granted)
                    .isEqualTo(original.granted + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsRevoked() {
        val original = getUsageCountsFromUi(locationGroupLabel)

        install(LOCATION_USER_APK)
        grantPermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).total)
                    .isNotEqualTo(original.total)
            assertThat(getUsageCountsFromUi(locationGroupLabel).granted)
                    .isNotEqualTo(original.granted)
        }

        revokePermission(LOCATION_USER_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(locationGroupLabel).granted)
                    .isEqualTo(original.granted)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenAppGetsInstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                    .isEqualTo(additionalPermissionBefore + 1)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenUserGetsUninstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                    .isNotEqualTo(additionalPermissionBefore)
        }

        uninstallApp(ADDITIONAL_USER_PKG)
        eventually {
            assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenDefinerGetsUninstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_DEFINER_APK)
        install(ADDITIONAL_USER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                    .isNotEqualTo(additionalPermissionBefore)
        }

        uninstallApp(ADDITIONAL_DEFINER_PKG)
        eventually {
            assertThat(getAdditionalPermissionCount()).isEqualTo(additionalPermissionBefore)
        }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(LOCATION_USER_PKG)
        uninstallApp(ADDITIONAL_DEFINER_PKG)
        uninstallApp(ADDITIONAL_USER_PKG)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun workAroundVectorDrawable() {
            workAroundAppCompatCheckVectorDrawableSetup()
        }
    }
}
