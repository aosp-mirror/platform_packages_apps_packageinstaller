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
import android.os.UserHandle;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.permission.ui.handheld.ManageStandardPermissionsFragment;
import com.android.packageinstaller.permission.ui.handheld.PermissionUsageFragment;
import com.android.packageinstaller.permission.ui.wear.AppPermissionsFragmentWear;
import com.android.packageinstaller.permission.utils.Utils;

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

        String permissionName;
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

            case Intent.ACTION_REVIEW_PERMISSION_USAGE: {
                if (!Utils.isPermissionsHubEnabled()) {
                    finish();
                    return;
                }

                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
                String groupName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);
                long numMillis = getIntent().getLongExtra(Intent.EXTRA_DURATION_MILLIS,
                        Long.MAX_VALUE);

                if (permissionName != null) {
                    String permGroupName = Utils.getGroupOfPlatformPermission(permissionName);
                    if (permGroupName == null) {
                        Log.w(LOG_TAG, "Invalid platform permission: " + permissionName);
                    }
                    if (groupName != null && !groupName.equals(permGroupName)) {
                        Log.i(LOG_TAG,
                                "Inconsistent EXTRA_PERMISSION_NAME / EXTRA_PERMISSION_GROUP_NAME");
                        finish();
                        return;
                    }
                    if (groupName == null) {
                        groupName = permGroupName;
                    }
                }

                androidXFragment = PermissionUsageFragment.newInstance(groupName, numMillis);
            } break;

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
                        UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                        if (userHandle == null) {
                            userHandle = UserHandle.of(UserHandle.myUserId());
                        }
                        androidXFragment = com.android.packageinstaller.permission.ui.handheld
                                .AppPermissionsFragment.newInstance(packageName, userHandle);
                    }
                }
            } break;

            case Intent.ACTION_MANAGE_PERMISSION_APPS: {
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);

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
