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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;

import java.util.List;

/**
 * Child fragment for the list of special app accesses. Must be added as a child fragment and its
 * parent fragment must be a {@link PreferenceFragmentCompat} which implements {@link Parent}.
 *
 * @param <PF> type of the parent fragment
 */
public class SpecialAppAccessListChildFragment<PF extends PreferenceFragmentCompat
        & SpecialAppAccessListChildFragment.Parent> extends Fragment
        implements Preference.OnPreferenceClickListener {

    private SpecialAppAccessListViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static SpecialAppAccessListChildFragment newInstance() {
        return new SpecialAppAccessListChildFragment();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this).get(SpecialAppAccessListViewModel.class);
        mViewModel.getLiveData().observe(this, roleItems -> onRoleListChanged());
    }

    private void onRoleListChanged() {
        List<RoleItem> roleItems = mViewModel.getLiveData().getValue();
        if (roleItems == null) {
            return;
        }

        PF preferenceFragment = requirePreferenceFragment();
        PreferenceManager preferenceManager = preferenceFragment.getPreferenceManager();
        Context context = preferenceManager.getContext();
        PreferenceScreen preferenceScreen = preferenceFragment.getPreferenceScreen();
        ArrayMap<String, Preference> oldPreferences = new ArrayMap<>();
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context);
            preferenceFragment.setPreferenceScreen(preferenceScreen);
        } else {
            for (int i = preferenceScreen.getPreferenceCount() - 1; i >= 0; --i) {
                Preference preference = preferenceScreen.getPreference(i);

                preferenceScreen.removePreference(preference);
                preference.setOrder(Preference.DEFAULT_ORDER);
                oldPreferences.put(preference.getKey(), preference);
            }
        }

        int roleItemsSize = roleItems.size();
        for (int i = 0; i < roleItemsSize; i++) {
            RoleItem roleItem = roleItems.get(i);

            Role role = roleItem.getRole();
            TwoTargetPreference preference = (TwoTargetPreference) oldPreferences.get(
                    role.getName());
            if (preference == null) {
                preference = preferenceFragment.createPreference(context);
                preference.setKey(role.getName());
                preference.setIconSpaceReserved(true);
                preference.setTitle(role.getShortLabelResource());
                preference.setPersistent(false);
                preference.setOnPreferenceClickListener(this);
            }

            role.preparePreferenceAsUser(preference, Process.myUserHandle(), context);

            preferenceScreen.addPreference(preference);
        }

        preferenceFragment.onPreferenceScreenChanged();
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String roleName = preference.getKey();
        Context context = requireContext();
        Role role = Roles.get(context).get(roleName);
        UserHandle user = Process.myUserHandle();
        Intent intent = role.getManageIntentAsUser(user, context);
        if (intent == null) {
            intent = SpecialAppAccessActivity.createIntent(roleName, context);
        }
        startActivity(intent);
        return true;
    }

    @NonNull
    private PF requirePreferenceFragment() {
        //noinspection unchecked
        return (PF) requireParentFragment();
    }

    /**
     * Interface that the parent fragment must implement.
     */
    public interface Parent {

        /**
         * Create a new preference for a special app access.
         *
         * @param context the {@code Context} to use when creating the preference.
         *
         * @return a new preference for a special app access
         */
        @NonNull
        TwoTargetPreference createPreference(@NonNull Context context);

        /**
         * Callback when changes have been made to the {@link PreferenceScreen} of the parent
         * {@link PreferenceFragmentCompat}.
         */
        void onPreferenceScreenChanged();
    }
}
