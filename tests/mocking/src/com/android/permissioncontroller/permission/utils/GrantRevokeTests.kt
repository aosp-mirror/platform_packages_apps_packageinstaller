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

package com.android.permissioncontroller.permission.utils

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_FOREGROUND
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.permissionToOp
import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_AUTO_REVOKED
import android.content.pm.PackageManager.FLAG_PERMISSION_ONE_TIME
import android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED
import android.content.pm.PackageManager.FLAG_PERMISSION_REVOKED_COMPAT
import android.content.pm.PackageManager.FLAG_PERMISSION_SYSTEM_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_FIXED
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.PermissionInfo
import android.content.pm.PermissionInfo.PROTECTION_FLAG_INSTANT
import android.content.pm.PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY
import android.os.Build
import android.os.UserHandle
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPackageInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermGroupInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermInfo
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

import androidx.test.ext.junit.runners.AndroidJUnit4

private const val PERMISSION_CONTROLLER_CHANGED_FLAG_MASK = FLAG_PERMISSION_USER_SET or
        FLAG_PERMISSION_USER_FIXED or
        FLAG_PERMISSION_ONE_TIME or
        FLAG_PERMISSION_REVOKED_COMPAT or
        FLAG_PERMISSION_ONE_TIME or
        FLAG_PERMISSION_REVIEW_REQUIRED or
        FLAG_PERMISSION_AUTO_REVOKED

/**
 * A suite of unit tests to test the granting and revoking of permissions. Note- does not currently
 * test the Location Access Check.
 */
@RunWith(AndroidJUnit4::class)
class GrantRevokeTests {

    companion object {
        private const val PERM_GROUP_NAME = Manifest.permission_group.LOCATION
        private const val FG_PERM_NAME = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val FG_PERM_2_NAME = Manifest.permission.ACCESS_FINE_LOCATION
        private const val FG_PERM_NAME_NO_APP_OP = "android.permission.permWithNoAppOp"
        private const val BG_PERM_NAME = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        private const val TEST_PACKAGE_NAME = "android.permission.cts.testapp"
        private const val TEST_UID = 1
        private val TEST_USER = UserHandle.getUserHandleForUid(TEST_UID)
        private const val NO_FLAGS = 0
        private val FG_PERM_NAMES = listOf(FG_PERM_NAME, FG_PERM_2_NAME, FG_PERM_NAME_NO_APP_OP)
        private val OP_NAME = permissionToOp(FG_PERM_NAME)!!
        private val OP_2_NAME = permissionToOp(FG_PERM_2_NAME)!!

        @BeforeClass
        @JvmStatic
        fun checkAppOpsNotNullAndDistinct() {
            assumeNotNull(OP_NAME, OP_2_NAME)
            assumeTrue(OP_NAME != OP_2_NAME)
        }
    }

    @Mock
    val app: Application = mock(Application::class.java)

    /**
     * Create a mock Application object, with a mock packageManager, AppOpsManager, and
     * ActivityManager.
     *
     * @return The mocked Application object
     */
    private fun resetMockAppState() {
        `when`(app.packageManager).thenReturn(mock(PackageManager::class.java))

        val aom: AppOpsManager = mock(AppOpsManager::class.java)
        // Return an invalid app op state, so setOpMode will always attempt to change the op state
        `when`(aom.unsafeCheckOpRaw(anyString(), anyInt(), nullable(String::class.java)))
            .thenReturn(-1)
        `when`(app.getSystemService(AppOpsManager::class.java)).thenReturn(aom)

        `when`(app.getSystemService(ActivityManager::class.java)).thenReturn(
            mock(ActivityManager::class.java))
    }

    /**
     * Create a LightPackageInfo object with a particular set of properties
     *
     * @param perms The (name -> permissionInfo) of the permissions requested by the app
     * @param isPreMApp Whether this app targets pre-M
     * @param isInstantApp {@code true} iff this is an instant app
     */
    private fun createMockPackage(
        perms: Map<String, Boolean>,
        isPreMApp: Boolean = false,
        isInstantApp: Boolean = false
    ): LightPackageInfo {
        val permNames = mutableListOf<String>()
        val permFlags = mutableListOf<Int>()
        for ((permName, isGranted) in perms) {
            permNames.add(permName)
            permFlags.add(if (isGranted) {
                PERMISSION_GRANTED
            } else {
                PERMISSION_DENIED
            })
        }

        return LightPackageInfo(TEST_PACKAGE_NAME, listOf(), permNames, permFlags, TEST_UID,
                if (isPreMApp) {
                    Build.VERSION_CODES.LOLLIPOP
                } else {
                    Build.VERSION_CODES.R
                }, isInstantApp, isInstantApp, 0, 0L)
    }

