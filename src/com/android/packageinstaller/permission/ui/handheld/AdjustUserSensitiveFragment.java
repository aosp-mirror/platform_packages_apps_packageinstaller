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

package com.android.packageinstaller.permission.ui.handheld;

import static android.content.Context.MODE_PRIVATE;

import static com.android.packageinstaller.Constants.ALLOW_OVERRIDE_USER_SENSITIVE_KEY;
import static com.android.packageinstaller.Constants.ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY;
import static com.android.packageinstaller.Constants.FORCED_USER_SENSITIVE_UIDS_KEY;
import static com.android.packageinstaller.Constants.PREFERENCES_FILE;
import static com.android.packageinstaller.permission.utils.Utils.getFullAppLabel;
import static com.android.packageinstaller.permission.utils.Utils.getParentUserContext;
import static com.android.packageinstaller.permission.utils.Utils.updateUserSensitive;

import android.app.ActionBar;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.packageinstaller.Constants;
import com.android.packageinstaller.permission.data.BooleanSharedPreferenceLiveData;
import com.android.packageinstaller.permission.data.ForcedUserSensitiveUidsLiveData;
import com.android.packageinstaller.permission.data.NonSensitivePackagesLiveData;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Allow the user to select which apps (that are usually not considered user sensitive) should still
 * be considered user sensitive.
 */
public class AdjustUserSensitiveFragment extends PermissionsFrameFragment {
    private Collator mCollator;
    private UserSensitiveOverrideViewModel mViewModel;

    /**
     * Switch that matches the value of the {@link Constants#ALLOW_OVERRIDE_USER_SENSITIVE_KEY}
     * shared preference
     */
    private SwitchPreference mGlobalUserSensitiveSwitch;

    /**
     * Switch that matches the value of the
     * {@link Constants#ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY} shared preference
     */
    private SwitchPreference mAssistantRecordAudioIsUserSensitiveSwitch;

    /** The packages that might have non user sensitive permissions */
    private ArrayList<ApplicationInfo> mSortedNonSensitivityPackages;

    /**
     * Caches the app labels for packages in {@link #mSortedNonSensitivityPackages}.
     *
     * <p>{@code pkgName -> label}
     */
    private ArrayMap<String, String> mLabelCache;

    /**
     * @return A new {@link AdjustUserSensitiveFragment}
     */
    public static AdjustUserSensitiveFragment newInstance() {
        return new AdjustUserSensitiveFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setLoading(true /* loading */, false /* animate */);
        setHasOptionsMenu(true);

        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(getString(R.string.adjust_user_sensitive_title));
        }

        mCollator = Collator.getInstance(getContext().getResources().getConfiguration().getLocales()
                .get(0));

        mViewModel = ViewModelProviders.of(this, new UserSensitiveOverrideViewModel.Factory(
                getActivity().getApplication())).get(UserSensitiveOverrideViewModel.class);
        mViewModel.getNonSensitivePackagesLiveData().observe(this,
                (v) -> updateOverrideUi());
        mViewModel.getAllowOverrideUserSensitiveLiveData().observe(this,
                (v) -> updateOverrideSwitches());
        mViewModel.getForcedUserSensitiveUidsLiveData().observe(this,
                (v) -> updatePerPackageOverrideSwitches());
        mViewModel.getAssistantRecordAudioIsUserSensitiveLiveData().observe(this,
                (v) -> mAssistantRecordAudioIsUserSensitiveSwitch.setChecked(v));

        addPreferencesFromResource(R.xml.adjust_user_sensitive);

        mGlobalUserSensitiveSwitch = findPreference("global");
        mGlobalUserSensitiveSwitch.setOnPreferenceChangeListener((p, newValue) -> {
            mViewModel.setAllowOverrideUserSensitive((Boolean) newValue);
            return true;
        });

