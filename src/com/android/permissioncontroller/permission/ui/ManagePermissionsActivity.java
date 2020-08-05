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

import static com.android.permissioncontroller.Constants.ACTION_MANAGE_AUTO_REVOKE;
import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FOR_AUTO_REVOKE;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FROM_INTENT;
import static com.android.permissioncontroller.PermissionControllerStatsLog.AUTO_REVOKE_NOTIFICATION_CLICKED;

import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
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
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.debug.PermissionUsageFragment;
import com.android.permissioncontroller.permission.debug.UtilsKt;
import com.android.permissioncontroller.permission.ui.auto.AutoAllAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoAppPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoManageStandardPermissionsFragment;
import com.android.permissioncontroller.permission.ui.auto.AutoPermissionAppsFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionGroupsFragment;
import com.android.permissioncontroller.permission.ui.handheld.AutoRevokeFragment;
import com.android.permissioncontroller.permission.ui.handheld.PermissionAppsFragment;
import com.android.permissioncontroller.permission.ui.legacy.AppPermissionActivity;
import com.android.permissioncontroller.permission.ui.wear.AppPermissionsFragmentWear;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.Random;

public final class ManagePermissionsActivity extends FragmentActivity {
    private static final String LOG_TAG = ManagePermissionsActivity.class.getSimpleName();

    public static final String EXTRA_ALL_PERMISSIONS =
            "com.android.permissioncontroller.extra.ALL_PERMISSIONS";

    /**
     * Name of the extra parameter that is the fragment that called the current fragment.
     */
    public static final String EXTRA_CALLER_NAME =
            "com.android.permissioncontroller.extra.CALLER_NAME";

    // The permission group which was interacted with
    public static final String EXTRA_RESULT_PERMISSION_INTERACTED = "com.android"
            + ".permissioncontroller.extra.RESULT_PERMISSION_INTERACTED";
    /**
     * The result of the permission in terms of {@link GrantPermissionsViewHandler.Result}
     */
    public static final String EXTRA_RESULT_PERMISSION_RESULT = "com.android"
            + ".permissioncontroller.extra.PERMISSION_RESULT";