    /**
     * Create a LightPermission object with a particular set of properties
     *
     * @param pkg Package requesting the permission
     * @param permName The name of the permission
     * @param granted Whether the permission is granted (should be false if the permission is compat
     * revoked)
     * @param backgroundPerm The name of this permission's background permission, if there is one
     * @param foregroundPerms The names of this permission's foreground permissions, if there are
     * any
     * @param flags The system permission flags of this permission
     * @param permInfoProtectionFlags The flags that the PermissionInfo object has (accessed by
     * PermissionInfo.getProtectionFlags)
     */
    private fun createMockPerm(
        pkgInfo: LightPackageInfo,
        permName: String,
        backgroundPerm: String? = null,
        foregroundPerms: List<String>? = null,
        flags: Int = NO_FLAGS,
        permInfoProtectionFlags: Int = 0
    ): LightPermission {
        val permInfo = LightPermInfo(permName, TEST_PACKAGE_NAME, PERM_GROUP_NAME, backgroundPerm,
            PermissionInfo.PROTECTION_DANGEROUS, permInfoProtectionFlags, 0)
        return LightPermission(pkgInfo, permInfo,
                pkgInfo.requestedPermissionsFlags[pkgInfo.requestedPermissions.indexOf(permName)]
                        == PERMISSION_GRANTED, flags, foregroundPerms)
    }

    /**
     * Create a LightAppPermGroup with a particular set of properties.
     *
     * @param pkg Package requesting the permission
     * @param perms The map of perm name to LightPermission (should be created with @createMockPerm)
     */
    private fun createMockGroup(
        pkgInfo: LightPackageInfo,
        perms: Map<String, LightPermission> = emptyMap()
    ): LightAppPermGroup {
        val pGi = LightPermGroupInfo(PERM_GROUP_NAME, TEST_PACKAGE_NAME, 0, 0, 0, false)
        return LightAppPermGroup(pkgInfo, pGi, perms, false, false)
    }

    /**
     * Create a list of strings which usefully states which flags are set in a group of flags.
     * Only checks for flags relevant to granting and revoking (so, for instance, policy fixed is
     * not checked).
     *
     * @param flags The flags to check
     *
     * @return a list of strings, representing which flags have been set
     */
    private fun flagsToString(flags: Int): List<String> {
        val flagStrings = mutableListOf<String>()
        if (flags and FLAG_PERMISSION_USER_SET != 0) {
            flagStrings.add("USER_SET")
        }
        if (flags and FLAG_PERMISSION_USER_FIXED != 0) {
            flagStrings.add("USER_FIXED")
        }
        if (flags and FLAG_PERMISSION_SYSTEM_FIXED != 0) {
            flagStrings.add("SYSTEM_FIXED")
        }
        if (flags and FLAG_PERMISSION_REVOKED_COMPAT != 0) {
            flagStrings.add("REVOKED_COMPAT")
        }
        if (flags and FLAG_PERMISSION_REVIEW_REQUIRED != 0) {
            flagStrings.add("REVIEW_REQUIRED")
        }
        if (flags and FLAG_PERMISSION_ONE_TIME != 0) {
            flagStrings.add("ONE_TIME")
        }
        return flagStrings
    }

