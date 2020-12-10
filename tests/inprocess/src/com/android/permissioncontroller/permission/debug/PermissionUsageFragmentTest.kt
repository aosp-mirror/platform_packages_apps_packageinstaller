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

package com.android.permissioncontroller.permission.debug

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.permissioncontroller.R
import com.android.permissioncontroller.getPreferenceSummary
import com.android.permissioncontroller.permission.PermissionHub2Test
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.scrollToPreference
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link PermissionUsageFragment}
 */
@RunWith(AndroidJUnit4::class)
class PermissionUsageFragmentTest : PermissionHub2Test() {
    private val APK =
            "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatUsesCameraPermission.apk"
    private val APP = "com.android.permissioncontroller.tests.appthatrequestpermission"
    private val APP_LABEL = "CameraRequestApp"

    @get:Rule
    val managePermissionsActivity = object : ActivityTestRule<ManagePermissionsActivity>(
            ManagePermissionsActivity::class.java) {
        override fun getActivityIntent() = Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE)

        override fun beforeActivityLaunched() {
            install(APK)
            grantPermission(APP, CAMERA)

            accessCamera()
        }
    }

    @Test
    fun cameraAccessShouldBeShown() {
        eventually {
            try {
                scrollToPreference(APP_LABEL)
            } catch (e: Exception) {
                onView(withContentDescription(R.string.permission_usage_refresh)).perform(click())
                throw e
            }
        }

        assertThat(getPreferenceSummary(APP_LABEL)).isEqualTo("Camera")

        // Expand usage
        onView(withText(APP_LABEL)).perform(click())
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(APP)
    }
}