/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.packageinstaller.permission.ui;

import android.app.Activity;
import android.app.AppOpsManager;
import android.os.Binder;
import android.os.IBinder;

public class OverlayTouchActivity extends Activity {
    private final IBinder mToken = new Binder();

    @Override
    protected void onResume() {
        super.onResume();
        setOverlayAllowed(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setOverlayAllowed(true);
    }

    private void setOverlayAllowed(boolean allowed) {
        AppOpsManager appOpsManager = getSystemService(AppOpsManager.class);
        if (appOpsManager != null) {
            appOpsManager.setUserRestriction(AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                    !allowed, mToken);
        }
    }
}
