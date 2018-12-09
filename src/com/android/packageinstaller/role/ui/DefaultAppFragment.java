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
import android.app.role.RoleManager;
import android.app.role.RoleManagerCallback;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
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

    private Role mRole;

    private RoleViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the role to be managed
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static DefaultAppFragment newInstance(@NonNull String roleName) {
        DefaultAppFragment fragment = new DefaultAppFragment();
        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_ROLE_NAME, roleName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRoleName = getArguments().getString(EXTRA_ROLE_NAME);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity activity = requireActivity();
        mRole = Roles.getRoles(activity).get(mRoleName);
        activity.setTitle(mRole.getLabelResource());

        mViewModel = ViewModelProviders.of(this, new RoleViewModel.Factory(mRole,
                activity.getApplication())).get(RoleViewModel.class);
        mViewModel.getLiveData().observe(this, this::onRoleInfoChanged);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.no_apps_for_default_app;
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

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        // TODO: STOPSHIP: Support multiple role holders.
        String packageName = preference.getKey();
        Log.i(LOG_TAG, "Adding application as role holder, role: " + mRoleName + ", package: "
                + packageName);
        Context context = requireContext();
        RoleManager roleManager = context.getSystemService(RoleManager.class);
        roleManager.addRoleHolderAsUser(mRoleName, packageName, Process.myUserHandle(),
                context.getMainExecutor(), new RoleManagerCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(LOG_TAG, "Added application as role holder, role: " + mRoleName
                                + ", package: " + packageName);
                        // TODO: STOPSHIP: Use role holder observation instead.
                        mViewModel.getLiveData().loadValue();
                    }
                    @Override
                    public void onFailure() {
                        Log.i(LOG_TAG, "Failed to add application as role holder, role: "
                                + mRoleName + ", package: " + packageName);
                        // TODO: STOPSHIP: Use role holder observation instead.
                        mViewModel.getLiveData().loadValue();
                    }
                });
        return true;
    }
}