    /**
     * The requestCode used when we decide not to use this activity, but instead launch
     * another activity in our place. When that activity finishes, we set it's result
     * as our result and then finish.
     */
    private static final int PROXY_ACTIVITY_REQUEST_CODE = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DeviceUtils.isAuto(this)) {
            // Automotive relies on a different theme. Apply before calling super so that
            // fragments are restored properly on configuration changes.
            setTheme(R.style.CarSettings);
        }
        super.onCreate(savedInstanceState);

        // If this is not a phone (which uses the Navigation component), and there is a previous
        // instance, re-use its Fragment instead of making a new one.
        if ((DeviceUtils.isTelevision(this) || DeviceUtils.isAuto(this)
                || DeviceUtils.isWear(this)) && savedInstanceState != null) {
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

        int autoRevokeAction =
                APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FOR_AUTO_REVOKE;
        int openFromIntentAction =
                APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION__ACTION__OPENED_FROM_INTENT;

        String permissionName;
        switch (action) {
            case Intent.ACTION_MANAGE_PERMISSIONS:
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoManageStandardPermissionsFragment.newInstance();
                } else if (DeviceUtils.isTelevision(this)) {
                    androidXFragment =
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
                if (!UtilsKt.shouldShowPermissionsDashboard()) {
                    finish();
                    return;
                }

                String groupName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);
                androidXFragment = PermissionUsageFragment.newInstance(groupName, Long.MAX_VALUE);
            } break;

            case Intent.ACTION_MANAGE_APP_PERMISSION: {
                if (DeviceUtils.isAuto(this) || DeviceUtils.isTelevision(this)
                        || DeviceUtils.isWear(this)) {
                    Intent compatIntent = new Intent(this, AppPermissionActivity.class);
                    compatIntent.putExtras(getIntent().getExtras());
                    startActivityForResult(compatIntent, PROXY_ACTIVITY_REQUEST_CODE);
                    return;
                }
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);
                String groupName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_GROUP_NAME);
                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                String caller = getIntent().getStringExtra(EXTRA_CALLER_NAME);

                Bundle args = AppPermissionFragment.createArgs(packageName, permissionName,
                        groupName, userHandle, caller, sessionId, null);
                setNavGraph(args, R.id.app_permission);
                return;
            }

            case Intent.ACTION_MANAGE_APP_PERMISSIONS: {
                String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                if (packageName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
                    finish();
                    return;
                }

                UserHandle userHandle = getIntent().getParcelableExtra(Intent.EXTRA_USER);
                if (userHandle == null) {
                    userHandle = UserHandle.of(UserHandle.myUserId());
                }

                try {
                    int uid = getPackageManager().getApplicationInfoAsUser(packageName, 0,
                            userHandle).uid;
                    long settingsSessionId = getIntent().getLongExtra(
                            Intent.ACTION_AUTO_REVOKE_PERMISSIONS, INVALID_SESSION_ID);
                    if (settingsSessionId != INVALID_SESSION_ID) {
                        sessionId = settingsSessionId;
                        Log.i(LOG_TAG, "sessionId: " + sessionId
                                + " Reaching AppPermissionGroupsFragment for auto revoke. "
                                + "packageName: " + packageName + " uid " + uid);
                        PermissionControllerStatsLog.write(
                                APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION, sessionId, uid,
                                packageName, autoRevokeAction);
                    } else {
                        if (KotlinUtils.INSTANCE.isROrAutoRevokeEnabled(getApplication(),
                                packageName, userHandle)) {
                            Log.i(LOG_TAG, "sessionId: " + sessionId
                                    + " Reaching AppPermissionGroupsFragment from intent. "
                                    + "packageName " + packageName + " uid " + uid);
                            PermissionControllerStatsLog.write(
                                    APP_PERMISSION_GROUPS_FRAGMENT_AUTO_REVOKE_ACTION, sessionId,
                                    uid, packageName, openFromIntentAction);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // Do no logging
                }

                final boolean allPermissions = getIntent().getBooleanExtra(
                        EXTRA_ALL_PERMISSIONS, false);


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
                    androidXFragment = com.android.permissioncontroller.permission.ui.television
                            .AppPermissionsFragment.newInstance(packageName, userHandle);
                } else {
                    Bundle args = AppPermissionGroupsFragment.createArgs(packageName, userHandle,
                            sessionId, true);
                    setNavGraph(args, R.id.app_permission_groups);
                    return;
                }
            } break;

            case Intent.ACTION_MANAGE_PERMISSION_APPS: {
                permissionName = getIntent().getStringExtra(Intent.EXTRA_PERMISSION_NAME);

                String permissionGroupName = getIntent().getStringExtra(
                        Intent.EXTRA_PERMISSION_GROUP_NAME);
                if (permissionGroupName == null) {
                    try {
                        PermissionInfo permInfo = getPackageManager().getPermissionInfo(
                                permissionName, 0);
                        permissionGroupName = Utils.getGroupOfPermission(permInfo);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.i(LOG_TAG, "Permission " + permissionName + " does not exist");
                    }
                }

                if (permissionName == null && permissionGroupName == null) {
                    Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PERMISSION_NAME or"
                            + "EXTRA_PERMISSION_GROUP_NAME");
                    finish();
                    return;
                }
                if (DeviceUtils.isAuto(this)) {
                    androidXFragment = AutoPermissionAppsFragment.newInstance(permissionName);
                } else if (DeviceUtils.isTelevision(this)) {
                    androidXFragment = com.android.permissioncontroller.permission.ui.television
                            .PermissionAppsFragment.newInstance(permissionName);
                } else {

                    Bundle args = PermissionAppsFragment.createArgs(permissionGroupName, sessionId);
                    args.putString(Intent.EXTRA_PERMISSION_NAME, permissionName);
                    setNavGraph(args, R.id.permission_apps);
                    return;
                }
            } break;

            case ACTION_MANAGE_AUTO_REVOKE: {
                Log.i(LOG_TAG, "sessionId " + sessionId + " starting auto revoke fragment"
                        + " from notification");
                PermissionControllerStatsLog.write(AUTO_REVOKE_NOTIFICATION_CLICKED, sessionId);

                if (DeviceUtils.isWear(this) || DeviceUtils.isAuto(this)
                        || DeviceUtils.isTelevision(this)) {
                    androidXFragment = com.android.permissioncontroller.permission.ui.handheld
                            .AutoRevokeFragment.newInstance();
                    androidXFragment.setArguments(AutoRevokeFragment.createArgs(sessionId));
                } else {
                    setNavGraph(AutoRevokeFragment.createArgs(sessionId), R.id.auto_revoke);
                    return;
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

    private void setNavGraph(Bundle args, int startDestination) {
        setContentView(R.layout.nav_host_fragment);
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        NavInflater inflater = navHost.getNavController().getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph);
        graph.setStartDestination(startDestination);
        navHost.getNavController().setGraph(graph, args);
    }

    @Override
    public ActionBar getActionBar() {
        ActionBar ab = super.getActionBar();
        if (ab != null) {
            ab.setHomeActionContentDescription(R.string.back);
        }
        return ab;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PROXY_ACTIVITY_REQUEST_CODE) {
            setResult(resultCode, data);
            finish();
        }
    }
}
