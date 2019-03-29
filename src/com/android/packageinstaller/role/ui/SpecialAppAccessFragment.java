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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * Fragment for a special app access.
 */
public class SpecialAppAccessFragment extends SettingsFragment
        implements Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = SpecialAppAccessFragment.class.getSimpleName();

    private static final String PREFERENCE_EXTRA_APPLICATION_INFO =
            SpecialAppAccessFragment.class.getName() + ".extra.APPLICATION_INFO";

    private static final String PREFERENCE_KEY_DESCRIPTION =
            SpecialAppAccessFragment.class.getName() + ".preference.DESCRIPTION";

    private String mRoleName;

    private Role mRole;

    private SpecialAppAccessViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the role for the special app access
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static SpecialAppAccessFragment newInstance(@NonNull String roleName) {
        SpecialAppAccessFragment fragment = new SpecialAppAccessFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = requireActivity();
        mRole = Roles.get(activity).get(mRoleName);
        activity.setTitle(mRole.getLabelResource());

        mViewModel = ViewModelProviders.of(this, new SpecialAppAccessViewModel.Factory(mRole,
                activity.getApplication())).get(SpecialAppAccessViewModel.class);
        mViewModel.getRoleLiveData().observe(this, this::onRoleChanged);
        mViewModel.observeManageRoleHolderState(this, this::onManageRoleHolderStateChanged);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.special_app_access_no_apps;
    }

    private void onRoleChanged(
            @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
        PreferenceManager preferenceManager = getPreferenceManager();
        Context context = preferenceManager.getContext();

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        Preference oldDescriptionPreference = null;
        ArrayMap<String, Preference> oldPreferences = new ArrayMap<>();
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context);
            setPreferenceScreen(preferenceScreen);
        } else {
            oldDescriptionPreference = preferenceScreen.findPreference(PREFERENCE_KEY_DESCRIPTION);
            for (int i = preferenceScreen.getPreferenceCount() - 1; i >= 0; --i) {
                Preference preference = preferenceScreen.getPreference(i);

                preferenceScreen.removePreference(preference);
                oldPreferences.put(preference.getKey(), preference);
            }
        }

        int qualifyingApplicationsSize = qualifyingApplications.size();
        for (int i = 0; i < qualifyingApplicationsSize; i++) {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = qualifyingApplications.get(i);
            ApplicationInfo qualifyingApplicationInfo = qualifyingApplication.first;
            boolean isHolderPackage = qualifyingApplication.second;

            String key = qualifyingApplicationInfo.packageName + '_'
                    + qualifyingApplicationInfo.uid;
            AppIconSwitchPreference preference = (AppIconSwitchPreference) oldPreferences.get(
                    key);
            if (preference == null) {
                preference = new AppIconSwitchPreference(context);
                preference.setKey(key);
                preference.setIcon(Utils.getBadgedIcon(context, qualifyingApplicationInfo));
                preference.setTitle(Utils.getAppLabel(qualifyingApplicationInfo, context));
                preference.setPersistent(false);
                preference.setOnPreferenceChangeListener((preference2, newValue) -> false);
                preference.setOnPreferenceClickListener(this);
                preference.getExtras().putParcelable(PREFERENCE_EXTRA_APPLICATION_INFO,
                        qualifyingApplicationInfo);
            }

            preference.setChecked(isHolderPackage);
            UserHandle user = UserHandle.getUserHandleForUid(qualifyingApplicationInfo.uid);
            mRole.prepareApplicationPreferenceAsUser(preference, qualifyingApplicationInfo, user,
                    context);

            preferenceScreen.addPreference(preference);
        }

        Preference descriptionPreference = oldDescriptionPreference;
        if (descriptionPreference == null) {
            descriptionPreference = new FooterPreference(context);
            descriptionPreference.setKey(PREFERENCE_KEY_DESCRIPTION);
            descriptionPreference.setSummary(mRole.getDescriptionResource());
        }
        preferenceScreen.addPreference(descriptionPreference);

        updateState();
    }

    private void onManageRoleHolderStateChanged(@NonNull ManageRoleHolderStateLiveData liveData,
            int state) {
        switch (state) {
            case ManageRoleHolderStateLiveData.STATE_SUCCESS:
                String packageName = liveData.getLastPackageName();
                if (packageName != null && liveData.isLastAdd()) {
                    mRole.onHolderSelectedAsUser(packageName, liveData.getLastUser(),
                            requireContext());
                }
                liveData.resetState();
                break;
            case ManageRoleHolderStateLiveData.STATE_FAILURE:
                liveData.resetState();
                // TODO: STOPSHIP: Notify user.
                break;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String key = preference.getKey();
        ManageRoleHolderStateLiveData liveData = mViewModel.getManageRoleHolderStateLiveData(key,
                this);
        if (liveData.getValue() != ManageRoleHolderStateLiveData.STATE_IDLE) {
            Log.i(LOG_TAG, "Trying to set special app access while another request is on-going");
            return true;
        }

        ApplicationInfo applicationInfo = preference.getExtras().getParcelable(
                PREFERENCE_EXTRA_APPLICATION_INFO);
        String packageName = applicationInfo.packageName;
        UserHandle user = UserHandle.getUserHandleForUid(applicationInfo.uid);
        boolean add = !((AppIconSwitchPreference) preference).isChecked();
        liveData.setRoleHolderAsUser(mRoleName, packageName, add, 0, user, requireContext());
        return true;
    }
}
