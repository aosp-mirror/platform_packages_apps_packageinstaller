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

package com.android.permissioncontroller

import android.support.test.uiautomator.UiDevice
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Wake up screen
 */
fun wakeUpScreen() {
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    uiDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP")
    uiDevice.executeShellCommand("wm dismiss-keyguard")
}

/**
 * If the first vector drawable is loaded inside PermissionController,
 * ResourceManagerInternal.checkVectorDrawableSetup() will try to load R.drawable.abc_vector_test
 * with PermissionController's resources, however the R class will be ours because our copy of
 * AndroidX is taking precedence, resulting in a Resources.NotFoundException. We can try to be the
 * first one loading a vector drawable to work around this.
 */
fun workAroundAppCompatCheckVectorDrawableSetup() {
    val context = InstrumentationRegistry.getInstrumentation().context
    AppCompatResources.getDrawable(
        context, com.android.permissioncontroller.tests.inprocess.R.drawable.abc_vector_test
    )
}
