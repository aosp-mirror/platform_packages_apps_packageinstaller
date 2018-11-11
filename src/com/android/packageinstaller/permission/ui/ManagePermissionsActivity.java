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

import static com.android.packageinstaller.permission.service.PermissionSearchIndexablesProvider
        .verifyIntent;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.permission.service.PermissionSearchIndexablesProvider;
import com.android.packageinstaller.permission.ui.handheld.ManageStandardPermissionsFragment;
import com.android.packageinstaller.permission.ui.handheld.PermissionUsageFragment;
import com.android.packageinstaller.permission.ui.wear.AppPermissionsFragmentWear;

public final class ManagePermissionsActivity extends FragmentActivity {
    private static final String LOG_TAG = ManagePermissionsActivity.class.getSimpleName();

    public static final String EXTRA_ALL_PERMISSIONS =
            "com.android.packageinstaller.extra.ALL_PERMISSIONS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.app.Fragment fragment = null;
        Fragment androidXFragment = null;
        String action = getIntent().getAction();

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        String permissionName = null;
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

            case PermissionSearchIndexablesProvider.ACTION_REVIEW_PERMISSION_USAGE:
                verifyIntent(this, getIntent());
                // fall through
            case Intent.ACTION_REVIEW_PERMISSION_USAGE:
                androidXFragment = PermissionUsageFragment.newInstance();
                break;

            case Intent.ACTION_MANAGE_APP_PERMISSIONS: {
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                if (packageName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
                    finish();
                    return;
                }
                if (DeviceUtils.isAuto(this)) {
                    fragment = com.android.packageinstaller.permission.ui.auto
                            .AppPermissionsFragment.newInstance(packageName);
                } else if (DeviceUtils.isWear(this)) {
                    androidXFragment = AppPermissionsFragmentWear.newInstance(packageName);
                } else if (DeviceUtils.isTelevision(this)) {
                    fragment = com.android.packageinstaller.permission.ui.television
                            .AppPermissionsFragment.newInstance(packageName);
                } else {
                    final boolean allPermissions = getIntent().getBooleanExtra(
                            EXTRA_ALL_PERMISSIONS, false);
                    if (allPermissions) {
                        androidXFragment = com.android.packageinstaller.permission.ui.handheld
                                .AllAppPermissionsFragment.newInstance(packageName);
                    } else {
                        androidXFragment = com.android.packageinstaller.permission.ui.handheld
                                .AppPermissionsFragment.newInstance(packageName);
                    }
                }
            } break;

            case PermissionSearchIndexablesProvider.ACTION_MANAGE_PERMISSION_APPS:
                permissionName = verifyIntent(this, getIntent());
                // fall through
            case Intent.ACTION_MANAGE_PERMISSION_APPS: {
                if (permissionName != null) {
                    permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
                }

                if (permissionName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PERMISSION_NAME");
                    finish();
                    return;
                }
                if (DeviceUtils.isTelevision(this)) {
                    fragment = com.android.packageinstaller.permission.ui.television
                            .PermissionAppsFragment.newInstance(permissionName);
                } else {
                    androidXFragment = com.android.packageinstaller.permission.ui.handheld
                            .PermissionAppsFragment.newInstance(permissionName);
                }
            } break;

            case Intent.ACTION_REVIEW_APP_PERMISSION_USAGE: {
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                if (packageName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
                    finish();
                    return;
                }
                androidXFragment = com.android.packageinstaller.permission.ui.handheld
                        .AppPermissionUsageFragment.newInstance(packageName);
            } break;

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
