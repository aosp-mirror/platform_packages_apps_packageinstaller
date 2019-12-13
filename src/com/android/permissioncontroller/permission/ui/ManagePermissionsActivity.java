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

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.auto.AutoAllAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoManageStandardPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoPermissionAppsFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionGroupsFragment;
import com.android.permissioncontroller.permission.ui.handheld.PermissionUsageFragment;
import com.android.permissioncontroller.permission.ui.wear.AppPermissionsFragmentWear;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.Random;

public final class ManagePermissionsActivity extends FragmentActivity {
    private static final String LOG_TAG = ManagePermissionsActivity.class.getSimpleName();

    public static final String EXTRA_ALL_PERMISSIONS =
            "com.android.permissioncontroller.extra.ALL_PERMISSIONS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DeviceUtils.isAuto(this)) {
            // Automotive relies on a different theme. Apply before calling super so that
            // fragments are restored properly on configuration changes.
            setTheme(R.style.CarSettings);
        }
        super.onCreate(savedInstanceState);

        // If there is a previous instance, re-use its Fragment instead of making a new one.
        if (savedInstanceState != null) {
            return;
        }

        android.app.Fragment fragment = null;
        Fragment androidXFragment = null;
        String action = getIntent().getAction();

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        long sessionId = getIntent().getLongExtra(Constants.EXTRA_SESSION_ID, INVALID_SESSION_ID);
        while (sessionId == INVALID_SESSION_ID) {
            sessionId = new Random().nextLong();
        }

        String permissionName;
        switch (action) {
            case Intent.ACTION_MANAGE_PERMISSIONS:
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoManageStandardPermissionsFragment.newInstance();
                } else if (DeviceUtils.isTelevision(this)) {
                    fragment =
                            com.android.permissioncontroller.permission.ui.television
                                    .ManagePermissionsFragment.newInstance();
                } else {
                    Bundle arguments = new Bundle();
                    arguments.putLong(EXTRA_SESSION_ID, sessionId);
                    setContentView(R.layout.nav_host_fragment);
                    Navigation.findNavController(this, R.id.nav_host_fragment).setGraph(
                            R.navigation.nav_graph, arguments);
                    return;

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

            case Intent.ACTION_MANAGE_APP_PERMISSION: {
                if (DeviceUtils.isAuto(this) || DeviceUtils.isTelevision(this)
                        || DeviceUtils.isWear(this)) {
                    Intent compatIntent = new Intent(this, AppPermissionActivity.class);
                    compatIntent.putExtras(getIntent().getExtras());
                    startActivity(compatIntent);
                    finish();
                    return;
                }
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
                String groupName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);
                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                String caller = getIntent().getStringExtra(AppPermissionActivity.EXTRA_CALLER_NAME);

                Bundle args = AppPermissionFragment.createArgs(packageName, permissionName,
                        groupName, userHandle, caller, sessionId, null);
                setContentView(R.layout.nav_host_fragment);
                NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);
                NavInflater inflater = navHost.getNavController().getNavInflater();
                NavGraph graph = inflater.inflate(R.navigation.nav_graph);
                graph.setStartDestination(R.id.app_permission);
                navHost.getNavController().setGraph(graph, args);
                return;
            }

            case Intent.ACTION_MANAGE_APP_PERMISSIONS: {
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                if (packageName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
                    finish();
                    return;
                }

                final boolean allPermissions = getIntent().getBooleanExtra(
                        EXTRA_ALL_PERMISSIONS, false);

                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                if (userHandle == null) {
                    userHandle = UserHandle.of(UserHandle.myUserId());
                }

                if (DeviceUtils.isAuto(this)) {
                    if (allPermissions) {
                        androidXFragment = AutoAllAppPermissionsFragment.newInstance(packageName,
                                userHandle);
                    } else {
                        androidXFragment = AutoAppPermissionsFragment.newInstance(packageName,
                                userHandle);
                    }
                } else if (DeviceUtils.isWear(this)) {
                    androidXFragment = AppPermissionsFragmentWear.newInstance(packageName);
                } else if (DeviceUtils.isTelevision(this)) {
                    fragment = com.android.permissioncontroller.permission.ui.television
                            .AppPermissionsFragment.newInstance(packageName);
                } else {
                    Bundle args = AppPermissionGroupsFragment.createArgs(packageName, userHandle,
                            sessionId, true);
                    setContentView(R.layout.nav_host_fragment);
                    NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.nav_host_fragment);
                    NavInflater inflater = navHost.getNavController().getNavInflater();
                    NavGraph graph = inflater.inflate(R.navigation.nav_graph);
                    graph.setStartDestination(R.id.app_permission_groups);
                    navHost.getNavController().setGraph(graph, args);
                    return;
                }
            } break;

            case Intent.ACTION_MANAGE_PERMISSION_APPS: {
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);

                if (permissionName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PERMISSION_NAME");
                    finish();
                    return;
                }
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoPermissionAppsFragment.newInstance(permissionName);
                } else if (DeviceUtils.isTelevision(this)) {
                    fragment = com.android.permissioncontroller.permission.ui.television
                            .PermissionAppsFragment.newInstance(permissionName);
                } else {
                    androidXFragment = com.android.permissioncontroller.permission.ui.handheld
                            .PermissionAppsFragment.newInstance(permissionName, sessionId);
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
        } else if (androidXFragment != null) {
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