    /**
     * Assert that the permissions of the given group match the expected state
     *
     * @param groupToCheck The LightAppPermGroup whose permissions we are checking
     * @param expectedState A map <permission name, grant state and permission flags pair>
     */
    private fun assertGroupPermState(
        groupToCheck: LightAppPermGroup,
        expectedState: Map<String, Pair<Boolean, Int>>
    ) {
        val perms = groupToCheck.permissions

        assertThat(perms.keys).isEqualTo(expectedState.keys)

        for ((permName, state) in expectedState) {
            val granted = state.first
            val flags = state.second

            assertWithMessage("permission $permName grant state incorrect")
                .that(perms[permName]?.isGrantedIncludingAppOp).isEqualTo(granted)

            val actualFlags = perms[permName]!!.flags
            assertWithMessage("permission $permName flags incorrect, expected" +
                "${flagsToString(flags)}; got ${flagsToString(actualFlags)}")
                .that(perms[permName]?.flags).isEqualTo(flags)
        }
    }

    /**
     * Verify that permission state was propagated to the system. Verify that grant or revoke
     * were called, if applicable, or verify they weren't. Verify that we have set flags
     * correctly, if applicable, or verify flags were not set.
     *
     * @param permName The name of the permission to verify
     * @param expectPermChange Whether or not a permission grant or revoke was expected. If false,
     * verify neither grant nor revoke were called
     * @param expectPermGranted If a permission change was expected, verify that the permission
     * was set to granted (if true) or revoked (if false)
     * @param expectedFlags The flags that the system should have set the permission to have
     * @param originalFlags The flags the permission originally had. Used to ensure the correct
     * flag mask was used
     */
    private fun verifyPermissionState(
        permName: String,
        expectPermChange: Boolean,
        expectPermGranted: Boolean = true,
        expectedFlags: Int = NO_FLAGS,
        originalFlags: Int = NO_FLAGS
    ) {
        val pm = app.packageManager
        if (expectPermChange) {
            if (expectPermGranted) {
                verify(pm).grantRuntimePermission(TEST_PACKAGE_NAME, permName, TEST_USER)
            } else {
                verify(pm).revokeRuntimePermission(TEST_PACKAGE_NAME, permName, TEST_USER)
            }
        } else {
            verify(pm, never()).grantRuntimePermission(TEST_PACKAGE_NAME, permName, TEST_USER)
            verify(pm, never()).revokeRuntimePermission(TEST_PACKAGE_NAME, permName, TEST_USER)
        }

        if (expectedFlags != originalFlags) {
            verify(pm).updatePermissionFlags(permName, TEST_PACKAGE_NAME,
                    PERMISSION_CONTROLLER_CHANGED_FLAG_MASK, expectedFlags, TEST_USER)
        } else {
            verify(pm, never()).updatePermissionFlags(eq(permName), eq(TEST_PACKAGE_NAME), anyInt(),
                anyInt(), eq(TEST_USER))
        }
    }

    /**
     * Verify that app op state was propagated to the system. Verify that setUidMode was called, if
     * applicable, or verify it wasn't.
     *
     * @param appOpName The name of the app op to check
     * @param expectAppOpSet Whether an app op change was expected. If false, verify setUidMode was
     * not called
     * @param expectedMode If a change was expected, the mode the app op should be set to
     */
    private fun verifyAppOpState(
        appOpName: String,
        expectAppOpSet: Boolean,
        expectedMode: Int = MODE_IGNORED
    ) {
        val aom = app.getSystemService(AppOpsManager::class.java)
        if (expectAppOpSet) {
            verify(aom).setUidMode(appOpName, TEST_UID, expectedMode)
        } else {
            verify(aom, never()).setUidMode(eq(appOpName), eq(TEST_UID), anyInt())
        }
    }

    /**
     * Verify that the test app either was or was not killed.
     *
     * @param shouldBeKilled Whether or not the app should have been killed
     */
    private fun verifyAppKillState(shouldBeKilled: Boolean) {
        val am = app.getSystemService(ActivityManager::class.java)
        if (shouldBeKilled) {
            verify(am).killUid(eq(TEST_UID), anyString())
        } else {
            verify(am, never()).killUid(eq(TEST_UID), anyString())
        }
    }