        mAssistantRecordAudioIsUserSensitiveSwitch = findPreference("assistantrecordaudio");
        mAssistantRecordAudioIsUserSensitiveSwitch.setOnPreferenceChangeListener((p, newValue) -> {
            mViewModel.setAssistantRecordAudioIsUserSensitive((Boolean) newValue);
            return true;
        });
    }

    private void updateOverrideUi() {
        mSortedNonSensitivityPackages = mViewModel.getNonSensitivePackagesLiveData().getValue();

        int numPkgs = mSortedNonSensitivityPackages.size();
        mLabelCache = new ArrayMap<>(numPkgs);
        for (int i = 0; i < numPkgs; i++) {
            ApplicationInfo appInfo = mSortedNonSensitivityPackages.get(i);
            mLabelCache.put(appInfo.packageName, getFullAppLabel(appInfo, getContext()));
        }

        mSortedNonSensitivityPackages.sort((a, b) ->
                mCollator.compare(mLabelCache.get(a.packageName), mLabelCache.get(b.packageName)));

        updateOverrideSwitches();
    }

    private void updateOverrideSwitches() {
        mGlobalUserSensitiveSwitch.setChecked(
                mViewModel.getAllowOverrideUserSensitiveLiveData().getValue());

        updatePerPackageOverrideSwitches();
    }

    private void updatePerPackageOverrideSwitches() {
        if (mSortedNonSensitivityPackages == null
                || mViewModel.getForcedUserSensitiveUidsLiveData().getValue() == null) {
            return;
        }

        Context context = getContext();

        setLoading(false, true);

        PreferenceCategory perApp = findPreference("perapp");

        ArrayMap<String, NonUserSensitiveAppPreference> oldPrefs = new ArrayMap<>();
        int numPrefs = perApp.getPreferenceCount();
        for (int i = 0; i < numPrefs; i++) {
            NonUserSensitiveAppPreference pref =
                    (NonUserSensitiveAppPreference) perApp.getPreference(i);
            oldPrefs.put(pref.getKey(), pref);
        }

        int numPkgs = mSortedNonSensitivityPackages.size();
        for (int pkgNum = 0; pkgNum < numPkgs; pkgNum++) {
            ApplicationInfo appInfo = mSortedNonSensitivityPackages.get(pkgNum);

            NonUserSensitiveAppPreference pref = oldPrefs.remove(appInfo.packageName + appInfo.uid);
            if (pref == null) {
                pref = new NonUserSensitiveAppPreference(context,
                        mLabelCache.get(appInfo.packageName),
                        Utils.getBadgedIcon(context, appInfo));
                pref.setKey(appInfo.packageName + appInfo.uid);

                pref.setOnPreferenceChangeListener(
                        (p, isChecked) -> {
                            mViewModel.setUidUserSensitive(appInfo.uid, (Boolean) isChecked);
                            return true;
                        });

                perApp.addPreference(pref);
            }
            pref.setOrder(pkgNum);

            pref.setChecked(mViewModel.getForcedUserSensitiveUidsLiveData().getValue().indexOfKey(
                    appInfo.uid) >= 0);
            pref.setEnabled(mViewModel.getAllowOverrideUserSensitiveLiveData().getValue());
        }

        int numRemovedPrefs = oldPrefs.size();
        for (int i = 0; i < numRemovedPrefs; i++) {
            perApp.removePreference(oldPrefs.valueAt(i));
        }
    }

    /**
     * A preference for a package.
     */
    private static class NonUserSensitiveAppPreference extends SwitchPreference {
        private final int mIconSize;
        private boolean mIsIconSizeSet = false;

        NonUserSensitiveAppPreference(@NonNull Context context, @NonNull String appLabel,
                @NonNull Drawable appIcon) {
            super(context);

            mIconSize = context.getResources().getDimensionPixelSize(
                    R.dimen.secondary_app_icon_size);

            setTitle(appLabel);
            setIcon(appIcon);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            if (!mIsIconSizeSet) {
                ImageView icon = ((ImageView) holder.findViewById(android.R.id.icon));

                icon.setMaxWidth(mIconSize);
                icon.setMaxHeight(mIconSize);

                mIsIconSizeSet = true;
            }

            super.onBindViewHolder(holder);
        }
    }

    private static class UserSensitiveOverrideViewModel extends AndroidViewModel {
        private final @NonNull SharedPreferences mPrefs;

        private final @NonNull NonSensitivePackagesLiveData mNonSensitivePackages;
        private final @NonNull BooleanSharedPreferenceLiveData mAllowOverrideUserSensitive;
        private final @NonNull ForcedUserSensitiveUidsLiveData mForcedUserSensitiveUids;
        private final @NonNull BooleanSharedPreferenceLiveData mAssistantRecordAudioIsUserSensitive;

        UserSensitiveOverrideViewModel(@NonNull Application application) {
            super(application);

            mPrefs = getParentUserContext(application).getSharedPreferences(PREFERENCES_FILE,
                    MODE_PRIVATE);

            mNonSensitivePackages = NonSensitivePackagesLiveData.get(application);
            mAllowOverrideUserSensitive = BooleanSharedPreferenceLiveData.get(
                    ALLOW_OVERRIDE_USER_SENSITIVE_KEY, application);
            mForcedUserSensitiveUids = ForcedUserSensitiveUidsLiveData.get(application);
            mAssistantRecordAudioIsUserSensitive = BooleanSharedPreferenceLiveData.get(
                    ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY, application);
        }

        @NonNull NonSensitivePackagesLiveData getNonSensitivePackagesLiveData() {
            return mNonSensitivePackages;
        }

        @NonNull BooleanSharedPreferenceLiveData getAllowOverrideUserSensitiveLiveData() {
            return mAllowOverrideUserSensitive;
        }

        @NonNull ForcedUserSensitiveUidsLiveData getForcedUserSensitiveUidsLiveData() {
            return mForcedUserSensitiveUids;
        }

        @NonNull BooleanSharedPreferenceLiveData getAssistantRecordAudioIsUserSensitiveLiveData() {
            return mAssistantRecordAudioIsUserSensitive;
        }

        /**
         * Update permission state to reflect user sensitivity selected.
         *
         * @param user The user to update.
         */
        private void updatePermissionFlags(@NonNull UserHandle user) {
            AsyncTask.execute(() -> updateUserSensitive(getApplication(), user));
        }

        /**
         * Update permission state to reflect user sensitivity selected. (for all users)
         */
        private void updatePermissionFlags() {
            AsyncTask.execute(() -> {
                List<UserHandle> users = getApplication().getSystemService(UserManager.class)
                        .getUserProfiles();

                int numUsers = users.size();
                for (int userNum = 0; userNum < numUsers; userNum++) {
                    UserHandle user = users.get(userNum);
                    updateUserSensitive(getApplication(), user);
                }
            });
        }

        /**
         * Mark/unmark a uid as user sensitive.
         *
         * @param uid The uid to mark
         * @param makeAlwaysUserSensitive {@code true} iff the uid should be made user sensitive
         */
        void setUidUserSensitive(int uid, boolean makeAlwaysUserSensitive) {
            Set<String> currentOverrides = mPrefs.getStringSet(FORCED_USER_SENSITIVE_UIDS_KEY,
                    null);
            String key = String.valueOf(uid);

            Set<String> newOverrides;
            if (makeAlwaysUserSensitive) {
                if (currentOverrides == null) {
                    newOverrides = Collections.singleton(key);
                } else {
                    newOverrides = new ArraySet<>(currentOverrides);
                    newOverrides.add(key);
                }
            } else {
                if (currentOverrides == null) {
                    return;
                }

                newOverrides = new ArraySet<>(currentOverrides);
                newOverrides.remove(key);
            }

            if (newOverrides.isEmpty()) {
                mPrefs.edit().remove(FORCED_USER_SENSITIVE_UIDS_KEY).apply();
            } else {
                mPrefs.edit().putStringSet(FORCED_USER_SENSITIVE_UIDS_KEY, newOverrides).apply();
            }

            updatePermissionFlags(UserHandle.getUserHandleForUid(uid));
        }

        /**
         * Allow/disallow the user to mark uids as user sensitive.
         *
         * <p>If this flips from false -> true, all uids are considered user sensitive by default.
         *
         * @param makeAlwaysUserSensitive {@code true} iff the user should be allowed to mark uids
         *                                            as user sensitive.
         */
        void setAllowOverrideUserSensitive(boolean makeAlwaysUserSensitive) {
            SharedPreferences.Editor sharedPrefChanges = mPrefs.edit();

            if (makeAlwaysUserSensitive) {
                sharedPrefChanges.putBoolean(ALLOW_OVERRIDE_USER_SENSITIVE_KEY, true);

                ArraySet<String> overrides = new ArraySet<>();

                ArrayList<ApplicationInfo> pkgs = getNonSensitivePackagesLiveData().getValue();
                int numPkgs = pkgs.size();
                for (int i = 0; i < numPkgs; i++) {
                    overrides.add(String.valueOf(pkgs.get(i).uid));
                }

                sharedPrefChanges.putStringSet(FORCED_USER_SENSITIVE_UIDS_KEY, overrides);
            } else {
                sharedPrefChanges.remove(ALLOW_OVERRIDE_USER_SENSITIVE_KEY);
                sharedPrefChanges.remove(FORCED_USER_SENSITIVE_UIDS_KEY);
            }

            sharedPrefChanges.apply();

            updatePermissionFlags();
        }

        /**
         * Mark the assistant's record audio permissions as user sensitive.
         *
         * @param isAssistantRecordAudioUserSensitive {@code true} to mark it as user sensitive,
         *                                            {@code false} to mark it as non sensitive
         */
        void setAssistantRecordAudioIsUserSensitive(boolean isAssistantRecordAudioUserSensitive) {
            SharedPreferences.Editor sharedPrefChanges = mPrefs.edit();

            if (isAssistantRecordAudioUserSensitive) {
                sharedPrefChanges.putBoolean(ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY, true);
            } else {
                sharedPrefChanges.remove(ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY);
            }

            sharedPrefChanges.apply();

            // We don't know which user contains the assistant
            updatePermissionFlags();
        }

        /**
         * {@link ViewModelProvider.Factory} for {@link UserSensitiveOverrideViewModel}.
         */
        public static class Factory implements ViewModelProvider.Factory {
            private @NonNull Application mApplication;

            Factory(@NonNull Application application) {
                mApplication = application;
            }

            @Override
            public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                //noinspection unchecked
                return (T) new UserSensitiveOverrideViewModel(mApplication);
            }
        }
    }
}
