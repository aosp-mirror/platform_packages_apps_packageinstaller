/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.role.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * Fragment for the list of special app accesses.
 */
public class SpecialAppAccessListFragment extends SettingsFragment
        implements Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = SpecialAppAccessListFragment.class.getSimpleName();

    private SpecialAppAccessListViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static SpecialAppAccessListFragment newInstance() {
        return new SpecialAppAccessListFragment();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this).get(SpecialAppAccessListViewModel.class);
        mViewModel.getLiveData().observe(this, roleItems -> onRoleListChanged());
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.no_special_app_access;
    }

    @Override
    protected int getHelpUriResource() {
        return R.string.help_uri_special_app_access;
    }

    private void onRoleListChanged() {
        List<RoleItem> roleItems = mViewModel.getLiveData().getValue();
        if (roleItems == null) {
            return;
        }

        PreferenceManager preferenceManager = getPreferenceManager();
        Context context = preferenceManager.getContext();
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        ArrayMap<String, Preference> oldPreferences = new ArrayMap<>();
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context);
            setPreferenceScreen(preferenceScreen);
        } else {
            for (int i = preferenceScreen.getPreferenceCount() - 1; i >= 0; --i) {
                Preference preference = preferenceScreen.getPreference(i);

                oldPreferences.put(preference.getKey(), preference);
            }
        }

        int roleItemsSize = roleItems.size();
        for (int i = 0; i < roleItemsSize; i++) {
            RoleItem roleItem = roleItems.get(i);

            Role role = roleItem.getRole();
            Preference preference = oldPreferences.get(role.getName());
            if (preference == null) {
                preference = new AppIconPreference(context);
                preference.setKey(role.getName());
                preference.setIconSpaceReserved(true);
                preference.setTitle(role.getLabelResource());
                preference.setPersistent(false);
                preference.setOnPreferenceClickListener(this);
            }

            preferenceScreen.addPreference(preference);
        }

        updateState();
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String roleName = preference.getKey();
        Context context = requireContext();
        Role role = Roles.get(context).get(roleName);
        UserHandle user = Process.myUserHandle();
        Intent intent = role.getManageIntentAsUser(user, context);
        if (intent == null) {
            intent = SpecialAppAccessActivity.createIntent(roleName, requireContext());
        }
        startActivity(intent);
        return true;
    }
}
