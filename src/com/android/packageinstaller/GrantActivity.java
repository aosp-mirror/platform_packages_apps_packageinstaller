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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AppSecurityPermissions;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/*
 * The activity which is responsible for asking the user to grant permissions
 * to applications.
 */
public class GrantActivity extends Activity implements OnClickListener {
    private Button mOk;
    private Button mCancel;
    private PackageManager mPm;
    private String mRequestingPackage;
    private String[] requested_permissions;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mPm = getPackageManager();
        mRequestingPackage = this.getCallingPackage();

        requested_permissions = getRequestedPermissions();
        if (requested_permissions.length == 0) {
            // The grant request was empty. Return success
            setResult(RESULT_OK);
            finish();
            return;
        }

        PackageInfo pkgInfo = getUpdatedPackageInfo();
        AppSecurityPermissions perms = new AppSecurityPermissions(this, pkgInfo);
        if (perms.getPermissionCount(AppSecurityPermissions.WHICH_NEW) == 0) {
            // The updated permissions dialog said there are no new permissions.
            // This should never occur if requested_permissions.length > 0,
            // but we check for it anyway, just in case.
            setResult(RESULT_OK);
            finish();
            return;
        }

        setContentView(R.layout.install_start);
        ((TextView)findViewById(R.id.install_confirm_question)).setText(R.string.grant_confirm_question);
        PackageUtil.AppSnippet as = new PackageUtil.AppSnippet(mPm.getApplicationLabel(pkgInfo.applicationInfo),
                mPm.getApplicationIcon(pkgInfo.applicationInfo));
        PackageUtil.initSnippetForNewApp(this, as, R.id.app_snippet);
        mOk = (Button)findViewById(R.id.ok_button);
        mOk.setText(R.string.ok);
        mCancel = (Button)findViewById(R.id.cancel_button);
        mOk.setOnClickListener(this);
        mCancel.setOnClickListener(this);

        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        tabHost.setup();
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        TabsAdapter adapter = new TabsAdapter(this, tabHost, viewPager);

        View newTab = perms.getPermissionsView(AppSecurityPermissions.WHICH_NEW);
        View allTab = getPermissionList(perms);

        adapter.addTab(tabHost.newTabSpec("new").setIndicator(
                getText(R.string.newPerms)), newTab);
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
            PackageInfo pkgInfo = mPm.getPackageInfo(mRequestingPackage, PackageManager.GET_PERMISSIONS);
            for (int i = 0; i < pkgInfo.requestedPermissions.length; i++) {
                for (String requested_permission : requested_permissions) {
                    if (requested_permission.equals(pkgInfo.requestedPermissions[i])) {
                        pkgInfo.requestedPermissionsFlags[i] |= PackageInfo.REQUESTED_PERMISSION_GRANTED;
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

    /**
     * Return an array of permissions requested by the caller, filtered to exclude
     * irrelevant or otherwise malicious permission requests from untrusted callers.
     */
    private String[] getRequestedPermissions() {
        String[] permissions = getIntent()
                .getStringArrayExtra(PackageManager.EXTRA_REQUEST_PERMISSION_PERMISSION_LIST);
        if (permissions == null) {
            return new String[0];
        }
        permissions = keepNormalDangerousPermissions(permissions);
        permissions = keepRequestingPackagePermissions(permissions);
        return permissions;

    }

    /**
     * Remove any permissions in {@code permissions} which are not present
     * in {@code mRequestingPackage} and return the result. We also filter out
     * permissions which are required by {@code mRequestingPackage}, and permissions
     * which have already been granted to {@code mRequestingPackage}, as those permissions
     * are useless to change.
     */
    private String[] keepRequestingPackagePermissions(String[] permissions) {
        List<String> result = new ArrayList<String>();
        try {
            PackageInfo pkgInfo = mPm.getPackageInfo(mRequestingPackage, PackageManager.GET_PERMISSIONS);
            if (pkgInfo.requestedPermissions == null) {
                return new String[0];
            }
            for (int i = 0; i < pkgInfo.requestedPermissions.length; i++) {
                for (String permission : permissions) {
                    final boolean isRequired =
                            ((pkgInfo.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_REQUIRED) != 0);
                    final boolean isGranted =
                            ((pkgInfo.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0);

                    /*
                     * We ignore required permissions, and permissions which have already
                     * been granted, as it's useless to grant those permissions.
                     */
                    if (permission.equals(pkgInfo.requestedPermissions[i])
                            && !isRequired && !isGranted) {
                        result.add(permission);
                        break;
                    }
                }
            }
        } catch (NameNotFoundException e) {
            throw new RuntimeException(e); // should never happen
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * Filter the permissions in {@code permissions}, keeping only the NORMAL or DANGEROUS
     * permissions.
     *
     * @param permissions the permissions to filter
     * @return A subset of {@code permissions} with only the
     *     NORMAL or DANGEROUS permissions kept
     */
    private String[] keepNormalDangerousPermissions(String[] permissions) {
        List<String> result = new ArrayList<String>();
        for (String permission : permissions) {
            try {
                PermissionInfo pInfo = mPm.getPermissionInfo(permission, 0);
                final int base = pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
                if ((base != PermissionInfo.PROTECTION_NORMAL)
                        && (base != PermissionInfo.PROTECTION_DANGEROUS)) {
                    continue;
                }
                result.add(permission);
            } catch (NameNotFoundException e) {
                // ignore
            }
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public void onClick(View v) {
        if (v == mOk) {
            for (String permission : requested_permissions) {
                mPm.grantPermission(mRequestingPackage, permission);
            }
            setResult(RESULT_OK);
        }
        if (v == mCancel) {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}
