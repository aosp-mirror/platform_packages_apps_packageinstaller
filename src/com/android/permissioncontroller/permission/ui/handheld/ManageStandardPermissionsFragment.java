/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.permissioncontroller.permission.ui.handheld;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.permission.debug.UtilsKt.shouldShowPermissionsDashboard;
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.permission.ui.model.ManageStandardPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.ManageStandardPermissionsViewModelFactory;
import com.android.permissioncontroller.permission.utils.Utils;

/**
 * Fragment that allows the user to manage standard permissions.
 */
public final class ManageStandardPermissionsFragment extends ManagePermissionsFragment {
    private static final String EXTRA_PREFS_KEY = "extra_prefs_key";
    private static final String AUTO_REVOKE_KEY = "auto_revoke_key";
    private static final String LOG_TAG = ManageStandardPermissionsFragment.class.getSimpleName();

    private static final int MENU_PERMISSION_USAGE = MENU_HIDE_SYSTEM + 1;

    private ManageStandardPermissionsViewModel mViewModel;

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param sessionId The current session ID
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        return arguments;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ManageStandardPermissionsViewModelFactory factory =
                new ManageStandardPermissionsViewModelFactory(getActivity().getApplication());
        mViewModel = new ViewModelProvider(this, factory)
                .get(ManageStandardPermissionsViewModel.class);
        mPermissionGroups = mViewModel.getUiDataLiveData().getValue();

        mViewModel.getUiDataLiveData().observe(this, permissionGroups -> {
            if (permissionGroups != null) {
                mPermissionGroups = permissionGroups;
                updatePermissionsUi();
            } else {
                Log.e(LOG_TAG, "ViewModel returned null data, exiting");
                getActivity().finish();
            }
        });

        mViewModel.getNumCustomPermGroups().observe(this, permNames -> updatePermissionsUi());
        mViewModel.getNumAutoRevoked().observe(this, show -> updatePermissionsUi());
    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().setTitle(com.android.permissioncontroller.R.string.app_permission_manager);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                pressBack(this);
                return true;
            case MENU_PERMISSION_USAGE:
                getActivity().startActivity(new Intent(Intent.ACTION_REVIEW_PERMISSION_USAGE)
                        .setClass(getContext(), ManagePermissionsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (shouldShowPermissionsDashboard()) {
            menu.add(Menu.NONE, MENU_PERMISSION_USAGE, Menu.NONE, R.string.permission_usage_title);
        }
    }

    @Override
    protected PreferenceScreen updatePermissionsUi() {
        PreferenceScreen screen = super.updatePermissionsUi();
        if (screen == null) {
            return null;
        }

        // Check if we need an additional permissions preference
        int numExtraPermissions = 0;
        if (mViewModel.getNumCustomPermGroups().getValue() != null) {
            numExtraPermissions = mViewModel.getNumCustomPermGroups().getValue();
        }

        Preference additionalPermissionsPreference = screen.findPreference(EXTRA_PREFS_KEY);
        if (numExtraPermissions == 0) {
            if (additionalPermissionsPreference != null) {
                screen.removePreference(additionalPermissionsPreference);
            }
        } else {
            if (additionalPermissionsPreference == null) {
                additionalPermissionsPreference = new FixedSizeIconPreference(
                        getPreferenceManager().getContext());
                additionalPermissionsPreference.setKey(EXTRA_PREFS_KEY);
                additionalPermissionsPreference.setIcon(Utils.applyTint(getActivity(),
                        R.drawable.ic_more_items,
                        android.R.attr.colorControlNormal));
                additionalPermissionsPreference.setTitle(R.string.additional_permissions);
                additionalPermissionsPreference.setOnPreferenceClickListener(preference -> {
                    mViewModel.showCustomPermissions(this,
                            ManageCustomPermissionsFragment.createArgs(
                                    getArguments().getLong(EXTRA_SESSION_ID)));
                    return true;
                });

                screen.addPreference(additionalPermissionsPreference);
            }

            additionalPermissionsPreference.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, numExtraPermissions,
                    numExtraPermissions));
        }

        Integer numAutoRevoked = mViewModel.getNumAutoRevoked().getValue();

        Preference autoRevokePreference = screen.findPreference(AUTO_REVOKE_KEY);
        if (numAutoRevoked != null && numAutoRevoked != 0) {
            if (autoRevokePreference == null) {
                autoRevokePreference = new FixedSizeIconPreference(
                        getPreferenceManager().getContext(), true, true);
                autoRevokePreference.setOrder(-1);
                autoRevokePreference.setKey(AUTO_REVOKE_KEY);
                autoRevokePreference.setSingleLineTitle(false);
                autoRevokePreference.setIcon(R.drawable.ic_info_outline_accent);
                autoRevokePreference.setTitle(
                            R.string.auto_revoke_permission_notification_title);
                autoRevokePreference.setSummary(
                        R.string.auto_revoke_setting_subtitle);
                autoRevokePreference.setOnPreferenceClickListener(preference -> {
                    mViewModel.showAutoRevoke(this, AutoRevokeFragment.createArgs(
                            getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID)));
                    return true;
                });

                screen.addPreference(autoRevokePreference);
            }
        } else if (numAutoRevoked != null && autoRevokePreference != null) {
            screen.removePreference(autoRevokePreference);
        }

        return screen;
    }

    @Override
    public void showPermissionApps(String permissionGroupName) {
        mViewModel.showPermissionApps(this, PermissionAppsFragment.createArgs(
                permissionGroupName, getArguments().getLong(EXTRA_SESSION_ID)));
    }
}
