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
package com.android.permissioncontrollertest.incident;

import android.content.Context;
import android.content.res.Resources;
import android.os.ConditionVariable;
import android.os.IncidentManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.permissioncontroller.R;

import org.junit.Assert;
import org.junit.Test;

/** Test for incident report sharing approval. */
public class RequestConfirmationTest {
    private static final String TAG = "RequestConfirmationTest";

    // TODO: Get this from config?
    private static final String PERMISSION_CONTROLLER_PKG =
            "com.google.android.permissioncontroller";

    private static final int AID_SHELL = 2000;
    private static final String PKG_SHELL = "com.android.shell";

    private static final int TIMEOUT = 3000;

    private enum Status {
        PENDING,
        APPROVED,
        DENIED,
        TIMED_OUT
    }

    private class AuthListener extends IncidentManager.AuthListener {
        private Status mStatus = Status.PENDING;
        private final ConditionVariable mCondition = new ConditionVariable();

        @Override
        public void onReportApproved() {
            synchronized (mCondition) {
                mStatus = Status.APPROVED;
                mCondition.open();
            }
        }

        @Override
        public void onReportDenied() {
            synchronized (mCondition) {
                mStatus = Status.DENIED;
                mCondition.open();
            }
        }

        public void waitForResponse(long timeoutMs) {
            if (!mCondition.block(timeoutMs)) {
                mStatus = Status.TIMED_OUT;
            }
        }

        public Status getStatus() {
            return mStatus;
        }
    };

    private AuthListener requestApproval(int callingUid, final String callingPackage,
            final int flags) {
        final Context context = InstrumentationRegistry.getContext();
        final IncidentManager incidentManager = context.getSystemService(IncidentManager.class);
        final AuthListener listener = new AuthListener();
        incidentManager.requestAuthorization(callingUid, callingPackage, flags, listener);
        return listener;
    }

    private AuthListener cancelAuthorization(AuthListener listener) {
        final Context context = InstrumentationRegistry.getContext();
        final IncidentManager incidentManager = context.getSystemService(IncidentManager.class);
        incidentManager.cancelAuthorization(listener);
        return listener;
    }

    private UiObject2 waitAndAssert(UiDevice device, BySelector selector) {
        Assert.assertTrue(device.wait(Until.hasObject(selector), TIMEOUT));
        final UiObject2 obj = device.findObject(selector);
        Assert.assertNotNull(obj);
        return obj;
    }

    private UiObject2 waitAndAssert(UiObject2 object, BySelector selector) {
        Assert.assertTrue(object.wait(Until.hasObject(selector), TIMEOUT));
        final UiObject2 obj = object.findObject(selector);
        Assert.assertNotNull(obj);
        return obj;
    }

    private UiObject2 getAndAssert(UiDevice device, BySelector selector) {
        final UiObject2 obj = device.findObject(selector);
        Assert.assertNotNull(obj);
        return obj;
    }

    private UiObject2 getAndAssert(UiObject2 object, BySelector selector) {
        final UiObject2 obj = object.findObject(selector);
        Assert.assertNotNull(obj);
        return obj;
    }

