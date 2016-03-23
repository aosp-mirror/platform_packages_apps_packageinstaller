/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package com.android.packageinstaller;

import android.app.Activity;
import android.app.admin.IDevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageDeleteObserver2;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * This activity corresponds to a download progress screen that is displayed 
 * when an application is uninstalled. The result of the application uninstall
 * is indicated in the result code that gets set to 0 or 1. The application gets launched
 * by an intent with the intent's class name explicitly set to UninstallAppProgress and expects
 * the application object of the application to uninstall.
 */
public class UninstallAppProgress extends Activity implements OnClickListener {
    private final String TAG="UninstallAppProgress";

    private ApplicationInfo mAppInfo;
    private boolean mAllUsers;
    private UserHandle mUser;
    private IBinder mCallback;

    private TextView mStatusTextView;
    private Button mOkButton;
    private Button mDeviceManagerButton;
    private Button mUsersButton;
    private ProgressBar mProgressBar;
    private View mOkPanel;
    private volatile int mResultCode = -1;

    private static final int UNINSTALL_COMPLETE = 1;

    private boolean isProfileOfOrSame(UserManager userManager, int userId, int profileId) {
        if (userId == profileId) {
            return true;
        }
        UserInfo parentUser = userManager.getProfileParent(profileId);
        return parentUser != null && parentUser.id == userId;
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UNINSTALL_COMPLETE:
                    mResultCode = msg.arg1;
                    final String packageName = (String) msg.obj;

                    if (mCallback != null) {
                        final IPackageDeleteObserver2 observer = IPackageDeleteObserver2.Stub
                                .asInterface(mCallback);
                        try {
                            observer.onPackageDeleted(mAppInfo.packageName, mResultCode,
                                    packageName);
                        } catch (RemoteException ignored) {
                        }
                        finish();
                        return;
                    }

                    if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) {
                        Intent result = new Intent();
                        result.putExtra(Intent.EXTRA_INSTALL_RESULT, mResultCode);
                        setResult(mResultCode == PackageManager.DELETE_SUCCEEDED
                                ? Activity.RESULT_OK : Activity.RESULT_FIRST_USER,
                                        result);
                        finish();
                        return;
                    }

                    // Update the status text
                    final String statusText;
                    switch (msg.arg1) {
                        case PackageManager.DELETE_SUCCEEDED:
                            statusText = getString(R.string.uninstall_done);
                            // Show a Toast and finish the activity
                            Context ctx = getBaseContext();
                            Toast.makeText(ctx, statusText, Toast.LENGTH_LONG).show();
                            setResultAndFinish(mResultCode);
                            return;
                        case PackageManager.DELETE_FAILED_DEVICE_POLICY_MANAGER: {
                            UserManager userManager =
                                    (UserManager) getSystemService(Context.USER_SERVICE);
                            IDevicePolicyManager dpm = IDevicePolicyManager.Stub.asInterface(
                                    ServiceManager.getService(Context.DEVICE_POLICY_SERVICE));
                            // Find out if the package is an active admin for some non-current user.
                            int myUserId = UserHandle.myUserId();
                            UserInfo otherBlockingUser = null;
                            for (UserInfo user : userManager.getUsers()) {
                                // We only catch the case when the user in question is neither the
                                // current user nor its profile.
                                if (isProfileOfOrSame(userManager, myUserId, user.id)) continue;

                                try {
                                    if (dpm.packageHasActiveAdmins(packageName, user.id)) {
                                        otherBlockingUser = user;
                                        break;
                                    }
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Failed to talk to package manager", e);
                                }
                            }
                            if (otherBlockingUser == null) {
                                Log.d(TAG, "Uninstall failed because " + packageName
                                        + " is a device admin");
                                mDeviceManagerButton.setVisibility(View.VISIBLE);
                                statusText = getString(
                                        R.string.uninstall_failed_device_policy_manager);
                            } else {
                                Log.d(TAG, "Uninstall failed because " + packageName
                                        + " is a device admin of user " + otherBlockingUser);
                                mDeviceManagerButton.setVisibility(View.GONE);
                                statusText = String.format(
                                        getString(R.string.uninstall_failed_device_policy_manager_of_user),
                                        otherBlockingUser.name);
                            }
                            break;
                        }
                        case PackageManager.DELETE_FAILED_OWNER_BLOCKED: {
                            UserManager userManager =
                                    (UserManager) getSystemService(Context.USER_SERVICE);
                            IPackageManager packageManager = IPackageManager.Stub.asInterface(
                                    ServiceManager.getService("package"));
                            List<UserInfo> users = userManager.getUsers();
                            int blockingUserId = UserHandle.USER_NULL;
                            for (int i = 0; i < users.size(); ++i) {
                                final UserInfo user = users.get(i);
                                try {
                                    if (packageManager.getBlockUninstallForUser(packageName,
                                            user.id)) {
                                        blockingUserId = user.id;
                                        break;
                                    }
                                } catch (RemoteException e) {
                                    // Shouldn't happen.
                                    Log.e(TAG, "Failed to talk to package manager", e);
                                }
                            }
                            int myUserId = UserHandle.myUserId();
                            if (isProfileOfOrSame(userManager, myUserId, blockingUserId)) {
                                mDeviceManagerButton.setVisibility(View.VISIBLE);
                            } else {
                                mDeviceManagerButton.setVisibility(View.GONE);
                                mUsersButton.setVisibility(View.VISIBLE);
                            }
                            // TODO: b/25442806
                            if (blockingUserId == UserHandle.USER_SYSTEM) {
                                statusText = getString(R.string.uninstall_blocked_device_owner);
                            } else if (blockingUserId == UserHandle.USER_NULL) {
                                Log.d(TAG, "Uninstall failed for " + packageName + " with code "
                                        + msg.arg1 + " no blocking user");
                                statusText = getString(R.string.uninstall_failed);
                            } else {
                                statusText = getString(R.string.uninstall_blocked_profile_owner);
                            }
                            break;
                        }
                        default:
                            Log.d(TAG, "Uninstall failed for " + packageName + " with code "
                                    + msg.arg1);
                            statusText = getString(R.string.uninstall_failed);
                            break;
                    }
                    mStatusTextView.setText(statusText);

