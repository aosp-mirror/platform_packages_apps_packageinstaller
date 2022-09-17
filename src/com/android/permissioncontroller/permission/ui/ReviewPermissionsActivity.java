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

package com.android.permissioncontroller.permission.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.ConfirmActionDialogFragment.OnActionConfirmedListener;
import com.android.permissioncontroller.permission.ui.handheld.ReviewPermissionsFragment;
import com.android.permissioncontroller.permission.ui.wear.ReviewPermissionsWearFragment;

public final class ReviewPermissionsActivity extends FragmentActivity
        implements OnActionConfirmedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        PackageInfo packageInfo = getTargetPackageInfo();
        if (packageInfo == null) {
            finish();
            return;
        }

        if (DeviceUtils.isWear(this)) {
            Fragment fragment = ReviewPermissionsWearFragment.newInstance(packageInfo);
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment).commit();
        } else {
            setContentView(R.layout.review_permissions);
            if (getSupportFragmentManager().findFragmentById(R.id.preferences_frame) == null) {
                getSupportFragmentManager().beginTransaction().add(R.id.preferences_frame,
                        ReviewPermissionsFragment.newInstance(packageInfo)).commit();
            }
        }
    }

    @Override
    public void onActionConfirmed(String action) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.preferences_frame);
        if (fragment instanceof OnActionConfirmedListener) {
            ((OnActionConfirmedListener) fragment).onActionConfirmed(action);
        }
    }

    private PackageInfo getTargetPackageInfo() {
        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