    /**
     * Combined logic for the tests.
     */
    private void runTestFlow(boolean notification, Status expected) {
        AuthListener listener = null;
        try {
            Log.d(TAG, String.format(
                        "---- BEGIN -- notification=%-5s expected=%-9s ------------------",
                        notification, expected));

            final Resources res = InstrumentationRegistry.getTargetContext().getResources();

            Log.d(TAG, "Initialize UiDevice instance");
            final UiDevice device;
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

            Log.d(TAG, "Request approval");
            listener = requestApproval(AID_SHELL, PKG_SHELL,
                    notification ? 0 : IncidentManager.FLAG_CONFIRMATION_DIALOG);

            if (notification) {
                Log.d(TAG, "Open the notification panel");
                device.openNotification();

                Log.d(TAG, "Find the notification");
                final UiObject2 notifications = getAndAssert(device,
                        By.pkg("com.android.systemui")
                            .res("com.android.systemui:id/notification_stack_scroller"));

                Log.d(TAG, "Wait for the notification to appear");
                final UiObject2 text = waitAndAssert(notifications,
                        By.text(res.getString(R.string.incident_report_notification_title)));

                Log.d(TAG, "Clicking the text");
                text.click();
            }

            Log.d(TAG, "Wait for the dialog to appear");
            waitAndAssert(device, By.pkg(PERMISSION_CONTROLLER_PKG));

            if (expected != Status.TIMED_OUT) {
                final String buttonId = expected == Status.APPROVED ? "button1" : "button2";

                Log.d(TAG, "Find the button: " + buttonId);
                final UiObject2 button = getAndAssert(device,
                        By.res("android", buttonId));

                Log.d(TAG, "Click the button");
                button.click();
            }

            Log.d(TAG, "Wait for approval response");
            listener.waitForResponse(TIMEOUT);
            Assert.assertEquals(expected, listener.getStatus());

            // If we didn't click, cancel the request
            if (expected == Status.TIMED_OUT) {
                Log.d(TAG, "Canceling the request.");
                cancelAuthorization(listener);
                // We don't need to cancel again after the request
                listener = null;
            }

            if (notification) {
                Log.d(TAG, "Open the notification panel");
                device.openNotification();

                Log.d(TAG, "Find the notification");
                final UiObject2 notifications = getAndAssert(device,
                        By.pkg("com.android.systemui")
                            .res("com.android.systemui:id/notification_stack_scroller"));

                Log.d(TAG, "Wait for the notification to be cleared");
                Assert.assertTrue(notifications.wait(Until.gone(
                                By.text(res.getString(
                                        R.string.incident_report_notification_title))),
                            TIMEOUT));

                Log.d(TAG, "Close the notification panel");
                device.pressBack();
            }

            Log.d(TAG, "Wait for the dialog to be dismissed");
            Assert.assertTrue(device.wait(Until.gone(
                            By.pkg(PERMISSION_CONTROLLER_PKG)),
                        TIMEOUT));

            Log.d(TAG, String.format(
                        "---- END ---- notification=%-5s expected=%-9s ------------------",
                        notification, expected));
        } finally {
            // Clean up in case we failed somewhere along the way, junit doesn't actually
            // crash the process, so we still do need to cancel the listener.
            if (listener != null) {
                cancelAuthorization(listener);
            }
        }
    }

    /**
     * Test that an approval request is approved via a notification.
     */
    @Test
    public void testApprovedWithNotification() throws Exception {
        runTestFlow(true, Status.APPROVED);
    }

    /**
     * Test that an approval request is denied via a notification.
     */
    @Test
    public void testDeniedWithNotification() throws Exception {
        runTestFlow(true, Status.DENIED);
    }

    /**
     * Test that an approval request is neither approved nor denied without
     * taking the action (in the timeout period... we can't wait forever).
     */
    @Test
    public void testTimedOutWithNotification() throws Exception {
        runTestFlow(true, Status.TIMED_OUT);
    }

    /**
     * Test that an approval request is approved via a notification.
     */
    @Test
    public void testApprovedWithDialog() throws Exception {
        runTestFlow(false, Status.APPROVED);
    }

    /**
     * Test that an approval request is denied via a notification.
     */
    @Test
    public void testDeniedWithDialog() throws Exception {
        runTestFlow(false, Status.DENIED);
    }

    /**
     * Test that an approval request is neither approved nor denied without
     * taking the action (in the timeout period... we can't wait forever).
     */
    @Test
    public void testTimedOutWithDialog() throws Exception {
        runTestFlow(false, Status.TIMED_OUT);
    }

    /**
     * Test that creating the drawables in ConfirmationActivity will fail if we
     * do it with too many images.
     */
    @Test
    public void testTooManyImages() {
        // TODO(b/129485788)
    }

    /**
     * Test that creating the drawables in ConfirmationActivity will fail if we
     * do it with not too many images but with images that are too big.
     */
    @Test
    public void testTooBigImages() {
        // TODO(b/129485788)
    }

    public static final void main(String[] args) {
        Log.d(TAG, "RequestConfirmationTest args=" + java.util.Arrays.toString(args));
        System.out.println("RequestConfirmationTest args=" + java.util.Arrays.toString(args));
    }
}

