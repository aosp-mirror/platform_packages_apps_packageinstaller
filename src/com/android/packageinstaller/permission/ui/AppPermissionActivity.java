/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.packageinstaller.permission.ui.handheld.AppPermissionFragment;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.Utils;

/**
 * Manage a single permission of a single app
 */
public final class AppPermissionActivity extends FragmentActivity {
    private static final String LOG_TAG = AppPermissionActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
            finish();
            return;
        }

        String permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
        if (permissionName == null) {
            Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PERMISSION_NAME");
            finish();
            return;
        }
        String groupName = Utils.getGroupOfPlatformPermission(permissionName);

        UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
        if (userHandle == null) {
            Log.i(LOG_TAG, "Missing mandatory argument EXTRA_USER");
            finish();
            return;
        }

        if (LocationUtils.isLocationGroupAndProvider(this, groupName,
                packageName)) {
            Intent intent = new Intent(this, LocationProviderInterceptDialog.class);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
            startActivity(intent);
            finish();
            return;
        }

        if (LocationUtils.isLocationGroupAndControllerExtraPackage(
                this, groupName, packageName)) {
            // Redirect to location controller extra package settings.
            LocationUtils.startLocationControllerExtraPackageSettings(this);
            finish();
            return;
        }

        Fragment androidXFragment = AppPermissionFragment.newInstance(packageName, permissionName,
                groupName, userHandle);

        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                    androidXFragment).commit();
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
