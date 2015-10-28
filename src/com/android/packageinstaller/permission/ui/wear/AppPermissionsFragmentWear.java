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

package com.android.packageinstaller.permission.ui.wear;

import android.Manifest;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.ui.OverlayTouchActivity;
import com.android.packageinstaller.permission.ui.wear.settings.PermissionsSettingsAdapter;
import com.android.packageinstaller.permission.ui.wear.settings.SettingsAdapter;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.SafetyNetLogger;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public final class AppPermissionsFragmentWear extends TitledSettingsFragment {

    private static final String LOG_TAG = "ManagePermsFragment";

    private static final int WARNING_CONFIRMATION_REQUEST = 252;
    private List<AppPermissionGroup> mToggledGroups;
    private AppPermissions mAppPermissions;
    private PermissionsSettingsAdapter mAdapter;

    private boolean mHasConfirmedRevoke;

    public static AppPermissionsFragmentWear newInstance(String packageName) {
        return setPackageName(new AppPermissionsFragmentWear(), packageName);
    }

    private static <T extends Fragment> T setPackageName(T fragment, String packageName) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        Activity activity = getActivity();
        PackageManager pm = activity.getPackageManager();
        PackageInfo packageInfo;

        try {
            packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + activity.getCallingPackage(), e);
            packageInfo = null;
        }

        if (packageInfo == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        mAppPermissions = new AppPermissions(activity, packageInfo, null, true, new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        });

        mAdapter = new PermissionsSettingsAdapter(getContext());

        initializePermissionGroupList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppPermissions.refresh();

        // Also refresh the UI
        final int count = mAdapter.getItemCount();
        for (int i = 0; i < count; ++i) {
            updatePermissionGroupSetting(i);
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mAppPermissions != null) {
            initializeLayout(mAdapter);
            bindHeader(mAppPermissions.getPackageInfo());
        }
    }

    private void bindHeader(PackageInfo packageInfo) {
        Activity activity = getActivity();
        PackageManager pm = activity.getPackageManager();
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        CharSequence label = appInfo.loadLabel(pm);
        mHeader.setText(label);
    }

    private void initializePermissionGroupList() {
        final String packageName = mAppPermissions.getPackageInfo().packageName;
        List<AppPermissionGroup> groups = mAppPermissions.getPermissionGroups();
        List<SettingsAdapter.Setting<AppPermissionGroup>> nonSystemGroups = new ArrayList<>();

        final int count = groups.size();
        for (int i = 0; i < count; ++i) {
            final AppPermissionGroup group = groups.get(i);
            if (!Utils.shouldShowPermission(group, packageName)) {
                continue;
            }

            boolean isPlatform = group.getDeclaringPackage().equals(Utils.OS_PKG);

            SettingsAdapter.Setting<AppPermissionGroup> setting =
                    new SettingsAdapter.Setting<AppPermissionGroup>(
                            group.getLabel(),
                            getPermissionGroupIcon(group),
                            i);
            setting.data = group;

            // The UI shows System settings first, then non-system settings
            if (isPlatform) {
                mAdapter.addSetting(setting);
            } else {
                nonSystemGroups.add(setting);
            }
        }

        // Now add the non-system settings to the end of the list
        final int nonSystemCount = nonSystemGroups.size();
        for (int i = 0; i < nonSystemCount; ++i) {
            final SettingsAdapter.Setting<AppPermissionGroup> setting = nonSystemGroups.get(i);
            mAdapter.addSetting(setting);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        logAndClearToggledGroups();
    }

    @Override
    public void onClick(WearableListView.ViewHolder view) {
        final int index = view.getPosition();
        SettingsAdapter.Setting<AppPermissionGroup> setting = mAdapter.get(index);
        final AppPermissionGroup group = setting.data;

        if (group == null) {
            Log.e(LOG_TAG, "Error: AppPermissionGroup is null");
            return;
        }

        // The way WearableListView is designed, there is no way to avoid this click handler
        // Since the policy is fixed, ignore the click as the user is not able to change the state
        // of this permission group
        if (group.isPolicyFixed()) {
            return;
        }

        OverlayTouchActivity activity = (OverlayTouchActivity) getActivity();
        if (activity.isObscuredTouch()) {
            activity.showOverlayDialog();
            return;
        }

        addToggledGroup(group);

        if (LocationUtils.isLocationGroupAndProvider(group.getName(), group.getApp().packageName)) {
            LocationUtils.showLocationDialog(getContext(), mAppPermissions.getAppLabel());
            return;
        }

        if (!group.areRuntimePermissionsGranted()) {
            group.grantRuntimePermissions(false);
        } else {
            final boolean grantedByDefault = group.hasGrantedByDefaultPermission();
            if (grantedByDefault || (!group.hasRuntimePermission() && !mHasConfirmedRevoke)) {
                Intent intent = new Intent(getActivity(), WarningConfirmationActivity.class);
                intent.putExtra(WarningConfirmationActivity.EXTRA_WARNING_MESSAGE,
                        getString(grantedByDefault ?
                                R.string.system_warning : R.string.old_sdk_deny_warning));
                intent.putExtra(WarningConfirmationActivity.EXTRA_INDEX, index);
                startActivityForResult(intent, WARNING_CONFIRMATION_REQUEST);
            } else {
                group.revokeRuntimePermissions(false);
            }
        }

        updatePermissionGroupSetting(index);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == WARNING_CONFIRMATION_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                int index = data.getIntExtra(WarningConfirmationActivity.EXTRA_INDEX, -1);
                if (index == -1) {
                    Log.e(LOG_TAG, "Warning confirmation request came back with no index.");
                    return;
                }

                SettingsAdapter.Setting<AppPermissionGroup> setting = mAdapter.get(index);
                final AppPermissionGroup group = setting.data;
                group.revokeRuntimePermissions(false);
                if (!group.hasGrantedByDefaultPermission()) {
                    mHasConfirmedRevoke = true;
                }

                updatePermissionGroupSetting(index);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void updatePermissionGroupSetting(int index) {
        SettingsAdapter.Setting<AppPermissionGroup> setting = mAdapter.get(index);
        AppPermissionGroup group = setting.data;
        mAdapter.updateSetting(
                index,
                group.getLabel(),
                getPermissionGroupIcon(group),
                group);
    }

    private void addToggledGroup(AppPermissionGroup group) {
        if (mToggledGroups == null) {
            mToggledGroups = new ArrayList<>();
        }
        // Double toggle is back to initial state.
        if (mToggledGroups.contains(group)) {
            mToggledGroups.remove(group);
        } else {
            mToggledGroups.add(group);
        }
    }

    private void logAndClearToggledGroups() {
        if (mToggledGroups != null) {
            String packageName = mAppPermissions.getPackageInfo().packageName;
            SafetyNetLogger.logPermissionsToggled(packageName, mToggledGroups);
            mToggledGroups = null;
        }
    }

    private int getPermissionGroupIcon(AppPermissionGroup group) {
        String groupName = group.getName();
        boolean isEnabled = group.areRuntimePermissionsGranted();
        int resId;

        switch (groupName) {
            case Manifest.permission_group.CALENDAR:
                resId = isEnabled ? R.drawable.ic_permission_calendar
                        : R.drawable.ic_permission_calendardisable;
                break;
            case Manifest.permission_group.CAMERA:
                resId = isEnabled ? R.drawable.ic_permission_camera
                        : R.drawable.ic_permission_cameradisable;
                break;
            case Manifest.permission_group.CONTACTS:
                resId = isEnabled ? R.drawable.ic_permission_contact
                        : R.drawable.ic_permission_contactdisable;
                break;
            case Manifest.permission_group.LOCATION:
                resId = isEnabled ? R.drawable.ic_permission_location
                        : R.drawable.ic_permission_locationdisable;
                break;
            case Manifest.permission_group.MICROPHONE:
                resId = isEnabled ? R.drawable.ic_permission_mic
                        : R.drawable.ic_permission_micdisable;
                break;
            case Manifest.permission_group.PHONE:
                resId = isEnabled ? R.drawable.ic_permission_call
                        : R.drawable.ic_permission_calldisable;
                break;
            case Manifest.permission_group.SENSORS:
                resId = isEnabled ? R.drawable.ic_permission_sensor
                        : R.drawable.ic_permission_sensordisable;
                break;
            case Manifest.permission_group.SMS:
                resId = isEnabled ? R.drawable.ic_permission_sms
                        : R.drawable.ic_permission_smsdisable;
                break;
            case Manifest.permission_group.STORAGE:
                resId = isEnabled ? R.drawable.ic_permission_storage
                        : R.drawable.ic_permission_storagedisable;
                break;
            default:
                resId = isEnabled ? R.drawable.ic_permission_shield
                        : R.drawable.ic_permission_shielddisable;
        }

        return resId;
    }
}
