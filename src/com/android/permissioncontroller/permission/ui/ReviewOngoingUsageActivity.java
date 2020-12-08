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

package com.android.permissioncontroller.permission.ui;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.permission.ui.handheld.ReviewOngoingUsageFragment;
import com.android.permissioncontroller.permission.debug.UtilsKt;

/**
 * A dialog listing the currently uses of camera, microphone, and location.
 */
public final class ReviewOngoingUsageActivity extends FragmentActivity {

    // Number of milliseconds in the past to look for accesses if nothing was specified.
    private static final long DEFAULT_MILLIS = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UtilsKt.shouldShowCameraMicIndicators()) {
            finish();
            return;
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        long numMillis = getIntent().getLongExtra(Intent.EXTRA_DURATION_MILLIS, DEFAULT_MILLIS);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                ReviewOngoingUsageFragment.newInstance(numMillis)).commit();
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // in automotive mode, there's no system wide back button, so need to add that
                if (DeviceUtils.isAuto(this)) {
                    onBackPressed();
                } else {
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}