                    // Hide the progress bar; Show the ok button
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mOkPanel.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        mAppInfo = intent.getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
        mAllUsers = intent.getBooleanExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, false);
        if (mAllUsers && !UserManager.get(this).isAdminUser()) {
            throw new SecurityException("Only admin user can request uninstall for all users");
        }
        mUser = intent.getParcelableExtra(Intent.EXTRA_USER);
        if (mUser == null) {
            mUser = android.os.Process.myUserHandle();
        } else {
            UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
            List<UserHandle> profiles = userManager.getUserProfiles();
            if (!profiles.contains(mUser)) {
                throw new SecurityException("User " + android.os.Process.myUserHandle() + " can't "
                        + "request uninstall for user " + mUser);
            }
        }
        mCallback = intent.getIBinderExtra(PackageInstaller.EXTRA_CALLBACK);
        initView();
    }
    
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        public void packageDeleted(String packageName, int returnCode) {
            Message msg = mHandler.obtainMessage(UNINSTALL_COMPLETE);
            msg.arg1 = returnCode;
            msg.obj = packageName;
            mHandler.sendMessage(msg);
        }
    }
    
    void setResultAndFinish(int retCode) {
        setResult(retCode);
        finish();
    }
    
    public void initView() {
        boolean isUpdate = ((mAppInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
        setTitle(isUpdate ? R.string.uninstall_update_title : R.string.uninstall_application_title);

        setContentView(R.layout.uninstall_progress);
        // Initialize views
        View snippetView = findViewById(R.id.app_snippet);
        PackageUtil.initSnippetForInstalledApp(this, mAppInfo, snippetView);
        mStatusTextView = (TextView) findViewById(R.id.center_text);
        mStatusTextView.setText(R.string.uninstalling);
        mDeviceManagerButton = (Button) findViewById(R.id.device_manager_button);
        mUsersButton = (Button) findViewById(R.id.users_button);
        mDeviceManagerButton.setVisibility(View.GONE);
        mDeviceManagerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings",
                        "com.android.settings.Settings$DeviceAdminSettingsActivity");
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
        mUsersButton.setVisibility(View.GONE);
        mUsersButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_USER_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressBar.setIndeterminate(true);
        // Hide button till progress is being displayed
        mOkPanel = (View) findViewById(R.id.ok_panel);
        mOkButton = (Button) findViewById(R.id.ok_button);
        mOkButton.setOnClickListener(this);
        mOkPanel.setVisibility(View.INVISIBLE);
        IPackageManager packageManager =
                IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        PackageDeleteObserver observer = new PackageDeleteObserver();
        try {
            packageManager.deletePackageAsUser(mAppInfo.packageName, observer,
                    mUser.getIdentifier(),
                    mAllUsers ? PackageManager.DELETE_ALL_USERS : 0);
        } catch (RemoteException e) {
            // Shouldn't happen.
            Log.e(TAG, "Failed to talk to package manager", e);
        }
    }

    public void onClick(View v) {
        if(v == mOkButton) {
            Log.i(TAG, "Finished uninstalling pkg: " + mAppInfo.packageName);
            setResultAndFinish(mResultCode);
        }
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (mResultCode == -1) {
                // Ignore back key when installation is in progress
                return true;
            } else {
                // If installation is done, just set the result code
                setResult(mResultCode);
            }
        }
        return super.dispatchKeyEvent(ev);
    }
}
