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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.Intent
import android.content.Intent.ACTION_MANAGE_PERMISSIONS
import android.permission.cts.PermissionUtils.grantPermission
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import android.support.test.uiautomator.UiDevice
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.permissioncontroller.DisableAnimationsRule
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity
import com.android.permissioncontroller.permission.utils.KotlinUtils.getPermGroupLabel
import com.android.permissioncontroller.permission.utils.Utils.getGroupOfPlatformPermission
import com.google.common.truth.Truth.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link ManageStandardPermissionsFragment}
 */
@RunWith(AndroidJUnit4::class)
class ManageStandardPermissionsFragmentTest {
    private val LOCATION_USER_APK =
            "/data/local/tmp/permissioncontroller/tests/unit/AppThatRequestsLocation.apk"
    private val ADDITIONAL_PERMISSION_DEFINER_APK =
            "/data/local/tmp/permissioncontroller/tests/unit/AppThatDefinesAdditionalPermission.apk"
    private val TEST_PKG = "android.permission.cts.appthatrequestpermission"

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val disableAnimations = DisableAnimationsRule()

    @get:Rule
    val managePermissionsActivity = object : ActivityTestRule<ManagePermissionsActivity>(
            ManagePermissionsActivity::class.java) {
        override fun getActivityIntent() = Intent(ACTION_MANAGE_PERMISSIONS)
    }

    /**
     * Get a {@link ViewAction} that runs a command on a view.
     *
     * @param onViewAction action to run on the view
     */
    private fun <T : View> runOnView(onViewAction: (T) -> Unit): ViewAction {
        return object : ViewAction {
            override fun getDescription() = "run on view"

            override fun getConstraints() = any(View::class.java)

            override fun perform(uiController: UiController, view: View) {
                onViewAction(view as T)
            }
        }
    }

    /**
     * Scroll until a preference is visible.
     *
     * @param title title of the preference
     */
    private fun scrollToPreference(title: CharSequence) {
        onView(withId(androidx.preference.R.id.recycler_view))
                .perform(scrollTo<RecyclerView.ViewHolder>(
                        hasDescendant(withText(title.toString()))))
    }

    /**
     * Get summary of preference.
     *
     * @param title title of the preference
     *
     * @return summary of preference
     */
    private fun getPreferenceSummary(title: CharSequence): CharSequence {
        lateinit var summary: CharSequence

        onView(allOf(hasSibling(withText(title.toString())), withResourceName("summary")))
                .perform(runOnView<TextView> { summary = it.text })

        return summary
    }

    /**
     * Read the {@link UsageCount} of the group of the permission from the Ui.
     *
     * @param permission permission the count should be read for
     *
     * @return usage counts for the group of the permission
     */
    private fun getUsageCountsFromUi(permission: String): UsageCount {
        val groupLabel = getPermGroupLabel(context, getGroupOfPlatformPermission(permission)!!)

        scrollToPreference(groupLabel)

        return getEventually {
            val summary = getPreferenceSummary(groupLabel)

            // Matches two numbers out of the summary line, i.e. "...3...12..." -> "3", "12"
            val groups = Regex("^[^\\d]*(\\d+)[^\\d]*(\\d+)[^\\d]*\$")
                    .find(summary)?.groupValues
                    ?: throw Exception("No usage counts found")

            UsageCount(groups[1].toInt(), groups[2].toInt())
        }
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
    fun wakeUpScreen() {
        val uiDevice = UiDevice.getInstance(instrumentation)

        uiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP")
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Test
    fun groupSummaryGetsUpdatedWhenAppGetsInstalled() {
        val original = getUsageCountsFromUi(ACCESS_COARSE_LOCATION)

        install(LOCATION_USER_APK)
        eventually {
            val afterInstall = getUsageCountsFromUi(ACCESS_COARSE_LOCATION)
            assertThat(afterInstall.granted).isEqualTo(original.granted)
            assertThat(afterInstall.total).isEqualTo(original.total + 1)
        }
    }

    @Test
    fun groupSummaryGetsUpdatedWhenPermissionGetsGranted() {
        val original = getUsageCountsFromUi(ACCESS_COARSE_LOCATION)

        install(LOCATION_USER_APK)
        eventually {
            assertThat(getUsageCountsFromUi(ACCESS_COARSE_LOCATION).total)
                    .isEqualTo(original.total + 1)
        }

        grantPermission(TEST_PKG, ACCESS_COARSE_LOCATION)
        eventually {
            assertThat(getUsageCountsFromUi(ACCESS_COARSE_LOCATION).granted)
                    .isEqualTo(original.granted + 1)
        }
    }

    @Test
    fun additionalPermissionSummaryGetUpdateWhenAppGetsInstalled() {
        val additionalPermissionBefore = getAdditionalPermissionCount()

        install(ADDITIONAL_PERMISSION_DEFINER_APK)
        eventually {
            assertThat(getAdditionalPermissionCount())
                    .isEqualTo(additionalPermissionBefore + 1)
        }
    }

    @After
    fun uninstallTestApp() {
        uninstallApp(TEST_PKG)
    }

    /**
     * Usage counts as read via {@link #getUsageCountsFromUi}.
     */
    private data class UsageCount(
        /** Number of apps with permission granted */
        val granted: Int,
        /** Number of apps that request permissions */
        val total: Int
    )
}