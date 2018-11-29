/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.utils.IconDrawableFactory;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * Fragment for the list of default apps.
 */
public class DefaultAppListFragment extends SettingsFragment
        implements Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = DefaultAppListFragment.class.getSimpleName();

    private RoleListViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static DefaultAppListFragment newInstance() {
        return new DefaultAppListFragment();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this, new RoleListViewModel.Factory(true,
                requireActivity().getApplication())).get(RoleListViewModel.class);
        mViewModel.getLiveData().observe(this, this::onRoleListChanged);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.no_default_apps;
    }

    @Override
    protected int getHelpUriResource() {
        return R.string.help_uri_default_apps;
    }

    private void onRoleListChanged(@NonNull List<RoleItem> roleItems) {
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

                preferenceScreen.removePreference(preference);
                oldPreferences.put(preference.getKey(), preference);
            }
        }

        int roleItemsSize = roleItems.size();
        for (int roleItemsIndex = 0; roleItemsIndex < roleItemsSize; roleItemsIndex++) {
            RoleItem roleItem = roleItems.get(roleItemsIndex);

            List<ApplicationInfo> holderApplicationInfos = roleItem.getHolderApplicationInfos();
            if (holderApplicationInfos.isEmpty()) {
                // TODO: Handle Assistant which is visible even without holder.
                continue;
            }

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

            ApplicationInfo holderApplicationInfo = holderApplicationInfos.get(0);
            preference.setIcon(IconDrawableFactory.getBadgedIcon(context, holderApplicationInfo,
                    UserHandle.getUserHandleForUid(holderApplicationInfo.uid)));
            preference.setSummary(Utils.getAppLabel(holderApplicationInfo, context));

            // TODO: Ordering?
            preferenceScreen.addPreference(preference);
        }

        updateState();
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        // TODO
        return true;
    }
}