    /**
     * Test the granting of a single foreground permission. The permission and its app op should be
     * granted.
     */
    @Test
    fun grantOnePermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test the granting of two foreground permissions, one with a background permission. The
     * permissions and app ops should be granted, and the permissions marked user set. The second
     * app op should be set to foreground mode.
     */
    @Test
    fun grantTwoPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false, FG_PERM_2_NAME to false))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        perms[FG_PERM_2_NAME] = createMockPerm(pkg, FG_PERM_2_NAME, BG_PERM_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyPermissionState(permName = FG_PERM_2_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_2_NAME, expectAppOpSet = true,
            expectedMode = MODE_FOREGROUND)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags),
            FG_PERM_2_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test the granting of a permission with no app op. No app ops should change, but the
     * permission should be granted
     */
    @Test
    fun grantNoAppOpPerm() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME_NO_APP_OP to false))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME_NO_APP_OP] = createMockPerm(pkg, FG_PERM_NAME_NO_APP_OP)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME_NO_APP_OP, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppOpState(appOpName = OP_2_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME_NO_APP_OP to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test that granting a background permission grants the background permission, and allows the
     * app ops of its foreground permissions, but does not grant the foreground permission itself.
     */
    @Test
    fun grantBgPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true, BG_PERM_NAME to false))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[BG_PERM_NAME] = createMockPerm(pkg, BG_PERM_NAME, null, listOf(FG_PERM_NAME))
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantBackgroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to NO_FLAGS),
            BG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test granting a foreground permission, then a background. After the foreground permission is
     * granted, the app op should be in foreground mode. After the background permission, it should
     * be fully allowed.
     */
    @Test
    fun grantBgAndFgPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false, BG_PERM_NAME to false))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[BG_PERM_NAME] = createMockPerm(pkg, BG_PERM_NAME, null, listOf(FG_PERM_NAME))
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = false)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_FOREGROUND)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags),
            BG_PERM_NAME to (false to NO_FLAGS))
        assertGroupPermState(newGroup, expectedState)

        resetMockAppState()
        val newGroup2 = KotlinUtils.grantBackgroundRuntimePermissions(app, newGroup)

        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState2 = mutableMapOf(FG_PERM_NAME to (true to newFlags),
            BG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup2, expectedState2)
    }

    /**
     * Test granting a group with a foreground permission that is system fixed, and another that
     * isn't. The system fixed permission should not change.
     */
    @Test
    fun grantSystemFixedTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false, FG_PERM_2_NAME to false))
        val permFlags = FLAG_PERMISSION_SYSTEM_FIXED
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        perms[FG_PERM_2_NAME] = createMockPerm(pkg, FG_PERM_2_NAME, flags = permFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyPermissionState(permName = FG_PERM_2_NAME, expectPermChange = false,
            expectedFlags = permFlags, originalFlags = permFlags)
        verifyAppOpState(appOpName = OP_2_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags),
            FG_PERM_2_NAME to (false to permFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test granting a group with a background permission that is system fixed, and a background
     * permission that isn't. The system fixed permission should not change.
     */
    @Test
    fun grantBgSystemFixedTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false, BG_PERM_NAME to false))
        val permFlags = FLAG_PERMISSION_SYSTEM_FIXED
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[BG_PERM_NAME] = createMockPerm(pkg, BG_PERM_NAME, null, FG_PERM_NAMES, permFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_FOREGROUND)
        verifyAppKillState(shouldBeKilled = false)

        var expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags),
            BG_PERM_NAME to (false to permFlags))
        assertGroupPermState(newGroup, expectedState)

        resetMockAppState()
        val newGroup2 = KotlinUtils.grantBackgroundRuntimePermissions(app, newGroup)

        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = false,
            expectedFlags = permFlags, originalFlags = permFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags),
            BG_PERM_NAME to (false to permFlags))
        assertGroupPermState(newGroup2, expectedState)
    }

    /**
     * Test granting a one time granted permission. The permission should still be granted, but no
     * longer be one time.
     */
    @Test
    fun grantOneTimeTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val oldFlags = FLAG_PERMISSION_ONE_TIME
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, flags = oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false,
            expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test granting a compat revoked (permission granted, app op denied) permission. The app op
     * should be allowed, as should the permission. The app should also be killed.
     */
    @Test
    fun grantPreMAppTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false), isPreMApp = true)
        val oldFlags = FLAG_PERMISSION_REVOKED_COMPAT
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, flags = oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)
        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false,
            expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyAppKillState(shouldBeKilled = true)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test the granting of a single foreground permission for a Pre M app. Nothing should change,
     * and the app should not be killed
     */
    @Test
    fun grantAlreadyGrantedPreMTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        val flags = FLAG_PERMISSION_USER_SET
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, flags = flags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()
        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false,
            expectedFlags = flags, originalFlags = flags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to flags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test that an instant app cannot have regular (non-instant) permission granted.
     */
    @Test
    fun cantGrantInstantAppStandardPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false), isInstantApp = true)
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to NO_FLAGS))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test that a pre-M app (pre runtime permissions) can't have a runtime only permission granted.
     */
    @Test
    fun cantGrantPreRuntimeAppWithRuntimeOnlyPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false), isPreMApp = true)
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME,
            permInfoProtectionFlags = PROTECTION_FLAG_RUNTIME_ONLY)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to NO_FLAGS))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test that an instant package can have an instant permission granted.
     */
    @Test
    fun grantInstantAppInstantPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false), isInstantApp = true)
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME,
            permInfoProtectionFlags = PROTECTION_FLAG_INSTANT)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = true, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_ALLOWED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test that granting a permission clears the user fixed and review required flags.
     */
    @Test
    fun grantClearsUserFixedAndReviewRequired() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val oldFlags = FLAG_PERMISSION_USER_FIXED or FLAG_PERMISSION_REVIEW_REQUIRED
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, flags = oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()
        val newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false,
            expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test revoking one foreground permission. The permission and app op should be revoked.
     */
    @Test
    fun revokeOnePermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test revoking two foreground permissions. Both permissions and app ops should be revoked.
     */
    @Test
    fun revokeTwoPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true, FG_PERM_2_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg,FG_PERM_NAME)
        perms[FG_PERM_2_NAME] = createMockPerm(pkg, FG_PERM_2_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyPermissionState(permName = FG_PERM_2_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_2_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags),
            FG_PERM_2_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test the revoking of a permission with no app op. No app ops should change, but the
     * permission should be revoked.
     */
    @Test
    fun revokeNoAppOpPerm() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME_NO_APP_OP to true))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME_NO_APP_OP] = createMockPerm(pkg, FG_PERM_NAME_NO_APP_OP)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()
        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME_NO_APP_OP, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppOpState(appOpName = OP_2_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME_NO_APP_OP to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test that revoking a background permission revokes the permission, and sets the app ops of
     * its foreground permissions to foreground only, and does not revoke the foreground permission.
     */
    @Test
    fun revokeBgPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true, BG_PERM_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[BG_PERM_NAME] = createMockPerm(pkg, BG_PERM_NAME, null, listOf(FG_PERM_NAME))
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeBackgroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false)
        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_FOREGROUND)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (true to NO_FLAGS),
            BG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test granting a foreground permission, then a background. After the foreground permission is
     * granted, the app op should be in foreground mode. After the background permission, it should
     * be fully allowed.
     */
    @Test
    fun revokeBgAndFgPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true, BG_PERM_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[BG_PERM_NAME] = createMockPerm(pkg, BG_PERM_NAME, null, listOf(FG_PERM_NAME))
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeBackgroundRuntimePermissions(app, group, true)

        val newFlags = FLAG_PERMISSION_USER_SET or FLAG_PERMISSION_USER_FIXED
        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_FOREGROUND)
        verifyAppKillState(shouldBeKilled = false)
        val expectedState = mutableMapOf(FG_PERM_NAME to (true to NO_FLAGS),
            BG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)

        resetMockAppState()
        val newGroup2 = KotlinUtils.revokeForegroundRuntimePermissions(app, newGroup, true)

        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState2 = mutableMapOf(FG_PERM_NAME to (false to newFlags),
            BG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup2, expectedState2)
    }

    /**
     * Test revoking a group with a foreground permission that is system fixed, and another that
     * isn't. The system fixed permission should not change.
     */
    @Test
    fun revokeSystemFixedTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true, FG_PERM_2_NAME to true))
        val permFlags = FLAG_PERMISSION_SYSTEM_FIXED
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[FG_PERM_2_NAME] = createMockPerm(pkg, FG_PERM_2_NAME, flags = permFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyPermissionState(permName = FG_PERM_2_NAME, expectPermChange = false)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppOpState(appOpName = OP_2_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags),
            FG_PERM_2_NAME to (true to permFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test revoking a group with a background permission that is system fixed, and a background
     * permission that isn't. The system fixed permission should not change.
     */
    @Test
    fun revokeBgSystemFixedTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true, BG_PERM_NAME to true))
        val permFlags = FLAG_PERMISSION_SYSTEM_FIXED
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, BG_PERM_NAME)
        perms[BG_PERM_NAME] = createMockPerm(pkg, BG_PERM_NAME, null, FG_PERM_NAMES, permFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        var expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags),
            BG_PERM_NAME to (true to permFlags))
        assertGroupPermState(newGroup, expectedState)

        resetMockAppState()
        val newGroup2 = KotlinUtils.revokeBackgroundRuntimePermissions(app, newGroup)

        verifyPermissionState(permName = BG_PERM_NAME, expectPermChange = false)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags),
            BG_PERM_NAME to (true to permFlags))
        assertGroupPermState(newGroup2, expectedState)
    }

    /**
     * Test revoking a one time granted permission. The permission should be revoked, but no
     * longer be one time.
     */
    @Test
    fun revokeOneTimeTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val oldFlags = FLAG_PERMISSION_ONE_TIME
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, flags = oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test compat revoking (permission granted, app op denied) permission. The app op
     * should be revoked, while the permission remains granted. The app should also be killed.
     */
    @Test
    fun revokePreMAppTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true), isPreMApp = true)
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET or FLAG_PERMISSION_REVOKED_COMPAT
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false,
            expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = true)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test the revoking of a single foreground permission for a Pre M app. Nothing should change,
     * and the app should not be killed
     */
    @Test
    fun revokeAlreadyRevokedPreMTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false))
        val perms = mutableMapOf<String, LightPermission>()
        val flags = FLAG_PERMISSION_USER_SET
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, flags = flags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to flags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test revoking a standard permission for an instant app, to show that instant app status does
     * not affect the revoking of a permission.
     */
    @Test
    fun revokeInstantAppTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true), isInstantApp = true)
        val perms = mutableMapOf<String, LightPermission>()
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group, true)

        val newFlags = FLAG_PERMISSION_USER_SET or FLAG_PERMISSION_USER_FIXED
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Revoke a permission that was user fixed, and set it to no longer be user fixed. The
     * permission and its app op should be revoked, and the permission should no longer be user
     * fixed.
     */
    @Test
    fun revokeUserFixedPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        val oldFlags = FLAG_PERMISSION_USER_FIXED
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, null, null, oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Revoke a permission that was not user fixed, and set it to be user fixed. The permission and
     * its app op should be revoked, and the permission should be user fixed.
     */
    @Test
    fun revokeAndSetUserFixedPermTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to true))
        val perms = mutableMapOf<String, LightPermission>()
        val oldFlags = FLAG_PERMISSION_USER_SET
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, null, null, oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group, true)

        val newFlags = oldFlags or FLAG_PERMISSION_USER_FIXED
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = true,
            expectPermGranted = false, expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = true, expectedMode = MODE_IGNORED)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }

    /**
     * Test revoking an already revoked permission, while changing its user fixed state from true
     * to false. The user fixed should update, but the state should stay the same otherwise.
     */
    @Test
    fun changeUserFixedTest() {
        val pkg = createMockPackage(mapOf(FG_PERM_NAME to false))
        val perms = mutableMapOf<String, LightPermission>()
        val oldFlags = FLAG_PERMISSION_USER_FIXED
        perms[FG_PERM_NAME] = createMockPerm(pkg, FG_PERM_NAME, null, null, oldFlags)
        val group = createMockGroup(pkg, perms)
        resetMockAppState()

        val newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, group)

        val newFlags = FLAG_PERMISSION_USER_SET
        verifyPermissionState(permName = FG_PERM_NAME, expectPermChange = false,
            expectedFlags = newFlags, originalFlags = oldFlags)
        verifyAppOpState(appOpName = OP_NAME, expectAppOpSet = false)
        verifyAppKillState(shouldBeKilled = false)

        val expectedState = mutableMapOf(FG_PERM_NAME to (false to newFlags))
        assertGroupPermState(newGroup, expectedState)
    }
}
