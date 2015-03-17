/*
** Copyright 2013, The Android Open Source Project
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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.v4.view.ViewPager;
import android.util.Slog;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AppSecurityPermissions;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;

/*
 * The activity which is responsible for asking the user to grant permissions
 * to applications.
 */
public class GrantActivity extends Activity implements OnClickListener {
    private static final String LOG_TAG = "GrantActivity";

    private static final int PERMISSION_GRANTED = 1;
    private static final int PERMISSION_DENIED = 2;
    private static final int PERMISSION_DENIED_RUNTIME = 3;

    private String[] mRequestedPermissions;
    private int[] mGrantResults;
    private final SparseArray<String> mRequestedRuntimePermissions = new SparseArray<>();

    private PackageManager mPm;

    private Button mOk;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPm = getPackageManager();

        mRequestedPermissions = getIntent().getStringArrayExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);
        if (mRequestedPermissions == null) {
            mRequestedPermissions = new String[0];
        }

        mGrantResults = new int[mRequestedPermissions.length];

        final int requestedPermCount = mRequestedPermissions.length;
        if (requestedPermCount == 0) {
            setResultAndFinish();
            return;
        }

        for (int i = 0; i < requestedPermCount; i++) {
            String permission = mRequestedPermissions[i];
            final int state = computePermissionGrantState(permission);
            switch (state) {
                case PERMISSION_GRANTED: {
                    mGrantResults[i] = PackageManager.PERMISSION_GRANTED;
                } break;

                case PERMISSION_DENIED: {
                    mGrantResults[i] = PackageManager.PERMISSION_DENIED;
                } break;

                case PERMISSION_DENIED_RUNTIME: {
                    mGrantResults[i] = PackageManager.PERMISSION_DENIED;
                    mRequestedRuntimePermissions.put(i, permission);
                } break;
            }
        }

        PackageInfo pkgInfo = getUpdatedPackageInfo();
        AppSecurityPermissions perms = new AppSecurityPermissions(this, pkgInfo);

        if (perms.getPermissionCount(AppSecurityPermissions.WHICH_NEW) == 0) {
            setResultAndFinish();
            return;
        }

        setContentView(R.layout.install_start);
        bindUi(pkgInfo, perms);
    }

    @Override
    public void onClick(View v) {
        if (v == mOk) {
            grantRequestedPermissions();
        }
        setResultAndFinish();
    }

    private void bindUi(PackageInfo pkgInfo, AppSecurityPermissions perms) {
        TextView confirmMessage = (TextView)findViewById(R.id.install_confirm_question);
        confirmMessage.setText(R.string.grant_confirm_question);

        PackageUtil.AppSnippet as = new PackageUtil.AppSnippet(
                mPm.getApplicationLabel(pkgInfo.applicationInfo),
                mPm.getApplicationIcon(pkgInfo.applicationInfo));
        PackageUtil.initSnippetForNewApp(this, as, R.id.app_snippet);

        mOk = (Button) findViewById(R.id.ok_button);
        mOk.setText(R.string.ok);
        mOk.setOnClickListener(this);

        Button cancel = (Button) findViewById(R.id.cancel_button);
        cancel.setOnClickListener(this);

        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        tabHost.setup();

        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        TabsAdapter adapter = new TabsAdapter(this, tabHost, viewPager);

        View newTab = perms.getPermissionsView(AppSecurityPermissions.WHICH_NEW);
        adapter.addTab(tabHost.newTabSpec("new").setIndicator(
                getText(R.string.newPerms)), newTab);

        View allTab = getPermissionList(perms);
        adapter.addTab(tabHost.newTabSpec("all").setIndicator(
                getText(R.string.allPerms)), allTab);
    }

    /**
     * Returns a PackageInfo object representing the results of adding all the permissions
     * in {@code requested_permissions} to {@code mRequestingPackage}. This is the package
     * permissions the user will have if they accept the grant request.
     */
    private PackageInfo getUpdatedPackageInfo() {
        try {
            PackageInfo pkgInfo = mPm.getPackageInfo(getCallingPackage(),
                    PackageManager.GET_PERMISSIONS);
            for (int i = 0; i < pkgInfo.requestedPermissions.length; i++) {
                String requestedPerm = pkgInfo.requestedPermissions[i];
                final int notGrantedCount = mRequestedRuntimePermissions.size();
                for (int j = 0; j < notGrantedCount; j++) {
                    String notGrantedPerm = mRequestedRuntimePermissions.get(j);
                    if (requestedPerm.equals(notGrantedPerm)) {
                        pkgInfo.requestedPermissionsFlags[i]
                                |= PackageInfo.REQUESTED_PERMISSION_GRANTED;
                    }
                }
            }
            return pkgInfo;
        } catch (NameNotFoundException e) {
            throw new RuntimeException(e); // will never occur
        }
    }

    private View getPermissionList(AppSecurityPermissions perms) {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View root = inflater.inflate(R.layout.permissions_list, null);
        View personalPermissions = perms.getPermissionsView(AppSecurityPermissions.WHICH_PERSONAL);
        View devicePermissions = perms.getPermissionsView(AppSecurityPermissions.WHICH_DEVICE);

        ((ViewGroup)root.findViewById(R.id.privacylist)).addView(personalPermissions);
        ((ViewGroup)root.findViewById(R.id.devicelist)).addView(devicePermissions);

        return root;
    }

    private int computePermissionGrantState(String permission) {
        final PackageInfo pkgInfo;
        try {
            pkgInfo = getPackageManager().getPackageInfo(getCallingPackage(),
                    PackageManager.GET_PERMISSIONS);
            if (pkgInfo.requestedPermissions == null) {
                return PERMISSION_DENIED;
            }
        } catch (NameNotFoundException e) {
            Slog.i(LOG_TAG, "No such permission:" + permission, e);
            return PERMISSION_DENIED;
        }

        boolean permissionRequested = false;

        for (int i = 0; i < pkgInfo.requestedPermissions.length; i++) {
            if (permission.equals(pkgInfo.requestedPermissions[i])) {
                permissionRequested = true;
                if ((pkgInfo.requestedPermissionsFlags[i]
                        & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    return PERMISSION_GRANTED;
                }
                break;
            }
        }

        if (!permissionRequested) {
            return PERMISSION_DENIED;
        }

        try {
            PermissionInfo pInfo = mPm.getPermissionInfo(permission, 0);
            if ((pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                    != PermissionInfo.PROTECTION_DANGEROUS) {
                return PERMISSION_DENIED;
            }
        } catch (NameNotFoundException e) {
            /* ignore */
        }

        return PERMISSION_DENIED_RUNTIME;
    }

    private void grantRequestedPermissions() {
        final int requestedPermCount = mRequestedRuntimePermissions.size();
        for (int i = 0; i < requestedPermCount; i++) {
            String permission = mRequestedRuntimePermissions.valueAt(i);
            mPm.grantPermission(getCallingPackage(), permission, new UserHandle(getUserId()));
            final int index = mRequestedRuntimePermissions.keyAt(i);
            mGrantResults[index] = PackageManager.PERMISSION_GRANTED;
        }
    }

    private void setResultAndFinish() {
        Intent result = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
        result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, mRequestedPermissions);
        result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, mGrantResults);
        setResult(RESULT_OK, result);
        finish();
    }
}
