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

package com.android.permissioncontroller.permission.data

import android.content.Context
import android.content.res.Resources.ID_NULL
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.permission.cts.PermissionUtils.install
import android.permission.cts.PermissionUtils.uninstallApp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val APK =
        "/data/local/tmp/permissioncontroller/tests/inprocess/AppThatUsesCameraPermission.apk"
private const val PKG = "com.android.permissioncontroller.tests.appthatrequestpermission"

class AttributionLabelLiveDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().context as Context

    @Before
    fun installAttributingApp() {
        install(APK)
    }

    @Test
    fun getValidTag() {
        AttributionLabelLiveData["testTag", PKG, myUserHandle()].withLoadedValue {
            assertThat(context.packageManager.getResourcesForApplication(PKG)
                    .getString(it!!)).isEqualTo("Test Attribution Label")
        }
    }

    @Test
    fun getDefaultTag() {
        AttributionLabelLiveData[null, PKG, myUserHandle()].withLoadedValue {
            assertThat(it).isEqualTo(ID_NULL)
        }
    }

    @Test
    fun getInvalidTag() {
        AttributionLabelLiveData["invalidTag", PKG, myUserHandle()].withLoadedValue {
            assertThat(it).isNull()
        }
    }

    @Test
    fun getFromInvalidPkg() {
        AttributionLabelLiveData["testTag", "invalid pkg", myUserHandle()].withLoadedValue {
            assertThat(it).isNull()
        }
    }

    @Test
    fun getFromInvalidUser() {
        AttributionLabelLiveData["testTag", PKG, UserHandle.of(Int.MAX_VALUE)].withLoadedValue {
            assertThat(it).isNull()
        }
    }

    @After
    fun uninstallAttributingApp() {
        uninstallApp(PKG)
    }
}
