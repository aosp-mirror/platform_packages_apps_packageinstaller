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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.packageinstaller.permission.utils.IconDrawableFactory;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * Fragment for a default app.
 */
public class DefaultAppFragment extends SettingsFragment
        implements Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = DefaultAppFragment.class.getSimpleName();

    public static final String EXTRA_ROLE_NAME =
            "com.android.packageinstaller.role.ui.extra.ROLE_NAME";

    private String mRoleName;

    private UserHandle mUser;

    private Role mRole;

    private DefaultAppViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the role for the default app
     * @param user the user for the default app
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static DefaultAppFragment newInstance(@NonNull String roleName,
            @NonNull UserHandle user) {
        DefaultAppFragment fragment = new DefaultAppFragment();
        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_ROLE_NAME, roleName);
        arguments.putParcelable(Intent.EXTRA_USER, user);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mRoleName = arguments.getString(EXTRA_ROLE_NAME);
        mUser = arguments.getParcelable(Intent.EXTRA_USER);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = requireActivity();
        mRole = Roles.getRoles(activity).get(mRoleName);
        activity.setTitle(mRole.getLabelResource());

        mViewModel = ViewModelProviders.of(this, new DefaultAppViewModel.Factory(mRole, mUser,
                activity.getApplication())).get(DefaultAppViewModel.class);
        mViewModel.getRoleLiveData().observe(this, this::onRoleInfoChanged);
        mViewModel.getAddRoleHolderStateLiveData().observe(this, this::onAddRoleHolderStateChanged);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.default_app_no_apps;
    }

    private void onRoleInfoChanged(@NonNull RoleInfo roleInfo) {
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

        List<ApplicationInfo> qualifyingApplicationInfos = roleInfo.getQualifyingApplicationInfos();
        List<String> holderPackageNames = roleInfo.getHolderPackageNames();
        int qualifyingApplicationInfosSize = qualifyingApplicationInfos.size();
        for (int i = 0; i < qualifyingApplicationInfosSize; i++) {
            ApplicationInfo qualifyingApplicationInfo = qualifyingApplicationInfos.get(i);

            TwoStatePreference preference = (TwoStatePreference) oldPreferences.get(
                    qualifyingApplicationInfo.packageName);
            if (preference == null) {
                // TODO: STOPSHIP: Support multiple role holders.
                preference = new AppIconRadioButtonPreference(context);
                preference.setKey(qualifyingApplicationInfo.packageName);
                preference.setIcon(IconDrawableFactory.getBadgedIcon(context,
                        qualifyingApplicationInfo, UserHandle.getUserHandleForUid(
                                qualifyingApplicationInfo.uid)));
                preference.setTitle(Utils.getAppLabel(qualifyingApplicationInfo, context));
                preference.setPersistent(false);
                preference.setOnPreferenceClickListener(this);
            }

            preference.setChecked(holderPackageNames.contains(
                    qualifyingApplicationInfo.packageName));

            // TODO: Ordering?
            preferenceScreen.addPreference(preference);
        }

        updateState();
    }

    private void onAddRoleHolderStateChanged(int state) {
        AddRoleHolderStateLiveData addRoleHolderStateLiveData =
                mViewModel.getAddRoleHolderStateLiveData();
        switch (state) {
            case AddRoleHolderStateLiveData.STATE_SUCCESS:
                addRoleHolderStateLiveData.resetState();
                break;
            case AddRoleHolderStateLiveData.STATE_FAILURE:
                // TODO: STOPSHIP: Notify user.
                addRoleHolderStateLiveData.resetState();
                break;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        AddRoleHolderStateLiveData addRoleHolderStateLiveData =
                mViewModel.getAddRoleHolderStateLiveData();
        if (addRoleHolderStateLiveData.getValue() != AddRoleHolderStateLiveData.STATE_IDLE) {
            Log.i(LOG_TAG, "Trying to set default app while another request is on-going");
            return true;
        }

        String packageName = preference.getKey();
        addRoleHolderStateLiveData.addRoleHolderAsUser(mRoleName, packageName, mUser,
                requireContext());
        return true;
    }
}
