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

package com.android.permissioncontroller.permission.service

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.SEND_SMS
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.ComponentCallbacks2
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FLAG_PERMISSION_WHITELIST_UPGRADE
import android.content.pm.PackageManager.MATCH_FACTORY_ONLY
import android.os.Build.VERSION_CODES.R
import android.permission.PermissionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.dataRepositories
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.MockitoSession
import org.mockito.quality.Strictness.LENIENT
import java.util.concurrent.CompletableFuture
import org.mockito.Mockito.`when` as whenever

@RunWith(AndroidJUnit4::class)
class RuntimePermissionsUpgradeControllerTest {
    companion object {
        /** Reuse application mock as we otherwise end up with multiple applications */
        val application = mock(PermissionControllerApplication::class.java)

        init {
            whenever(application.applicationContext).thenReturn(application)
            whenever(application.createPackageContextAsUser(any(), anyInt(), any())).thenReturn(
                    application)

            whenever(application.registerComponentCallbacks(any())).thenAnswer {
                val dataRepository = it.arguments[0] as ComponentCallbacks2

                dataRepositories.add(dataRepository)
            }
        }
    }

    /** Latest permission database version known in this test */
    private val LATEST_VERSION = 8;

    /** Use a unique test package name for each test */
    private val TEST_PKG_NAME: String
        get() = Thread.currentThread().stackTrace
                .filter { it.className == this::class.java.name }[1].methodName

    /** Mockito session of this test */
    private var mockitoSession: MockitoSession? = null

    @Mock
    lateinit var packageManager: PackageManager
    @Mock
    lateinit var permissionManager: PermissionManager
    @Mock
    lateinit var activityManager: ActivityManager
    @Mock
    lateinit var appOpsManager: AppOpsManager

    /**
     * Set up {@link #packageManager} as if the passed packages are installed.
     *
     * @param pkgs packages that should pretend to be installed
     */
    private fun setPackages(vararg pkgs: Package) {
        whenever(packageManager.getInstalledPackagesAsUser(anyInt(), anyInt())).thenAnswer {
            val flags = it.arguments[0] as Int

            pkgs.filter { pkg ->
                (flags and MATCH_FACTORY_ONLY) == 0 || pkg.isPreinstalled
            }.map { pkg ->
                PackageInfo().apply {
                    packageName = pkg.name
                    requestedPermissions = pkg.permissions.map { it.name }.toTypedArray()
                    requestedPermissionsFlags = pkg.permissions.map {
                        if (it.isGranted) {
                            REQUESTED_PERMISSION_GRANTED
                        } else {
                            0
                        }
                    }.toIntArray()
                    applicationInfo = ApplicationInfo().apply {
                        targetSdkVersion = R
                    }
                }
            }
        }

        whenever(packageManager.getPermissionFlags(any(), any(), any())).thenAnswer {
            val permissionName = it.arguments[0] as String
            val packageName = it.arguments[1] as String

            pkgs.find { it.name == packageName }?.permissions
                    ?.find { it.name == permissionName }?.flags ?: 0
        }
    }

    /**
     * Set up system, i.e. point all the services to the mocks and forward some boring methods to
     * the system.
     */
    @Before
    fun initSystem() {
        initMocks(this)

        mockitoSession = mockitoSession().mockStatic(PermissionControllerApplication::class.java)
                .strictness(LENIENT).startMocking()

        whenever(PermissionControllerApplication.get()).thenReturn(application)

        whenever(application.getSystemService(PermissionManager::class.java)).thenReturn(
                permissionManager)
        whenever(application.getSystemService(ActivityManager::class.java)).thenReturn(activityManager)
        whenever(application.getSystemService(AppOpsManager::class.java)).thenReturn(appOpsManager)

        whenever(application.packageManager).thenReturn(packageManager)

        whenever(packageManager.getPermissionInfo(any(), anyInt())).thenAnswer {
            val permissionName = it.arguments[0] as String

            InstrumentationRegistry.getInstrumentation().getTargetContext().packageManager
                    .getPermissionInfo(permissionName, 0)
        }
    }

    /**
     * Call {@link RuntimePermissionsUpgradeController#upgradeIfNeeded) and wait until finished.
     */
    private fun upgradeIfNeeded() {
        val completionCallback = CompletableFuture<Unit>()
        RuntimePermissionsUpgradeController.upgradeIfNeeded(application, Runnable {
            completionCallback.complete(Unit)
        })
        completionCallback.join()
    }

    private fun setInitialDatabaseVersion(initialVersion: Int) {
        whenever(permissionManager.runtimePermissionsVersion).thenReturn(initialVersion)
    }

    private fun verifyWhitelisted(packageName: String, vararg permissionNames: String) {
        for (permissionName in permissionNames) {
            verify(packageManager, timeout(100)).addWhitelistedRestrictedPermission(
                    packageName, permissionName, FLAG_PERMISSION_WHITELIST_UPGRADE)
        }
    }

    private fun verifyNotWhitelisted(packageName: String, vararg permissionNames: String) {
        for (permissionName in permissionNames) {
            verify(packageManager, never()).addWhitelistedRestrictedPermission(eq(packageName),
                    eq(permissionName), anyInt())
        }
    }

    @Test
    fun restrictedPermissionsOfPreinstalledPackagesGetWhiteListed() {
        setInitialDatabaseVersion(LATEST_VERSION)

        setPackages(
            PreinstalledPackage(TEST_PKG_NAME,
                Permission(SEND_SMS)
            )
        )

        upgradeIfNeeded()

        verifyWhitelisted(TEST_PKG_NAME, SEND_SMS)
    }

    @Test
    fun nonRestrictedPermissionsOfPreinstalledPackagesDoNotGetWhiteListed() {
        setInitialDatabaseVersion(LATEST_VERSION)

        setPackages(
            PreinstalledPackage(TEST_PKG_NAME,
                Permission(ACCESS_FINE_LOCATION)
            )
        )

        upgradeIfNeeded()

        verifyNotWhitelisted(TEST_PKG_NAME, ACCESS_FINE_LOCATION)
    }

    @Test
    fun restrictedPermissionsOfNonPreinstalledPackagesDoNotGetWhiteListed() {
        setInitialDatabaseVersion(LATEST_VERSION)
        setPackages(
            Package(TEST_PKG_NAME,
                Permission(SEND_SMS)
            )
        )

        upgradeIfNeeded()

        verifyNotWhitelisted(TEST_PKG_NAME, SEND_SMS)
    }

    @After
    fun resetSystem() {
        // Send low memory notifications for all data repositories which will clear cached data
        dataRepositories.forEach { it.onLowMemory() }

        mockitoSession?.finishMocking()
    }

    private data class Permission(
        val name: String,
        val isGranted: Boolean = false,
        val flags: Int = 0
    )

    private open class Package(
        val name: String,
        val permissions: List<Permission> = emptyList(),
        val isPreinstalled: Boolean = false
    ) {
        constructor(name: String, vararg permission: Permission, isPreinstalled: Boolean = false) :
                this(name, permission.toList(), isPreinstalled)
    }

    private class PreinstalledPackage(
        name: String,
        permissions: List<Permission> = emptyList()
    ) : Package(name, permissions, true) {
        constructor(name: String, vararg permission: Permission) :
                this(name, permission.toList())
    }
}
