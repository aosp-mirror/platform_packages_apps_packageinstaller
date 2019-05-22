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

package com.android.packageinstaller.permission.ui.auto;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.android.packageinstaller.auto.AutoSettingsFrameFragment;
import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.packageinstaller.permission.model.PermissionGroups;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.util.ArrayList;

/** Base class to show the list of permissions that can be granted/denied. */
abstract class AutoManagePermissionsFragment extends AutoSettingsFrameFragment implements
        PermissionGroups.PermissionsGroupsChangeCallback, Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = "ManagePermissionsFragment";

    static final String OS_PKG = "android";

    private PermissionGroups mPermissions;

    private Collator mCollator;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true);

        mPermissions = new PermissionGroups(getContext(), requireActivity().getLoaderManager(),
                /* callback= */ this, /* getAppUiInfo= */ false,
                /* getNonPlatformPermissions= */ true);
        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        setHeaderLabel(getString(getScreenHeaderRes()));
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        PermissionGroup group = mPermissions.getGroup(key);
        if (group == null) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(Intent.EXTRA_PERMISSION_NAME, key);
        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(LOG_TAG, "No app to handle " + intent);
        }

        return true;
    }

    /** Returns the header string resource. */
    @StringRes
    protected abstract int getScreenHeaderRes();

    /** Returns the current permissions. */
    protected PermissionGroups getPermissions() {
        return mPermissions;
    }

    @Override
    public void onPermissionGroupsChanged() {
        updatePermissionsUi();
    }

    /** Update the preferences to show the new {@link #getPermissions() permissions}. */
    protected abstract void updatePermissionsUi();

    /**
     * Add preferences for all permissions of a type to the preference screen.
     */
    protected void updatePermissionsUi(boolean addSystemPermissions) {
        Context context = getPreferenceManager().getContext();
        if (context == null || getActivity() == null) {
            return;
        }

        ArrayList<PermissionGroup> groups = new ArrayList<>(mPermissions.getGroups());
        groups.sort((x, y) -> mCollator.compare(x.getLabel(), y.getLabel()));
        getPreferenceScreen().removeAll();
        getPreferenceScreen().setOrderingAsAdded(true);

        // Use this to speed up getting the info for all of the PermissionApps below.
        // Create a new one for each refresh to make sure it has fresh data.
        for (int i = 0; i < groups.size(); i++) {
            PermissionGroup group = groups.get(i);
            boolean isSystemPermission = group.getDeclaringPackage().equals(OS_PKG);

            if (addSystemPermissions == isSystemPermission) {
                Preference preference = findPreference(group.getName());

                if (preference == null) {
                    preference = new Preference(context);
                    preference.setOnPreferenceClickListener(this);
                    preference.setKey(group.getName());
                    preference.setIcon(Utils.applyTint(context, group.getIcon(),
                            android.R.attr.colorControlNormal));
                    preference.setTitle(group.getLabel());
                    // Set blank summary so that no resizing/jumping happens when the summary is
                    // loaded.
                    preference.setSummary(" ");
                    preference.setPersistent(false);
                    getPreferenceScreen().addPreference(preference);
                }
                preference.setSummary(
                        getString(R.string.app_permissions_group_summary, group.getGranted(),
                                group.getTotal()));
            }
        }
        if (getPreferenceScreen().getPreferenceCount() != 0) {
            setLoading(false);
        }
    }
}
