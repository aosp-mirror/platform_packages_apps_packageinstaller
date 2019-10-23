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
package com.android.packageinstaller.permission.ui.handheld;

import static com.android.packageinstaller.Constants.EXTRA_SESSION_ID;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;


/**
 * Fragment that allows the user to manage standard permissions.
 */
public final class ManageStandardPermissionsFragment extends ManagePermissionsFragment {
    private static final String EXTRA_PREFS_KEY = "extra_prefs_key";
    private static final String LOG_TAG = "ManageStandardPermissionsFragment";

    private ManageStandardPermissionsViewModel mViewModel;

    /**
     * @return A new fragment
     */
    public static ManageStandardPermissionsFragment newInstance(long sessionId) {
        ManageStandardPermissionsFragment fragment = new ManageStandardPermissionsFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
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
    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().setTitle(com.android.permissioncontroller.R.string.app_permission_manager);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                additionalPermissionsPreference = new Preference(
                        getPreferenceManager().getContext());
                additionalPermissionsPreference.setKey(EXTRA_PREFS_KEY);
                additionalPermissionsPreference.setIcon(Utils.applyTint(getActivity(),
                        R.drawable.ic_more_items,
                        android.R.attr.colorControlNormal));
                additionalPermissionsPreference.setTitle(R.string.additional_permissions);
                additionalPermissionsPreference.setOnPreferenceClickListener(preference -> {
                    long sessionId = getArguments().getLong(EXTRA_SESSION_ID);
                    ManageCustomPermissionsFragment frag =
                            ManageCustomPermissionsFragment.newInstance(sessionId);
                    frag.setTargetFragment(ManageStandardPermissionsFragment.this, 0);
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(android.R.id.content, frag);
                    ft.addToBackStack(null);
                    ft.commit();
                    return true;
                });

                screen.addPreference(additionalPermissionsPreference);
            }

            additionalPermissionsPreference.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, numExtraPermissions,
                    numExtraPermissions));
        }
        return screen;
    }
}
