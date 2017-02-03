/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.packageinstaller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.IActivityManager;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;

import com.android.packageinstaller.shadows.ShadowPackageInstaller;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

/**
 * Unit-tests for {@link InstallStart}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/apps/PackageInstaller/AndroidManifest.xml",
        shadows = ShadowPackageInstaller.class,
        sdk = 23)
public class InstallStartTest {
    private static final int TEST_UID = 12345;
    private static final int TEST_SESSION_ID = 12;
    private static final String TEST_INSTALLER = "com.test.installer";

    @Mock
    private IActivityManager mMockActivityManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockActivityManager.getLaunchedFromUid(any(IBinder.class)))
                .thenReturn(TEST_UID);
    }

    @Test
    public void testActionConfirmPermission() {
        // GIVEN the activity was launched with ACTION_CONFIRM_PERMISSION and no extras
        Intent launchIntent = new Intent(PackageInstaller.ACTION_CONFIRM_PERMISSIONS);
        ActivityController<InstallStart> activityController =
                Robolectric.buildActivity(InstallStart.class).withIntent(launchIntent);
        activityController.get().injectIActivityManager(mMockActivityManager);

        // WHEN onCreate is called
        activityController.create();

        // THEN the activity should be finishing itself, as its no UI activity
        assertTrue(shadowOf(activityController.get()).isFinishing());

        // THEN PackageInstallerActivity should be launched
        assertEquals(PackageInstallerActivity.class.getName(),
                shadowOf(activityController.get()).getNextStartedActivity().getComponent()
                        .getClassName());
    }

    @Test
    public void testActionConfirmPermissionWithExtra() {
        // GIVEN the activity was launched with ACTION_CONFIRM_PERMISSION and
        //       EXTRA_NOT_UNKNOWN_SOURCE set to true
        Intent launchIntent = new Intent(PackageInstaller.ACTION_CONFIRM_PERMISSIONS)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        ActivityController<InstallStart> activityController =
                Robolectric.buildActivity(InstallStart.class).withIntent(launchIntent);
        activityController.get().injectIActivityManager(mMockActivityManager);

        // WHEN onCreate is called
        activityController.create();

        // THEN the activity should be finishing itself, as its no UI activity
        assertTrue(shadowOf(activityController.get()).isFinishing());

        // THEN PackageInstallerActivity should be launched
        Intent intent = shadowOf(activityController.get()).getNextStartedActivity();
        assertEquals(PackageInstallerActivity.class.getName(),
                intent.getComponent().getClassName());
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false));
    }

    @Test
    public void testActionConfirmPermissionWithSessionId() {
        // GIVEN the activity was launched with ACTION_CONFIRM_PERMISSION and
        //       EXTRA_SESSION_ID set
        Intent launchIntent = new Intent(PackageInstaller.ACTION_CONFIRM_PERMISSIONS)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, TEST_SESSION_ID);
        ActivityController<InstallStart> activityController =
                Robolectric.buildActivity(InstallStart.class).withIntent(launchIntent);
        activityController.get().injectIActivityManager(mMockActivityManager);

        // GIVEN that a PackageInstallerSession with that session id exists
        PackageInstaller.SessionInfo session = new PackageInstaller.SessionInfo();
        session.sessionId = TEST_SESSION_ID;
        session.installerPackageName = TEST_INSTALLER;
        ShadowPackageInstaller.putSessionInfo(session);

        // WHEN onCreate is called
        activityController.create();

        // THEN the activity should be finishing itself, as its no UI activity
        assertTrue(shadowOf(activityController.get()).isFinishing());

        // THEN PackageInstallerActivity should be launched
        Intent intent = shadowOf(activityController.get()).getNextStartedActivity();
        assertEquals(PackageInstallerActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(TEST_INSTALLER,
                intent.getStringExtra(PackageInstallerActivity.EXTRA_CALLING_PACKAGE));
    }
}
