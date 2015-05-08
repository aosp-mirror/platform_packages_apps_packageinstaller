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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.hardware.camera2.utils.ArrayUtils;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.model.PermissionGroup;

public class GrantPermissionsActivity extends Activity implements
        GrantPermissionViewHandler.OnRequestGrantPermissionGroupResult {
    private static final String LOG_TAG = "GrantPermissionsActivity";

    private static final int PERMISSION_GRANTED = 1;
    private static final int PERMISSION_DENIED = 2;
    private static final int PERMISSION_DENIED_RUNTIME = 3;

    private String[] mRequestedPermissions;
    private int[] mGrantResults;
    private final SparseArray<String> mRequestedRuntimePermissions = new SparseArray<>();

    private ArrayMap<String, GroupState> mRequestGrantPermissionGroups = new ArrayMap<>();

    private final GrantPermissionViewHandler mViewHandler =
            new GrantPermissionViewHandler(this, this);
    private AppPermissions mAppPermissions;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setFinishOnTouchOutside(false);

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

        PackageInfo callingPackageInfo = getCallingPackageInfo();
        if (callingPackageInfo == null) {
            setResultAndFinish();
            return;
        }

        updateDefaultResults(callingPackageInfo);

        mAppPermissions = new AppPermissions(this, callingPackageInfo, mRequestedPermissions);

        for (PermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (!group.areRuntimePermissionsGranted()) {
                mRequestGrantPermissionGroups.put(group.getName(), new GroupState(group));
            }
        }

        if (!showNextPermissionGroupFragment()) {
            setResultAndFinish();
        }
        setContentView(mViewHandler.creatView());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mViewHandler.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mViewHandler.loadSavedInstance(savedInstanceState);
    }

    private boolean showNextPermissionGroupFragment() {
        final int groupCount = mRequestGrantPermissionGroups.size();

        for (int i = 0; i < groupCount; i++) {
            GroupState groupState = mRequestGrantPermissionGroups.valueAt(i);
            if (!groupState.mGroup.areRuntimePermissionsGranted()
                    && groupState.mState == GroupState.STATE_UNKNOWN) {
                CharSequence appLabel = mAppPermissions.getAppLabel();
                SpannableString message = new SpannableString(getString(
                        R.string.permission_warning_template, appLabel,
                        groupState.mGroup.getLabel()));
                // Bold/color the app name.
                int appLabelStart = message.toString().indexOf(appLabel.toString(), 0);
                int appLabelLength = appLabel.length();
                int color = getResources().getColor(R.color.grant_permissions_app_color, null);
                message.setSpan(new ForegroundColorSpan(color), appLabelStart,
                        appLabelStart + appLabelLength, 0);

                // Set the new grant view
                // TODO: Use a real message for the action. We need group action APIs
                Resources resources;
                try {
                    resources = getPackageManager().getResourcesForApplication(
                            groupState.mGroup.getIconPkg());
                } catch (NameNotFoundException e) {
                    // Fallback to system.
                    resources = Resources.getSystem();
                }
                int icon = groupState.mGroup.getIconResId();

                mViewHandler.showPermission(groupState.mGroup.getName(), groupCount, i,
                                Icon.createWithResource(resources, icon), message);
                return  true;
            }
        }

        return false;
    }

    @Override
    public void onRequestGrantPermissionGroupResult(String name, boolean granted) {
        GroupState groupState = mRequestGrantPermissionGroups.get(name);
        if (groupState.mGroup != null) {
            if (granted) {
                groupState.mGroup.grantRuntimePermissions();
                groupState.mState = GroupState.STATE_ALLOWED;
                updateGrantResults(groupState.mGroup);
            } else {
                groupState.mState = GroupState.STATE_DENIED;
            }
        }
        if (!showNextPermissionGroupFragment()) {
            setResultAndFinish();
        }
    }

    private void updateGrantResults(PermissionGroup group) {
        for (Permission permission : group.getPermissions()) {
            if (permission.isGranted()) {
                final int index = ArrayUtils.getArrayIndex(
                        mRequestedPermissions, permission.getName());
                if (index >= 0) {
                    mGrantResults[index] = PackageManager.PERMISSION_GRANTED;
                }
            }
        }
    }

    private int computePermissionGrantState(PackageInfo callingPackageInfo, String permission) {
        boolean permissionRequested = false;

        for (int i = 0; i < callingPackageInfo.requestedPermissions.length; i++) {
            if (permission.equals(callingPackageInfo.requestedPermissions[i])) {
                permissionRequested = true;
                if ((callingPackageInfo.requestedPermissionsFlags[i]
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
            PermissionInfo pInfo = getPackageManager().getPermissionInfo(permission, 0);
            if ((pInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                    != PermissionInfo.PROTECTION_DANGEROUS) {
                return PERMISSION_DENIED;
            }
        } catch (NameNotFoundException e) {
            /* ignore */
        }

        return PERMISSION_DENIED_RUNTIME;
    }

    private PackageInfo getCallingPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(getCallingPackage(),
                    PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + getCallingPackage(), e);
            return null;
        }
    }

    private void updateDefaultResults(PackageInfo callingPackageInfo) {
        final int requestedPermCount = mRequestedPermissions.length;
        for (int i = 0; i < requestedPermCount; i++) {
            String permission = mRequestedPermissions[i];
            final int state = computePermissionGrantState(callingPackageInfo, permission);
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
    }

    private void setResultAndFinish() {
        Intent result = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
        result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, mRequestedPermissions);
        result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, mGrantResults);
        setResult(RESULT_OK, result);
        finish();
    }

    private static final class GroupState {
        public static final int STATE_UNKNOWN = 0;
        public static final int STATE_ALLOWED = 1;
        public static final int STATE_DENIED = 2;

        public final PermissionGroup mGroup;
        public int mState = STATE_UNKNOWN;

        public GroupState(PermissionGroup group) {
            mGroup = group;
        }
    }
}
