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

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.permission.ui.handheld.ManageStandardPermissionsFragment;
import com.android.packageinstaller.permission.ui.handheld.PermissionUsageFragment;

public final class ManagePermissionsActivity extends FragmentActivity {
    private static final String LOG_TAG = ManagePermissionsActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.app.Fragment fragment = null;
        Fragment androidXFragment = null;
        String action = getIntent().getAction();

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        switch (action) {
            case Intent.ACTION_MANAGE_PERMISSIONS:
                if (DeviceUtils.isTelevision(this)) {
                    fragment =
                            com.android.packageinstaller.permission.ui.television
                                    .ManagePermissionsFragment.newInstance();
                } else {
                    androidXFragment = ManageStandardPermissionsFragment.newInstance();
                }
                break;

            case Intent.ACTION_REVIEW_PERMISSION_USAGE:
                androidXFragment = PermissionUsageFragment.newInstance();
                break;

            default: {
                Log.w(LOG_TAG, "Unrecognized action " + action);
                finish();
                return;
            }
        }

        if (fragment != null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, fragment)
                    .commit();
        } else {
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                    androidXFragment).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // in automotive mode, there's no system wide back button, so need to add that
        if (DeviceUtils.isAuto(this)) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    onBackPressed();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
