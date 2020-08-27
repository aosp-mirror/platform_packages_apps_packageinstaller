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
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.permission.ui.model.ManageCustomPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.ManageCustomPermissionsViewModelFactory;

import java.util.HashMap;

/**
 * Fragment that allows the user to manage custom permissions.
 */
public class ManageCustomPermissionsFragment extends ManagePermissionsFragment {

    private ManageCustomPermissionsViewModel mViewModel;

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

        ManageCustomPermissionsViewModelFactory factory =
                new ManageCustomPermissionsViewModelFactory(getActivity().getApplication());
        mViewModel = new ViewModelProvider(this, factory)
                .get(ManageCustomPermissionsViewModel.class);
        mPermissionGroups = mViewModel.getUiDataLiveData().getValue();

        mViewModel.getUiDataLiveData().observe(this, permissionGroups -> {
            if (permissionGroups == null) {
                mPermissionGroups = new HashMap<>();
            } else {
                mPermissionGroups = permissionGroups;
            }
            updatePermissionsUi();
        });
    }

    @Override
    public void showPermissionApps(String permissionGroupName) {
        mViewModel.showPermissionApps(this, PermissionAppsFragment.createArgs(
                permissionGroupName, getArguments().getLong(EXTRA_SESSION_ID)));
    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().setTitle(com.android.permissioncontroller.R.string.additional_permissions);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                pressBack(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
