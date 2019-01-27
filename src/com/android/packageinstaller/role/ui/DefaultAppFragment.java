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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.permissioncontroller.R;

import java.util.List;
import java.util.Objects;

/**
 * Fragment for a default app.
 */
public class DefaultAppFragment extends SettingsFragment
        implements DefaultAppConfirmationDialogFragment.Listener,
        Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = DefaultAppFragment.class.getSimpleName();

    private static final String PREFERENCE_KEY_NONE =
            DefaultAppFragment.class.getPackage().getName() + ".preference.NONE";

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
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        arguments.putParcelable(Intent.EXTRA_USER, user);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);
        mUser = arguments.getParcelable(Intent.EXTRA_USER);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = requireActivity();
        mRole = Roles.get(activity).get(mRoleName);
        activity.setTitle(mRole.getLabelResource());

        mViewModel = ViewModelProviders.of(this, new DefaultAppViewModel.Factory(mRole, mUser,
                activity.getApplication())).get(DefaultAppViewModel.class);
        mViewModel.getRoleLiveData().observe(this, this::onRoleChanged);
        mViewModel.getManageRoleHolderStateLiveData().observe(this,
                this::onManageRoleHolderStateChanged);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.default_app_no_apps;
    }

    private void onRoleChanged(
            @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
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

        if (mRole.shouldShowNone()) {
            Drawable icon = AppCompatResources.getDrawable(context, R.drawable.ic_remove_circle);
            String title = context.getString(R.string.default_app_none);
            boolean noHolderApplication = !hasHolderApplication(qualifyingApplications);
            addPreference(PREFERENCE_KEY_NONE, icon, title, noHolderApplication, oldPreferences,
                    preferenceScreen, context);
        }

        int qualifyingApplicationsSize = qualifyingApplications.size();
        for (int i = 0; i < qualifyingApplicationsSize; i++) {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = qualifyingApplications.get(i);
            ApplicationInfo qualifyingApplicationInfo = qualifyingApplication.first;
            boolean isHolderApplication = qualifyingApplication.second;

            String key = qualifyingApplicationInfo.packageName;
            Drawable icon = Utils.getBadgedIcon(context, qualifyingApplicationInfo);
            String title = Utils.getAppLabel(qualifyingApplicationInfo, context);
            addPreference(key, icon, title, isHolderApplication, oldPreferences, preferenceScreen,
                    context);
        }

        updateState();
    }

    private static boolean hasHolderApplication(
            @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
        int qualifyingApplicationsSize = qualifyingApplications.size();
        for (int i = 0; i < qualifyingApplicationsSize; i++) {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = qualifyingApplications.get(i);
            boolean isHolderApplication = qualifyingApplication.second;

            if (isHolderApplication) {
                return true;
            }
        }
        return false;
    }

    private void addPreference(@NonNull String key, @NonNull Drawable icon,
            @NonNull CharSequence title, boolean checked,
            @NonNull ArrayMap<String, Preference> oldPreferences,
            @NonNull PreferenceScreen preferenceScreen, @NonNull Context context) {
        AppIconRadioButtonPreference preference = (AppIconRadioButtonPreference) oldPreferences.get(
                key);
        if (preference == null) {
            preference = new AppIconRadioButtonPreference(context);
            preference.setKey(key);
            preference.setIcon(icon);
            preference.setTitle(title);
            preference.setPersistent(false);
            preference.setOnPreferenceChangeListener((preference2, newValue) -> false);
            preference.setOnPreferenceClickListener(this);
        }
        preference.setChecked(checked);
        // TODO: Ordering?
        preferenceScreen.addPreference(preference);
    }

    private void onManageRoleHolderStateChanged(int state) {
        ManageRoleHolderStateLiveData liveData = mViewModel.getManageRoleHolderStateLiveData();
        switch (state) {
            case ManageRoleHolderStateLiveData.STATE_SUCCESS:
                liveData.resetState();
                break;
            case ManageRoleHolderStateLiveData.STATE_FAILURE:
                // TODO: STOPSHIP: Notify user.
                liveData.resetState();
                break;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String key = preference.getKey();
        if (Objects.equals(key, PREFERENCE_KEY_NONE)) {
            setNoneDefaultApp();
        } else {
            String packageName = key;
            CharSequence confirmationMessage = mRole.getConfirmationMessage(packageName,
                    requireContext());
            if (confirmationMessage != null) {
                DefaultAppConfirmationDialogFragment.show(packageName, confirmationMessage, this);
            } else {
                setDefaultApp(packageName);
            }
        }
        return true;
    }

    @Override
    public void setDefaultApp(@NonNull String packageName) {
        ManageRoleHolderStateLiveData liveData = mViewModel.getManageRoleHolderStateLiveData();
        if (liveData.getValue() != ManageRoleHolderStateLiveData.STATE_IDLE) {
            Log.i(LOG_TAG, "Trying to set default app while another request is on-going");
            return;
        }
        liveData.setRoleHolderAsUser(mRoleName, packageName, true, mUser, requireContext());
    }

    private void setNoneDefaultApp() {
        mRole.onNoneHolderSelectedAsUser(requireContext(), mUser);

        ManageRoleHolderStateLiveData liveData = mViewModel.getManageRoleHolderStateLiveData();
        if (liveData.getValue() != ManageRoleHolderStateLiveData.STATE_IDLE) {
            Log.i(LOG_TAG, "Trying to set default app while another request is on-going");
            return;
        }
        liveData.clearRoleHoldersAsUser(mRoleName, mUser, requireContext());
    }
}
