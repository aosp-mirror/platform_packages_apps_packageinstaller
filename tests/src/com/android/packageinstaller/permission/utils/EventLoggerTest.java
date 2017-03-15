/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.packageinstaller.permission.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;

import com.android.internal.logging.nano.MetricsProto;
import com.android.packageinstaller.shadows.ShadowMetricsLogger;
import com.android.packageinstaller.shadows.ShadowSystemProperties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests {@link com.android.packageinstaller.permission.utils.EventLogger}
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/apps/PackageInstaller/AndroidManifest.xml",
        shadows = {ShadowMetricsLogger.class, ShadowSystemProperties.class},
        sdk = 23)
public class EventLoggerTest {
    @Before
    public void setUp() {
        ShadowSystemProperties.setUserBuild(true);
        ShadowMetricsLogger.clearLogs();
    }

    @Test
    public void testValidRequested() {
        EventLogger.logPermissionRequested(null, Manifest.permission.READ_CALENDAR,
                "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_PERMISSION_REQUEST_READ_CALENDAR,
                        "testPackage")));
    }

    @Test
    public void testValidAppOpRequested() {
        EventLogger.logPermissionRequested(null, Manifest.permission.SYSTEM_ALERT_WINDOW,
                "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_APPOP_REQUEST_SYSTEM_ALERT_WINDOW,
                        "testPackage")));
    }

    @Test
    public void testValidDenied() {
        EventLogger.logPermissionDenied(null, Manifest.permission.READ_CALENDAR, "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_PERMISSION_REQUEST_READ_CALENDAR + 2,
                        "testPackage")));
    }

    @Test
    public void testInvalidRequestedEngBuild() throws Throwable {
        ShadowSystemProperties.setUserBuild(false);
        EventLogger.logPermissionRequested(null, "invalid", "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_PERMISSION_REQUEST_UNKNOWN,
                        "testPackage")));
    }

    @Test
    public void testInvalidDeniedEngBuild() throws Throwable {
        ShadowSystemProperties.setUserBuild(false);
        EventLogger.logPermissionRequested(null, "invalid", "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_PERMISSION_REQUEST_UNKNOWN,
                        "testPackage")));
    }

    @Test
    public void testInvalidRequestedUserBuild() throws Throwable {
        EventLogger.logPermissionRequested(null, "invalid", "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_PERMISSION_REQUEST_UNKNOWN,
                        "testPackage")));
    }

    @Test
    public void testInvalidDeniedUserBuild() throws Throwable {
        EventLogger.logPermissionDenied(null, "invalid", "testPackage");

        assertEquals(1, ShadowMetricsLogger.getLogs().size());
        assertTrue(ShadowMetricsLogger.getLogs().contains(
                new ShadowMetricsLogger.Log(null,
                        MetricsProto.MetricsEvent.ACTION_PERMISSION_REQUEST_UNKNOWN + 2,
                        "testPackage")));
    }
}
