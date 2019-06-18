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
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;

import java.util.List;

/**
 * Child fragment for a special app access. Must be added as a child fragment and its parent
 * fragment must be a {@link PreferenceFragmentCompat} which implements {@link Parent}.
 *
 * @param <PF> type of the parent fragment
 */
public class SpecialAppAccessChildFragment<PF extends PreferenceFragmentCompat
        & SpecialAppAccessChildFragment.Parent> extends Fragment
        implements Preference.OnPreferenceClickListener {

    private static final String PREFERENCE_EXTRA_APPLICATION_INFO =
            SpecialAppAccessChildFragment.class.getName() + ".extra.APPLICATION_INFO";

    private static final String PREFERENCE_KEY_DESCRIPTION =
            SpecialAppAccessChildFragment.class.getName() + ".preference.DESCRIPTION";

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
    public static SpecialAppAccessChildFragment newInstance(@NonNull String roleName) {
        SpecialAppAccessChildFragment fragment = new SpecialAppAccessChildFragment();
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

        PF preferenceFragment = requirePreferenceFragment();
        Activity activity = requireActivity();
        mRole = Roles.get(activity).get(mRoleName);
        preferenceFragment.setTitle(getString(mRole.getLabelResource()));

        mViewModel = ViewModelProviders.of(this, new SpecialAppAccessViewModel.Factory(mRole,
                activity.getApplication())).get(SpecialAppAccessViewModel.class);
        mViewModel.getRoleLiveData().observe(this, this::onRoleChanged);
        mViewModel.observeManageRoleHolderState(this, this::onManageRoleHolderStateChanged);
    }

    private void onRoleChanged(
            @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
        PF preferenceFragment = requirePreferenceFragment();
        PreferenceManager preferenceManager = preferenceFragment.getPreferenceManager();
        Context context = preferenceManager.getContext();

        PreferenceScreen preferenceScreen = preferenceFragment.getPreferenceScreen();
        Preference oldDescriptionPreference = null;
        ArrayMap<String, Preference> oldPreferences = new ArrayMap<>();
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context);
            preferenceFragment.setPreferenceScreen(preferenceScreen);
        } else {
            oldDescriptionPreference = preferenceScreen.findPreference(PREFERENCE_KEY_DESCRIPTION);
            if (oldDescriptionPreference != null) {
                preferenceScreen.removePreference(oldDescriptionPreference);
                oldDescriptionPreference.setOrder(Preference.DEFAULT_ORDER);
            }
            for (int i = preferenceScreen.getPreferenceCount() - 1; i >= 0; --i) {
                Preference preference = preferenceScreen.getPreference(i);

                preferenceScreen.removePreference(preference);
                preference.setOrder(Preference.DEFAULT_ORDER);
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
            TwoStatePreference preference = (TwoStatePreference) oldPreferences.get(key);
            if (preference == null) {
                preference = preferenceFragment.createApplicationPreference(context);
                preference.setKey(key);
                preference.setIcon(Utils.getBadgedIcon(context, qualifyingApplicationInfo));
                preference.setTitle(Utils.getFullAppLabel(qualifyingApplicationInfo, context));
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
            descriptionPreference = preferenceFragment.createFooterPreference(context);
            descriptionPreference.setKey(PREFERENCE_KEY_DESCRIPTION);
            descriptionPreference.setSummary(mRole.getDescriptionResource());
        }
        preferenceScreen.addPreference(descriptionPreference);

        preferenceFragment.onPreferenceScreenChanged();
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
                break;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        ApplicationInfo applicationInfo = preference.getExtras().getParcelable(
                PREFERENCE_EXTRA_APPLICATION_INFO);
        String packageName = applicationInfo.packageName;
        UserHandle user = UserHandle.getUserHandleForUid(applicationInfo.uid);
        boolean allow = !((TwoStatePreference) preference).isChecked();
        String key = preference.getKey();
        mViewModel.setSpecialAppAccessAsUser(packageName, allow, user, key, this,
                this::onManageRoleHolderStateChanged);
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
         * Set the title of the current settings page.
         *
         * @param title the title of the current settings page
         */
        void setTitle(@NonNull CharSequence title);

        /**
         * Create a new preference for an application.
         *
         * @param context the {@code Context} to use when creating the preference.
         *
         * @return a new preference for an application
         */
        @NonNull
        TwoStatePreference createApplicationPreference(@NonNull Context context);

        /**
         * Create a new preference for the footer.
         *
         * @param context the {@code Context} to use when creating the preference.
         *
         * @return a new preference for the footer
         */
        @NonNull
        Preference createFooterPreference(@NonNull Context context);

        /**
         * Callback when changes have been made to the {@link PreferenceScreen} of the parent
         * {@link PreferenceFragmentCompat}.
         */
        void onPreferenceScreenChanged();
    }
}
