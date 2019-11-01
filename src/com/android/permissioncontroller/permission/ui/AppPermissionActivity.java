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

package com.android.permissioncontroller.permission.ui;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.auto.AutoAppPermissionFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment;
import com.android.permissioncontroller.permission.utils.LocationUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.List;

/**
 * Manage a single permission of a single app
 */
public final class AppPermissionActivity extends FragmentActivity {
    private static final String LOG_TAG = AppPermissionActivity.class.getSimpleName();

    public static final String EXTRA_CALLER_NAME =
            "com.android.permissioncontroller.extra.CALLER_NAME";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DeviceUtils.isAuto(this)) {
            // Automotive relies on a different theme. Apply before calling super so that
            // fragments are restored properly on configuration changes.
            setTheme(R.style.CarSettings);
        }
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.e(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
            finish();
            return;
        }

        String permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
        String groupName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (permissionName == null && groupName == null) {
            Log.e(LOG_TAG, "Missing argument EXTRA_PERMISSION_NAME or "
                    + "EXTRA_PERMISSION_GROUP_NAME, at least one must be present.");
            finish();
            return;
        }
        if (groupName == null) {
            groupName = Utils.getGroupOfPlatformPermission(permissionName);
            PermissionInfo permission;
            try {
                permission = getPackageManager().getPermissionInfo(permissionName, 0);
                if (!permission.packageName.equals(Utils.OS_PKG)) {
                    List<PermissionGroupInfo> groupInfos =
                            getPackageManager().getAllPermissionGroups(0);
                    for (PermissionGroupInfo groupInfo : groupInfos) {
                        if (groupInfo.name.equals(permission.group)) {
                            groupName = permission.group;
                        }
                    }

                }
            } catch (PackageManager.NameNotFoundException e) {
                groupName = null;
            }
        }

        UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
        if (userHandle == null) {
            Log.e(LOG_TAG, "Missing mandatory argument EXTRA_USER");
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

        String caller = getIntent().getStringExtra(EXTRA_CALLER_NAME);

        Fragment androidXFragment;
        if (DeviceUtils.isAuto(this)) {
            androidXFragment = AutoAppPermissionFragment.newInstance(packageName, permissionName,
                    groupName, userHandle);
        } else {
            long sessionId = getIntent().getLongExtra(Constants.EXTRA_SESSION_ID,
                    INVALID_SESSION_ID);
            androidXFragment = AppPermissionFragment.newInstance(packageName, permissionName,
                    groupName, userHandle, caller, sessionId);
        }

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
