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

import android.os.Parcel
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpUsageLiveDataTest {
    @Test
    fun serializeDeserializeWithAttribution() {
        val opAccess = OpAccess("testPkg", "testAttr", UserHandle.of(23), 42)

        assertThat(parcelUnparcel(opAccess)).isEqualTo(opAccess)
    }

    @Test
    fun serializeDeserializeWithoutAttribution() {
        val opAccess = OpAccess("testPkg", null, UserHandle.of(23), 42)

        assertThat(parcelUnparcel(opAccess)).isEqualTo(opAccess)
    }

    private fun parcelUnparcel(original: OpAccess): OpAccess {
        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)

        return parcel.readParcelable(OpAccess::class.java.classLoader)!!
    }
}