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

import android.permission.cts.PermissionUtils.uninstallApp
import androidx.navigation.Navigation.findNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.permissioncontroller.R
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple tests for {@link PermissionAppsFragment} when showing custom permission
 */
@RunWith(AndroidJUnit4::class)
class CustomPermissionAppsFragmentTest : PermissionAppsFragmentTest(
    "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatUsesAdditionalPermission.apk",
    "com.android.permissioncontroller.tests.appthatrequestpermission",
    "com.android.permissioncontroller.tests.A",
    "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatDefinesAdditionalPermission.apk",
    "com.android.permissioncontroller.tests.appthatdefinespermission"
) {
    @Ignore("b/155112992")
    @Test
    fun fragmentIsClosedWhenPermissionIsRemoved() {
        uninstallApp(definerApk!!)
        eventually {
            assertThat(findNavController(managePermissionsActivity.activity, R.id.nav_host_fragment)
                .currentDestination?.id).isNotEqualTo(R.id.permission_apps)
        }
    }
}
