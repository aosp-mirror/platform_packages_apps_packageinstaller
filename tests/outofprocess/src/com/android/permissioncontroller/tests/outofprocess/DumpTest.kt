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

package com.android.permissioncontroller.tests.outofprocess

import android.os.ParcelFileDescriptor.AutoCloseInputStream
import android.os.UserHandle.myUserId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.permissioncontroller.PermissionControllerProto.PermissionControllerDumpProto
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.InvalidProtocolBufferException
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets.UTF_8

@RunWith(AndroidJUnit4::class)
class DumpTest {
    private val OS_PKG = "android"

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun getDump(): PermissionControllerDumpProto {
        val dumpFile = instrumentation.getUiAutomation()
                .executeShellCommand("dumpsys permissionmgr --proto")
        val dump = AutoCloseInputStream(dumpFile).readBytes()

        try {
            return PermissionControllerDumpProto.parseFrom(dump)
        } catch (e: InvalidProtocolBufferException) {
            fail("Cannot parse proto from ${String(dump, UTF_8)}")
            throw e
        }
    }

    @Test
    fun autoRevokeDumpHasCurrentUser() {
        val dump = getDump()

        // Sometimes the dump takes to long to get generated, esp. on low end devices
        assumeTrue(dump.autoRevoke.usersList.isNotEmpty())

        assertThat(dump.autoRevoke.usersList.map { it.userId }).contains(myUserId())
    }

    @Test
    fun autoRevokeDumpHasAndroidPackage() {
        val dump = getDump()

        // Sometimes the dump takes to long to get generated, esp. on low end devices
        assumeTrue(dump.autoRevoke.usersList.isNotEmpty())

        assertThat(dump.autoRevoke.usersList[myUserId()].packagesList.map { it.packageName })
                .contains(OS_PKG)
    }
}
