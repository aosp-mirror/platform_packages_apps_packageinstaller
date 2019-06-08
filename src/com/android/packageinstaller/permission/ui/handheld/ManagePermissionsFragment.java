/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.packageinstaller.Constants.EXTRA_SESSION_ID;
import static com.android.packageinstaller.Constants.INVALID_SESSION_ID;

import android.app.ActionBar;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.packageinstaller.permission.model.PermissionGroups;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.util.ArrayList;

/**
 * Superclass for fragments allowing the user to manage permissions.
 */
abstract class ManagePermissionsFragment extends PermissionsFrameFragment
        implements PermissionGroups.PermissionsGroupsChangeCallback,
        Preference.OnPreferenceClickListener {
    private static final String LOG_TAG = "ManagePermissionsFragment";

    static final String OS_PKG = "android";

    private PermissionGroups mPermissions;

    private Collator mCollator;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setLoading(true /* loading */, false /* animate */);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPermissions = new PermissionGroups(getContext(), getActivity().getLoaderManager(), this,
                false, true);
        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        PermissionGroup group = mPermissions.getGroup(key);
        if (group == null) {
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS)
                .putExtra(Intent.EXTRA_PERMISSION_NAME, key)
                .putExtra(EXTRA_SESSION_ID,
                        getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID));
        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(LOG_TAG, "No app to handle " + intent);
        }

        return true;
    }

    /**
     * @return the permissions
     */
    protected PermissionGroups getPermissions() {
        return mPermissions;
    }

    @Override
    public void onPermissionGroupsChanged() {
        updatePermissionsUi();
    }

    /**
     * Update the preferences to show the new {@link #getPermissions() permissions}.
     */
    protected abstract void updatePermissionsUi();

    /**
     * Add preferences for all permissions of a type to the preference screen.
     *
     * @param addSystemPermissions If the permissions added should be system permissions or not
     *
     * @return The preference screen the permissions were added to
     */
    protected PreferenceScreen updatePermissionsUi(boolean addSystemPermissions) {
        Context context = getPreferenceManager().getContext();
        if (context == null || getActivity() == null) {
            return null;
        }

        ArrayList<PermissionGroup> groups = new ArrayList<>(mPermissions.getGroups());
        groups.sort((x, y) -> mCollator.compare(x.getLabel(), y.getLabel()));
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        } else {
            screen.removeAll();
        }
        screen.setOrderingAsAdded(true);

        // Use this to speed up getting the info for all of the PermissionApps below.
        // Create a new one for each refresh to make sure it has fresh data.
        for (int i = 0; i < groups.size(); i++) {
            PermissionGroup group = groups.get(i);
            boolean isSystemPermission = group.getDeclaringPackage().equals(OS_PKG);

            if (addSystemPermissions == isSystemPermission) {
                Preference preference = findPreference(group.getName());

                if (preference == null) {
                    preference = new FixedSizeIconPreference(context);
                    preference.setOnPreferenceClickListener(this);
                    preference.setKey(group.getName());
                    preference.setIcon(Utils.applyTint(context, group.getIcon(),
                            android.R.attr.colorControlNormal));
                    preference.setTitle(group.getLabel());
                    // Set blank summary so that no resizing/jumping happens when the summary is
                    // loaded.
                    preference.setSummary(" ");
                    preference.setPersistent(false);
                    screen.addPreference(preference);
                }
                preference.setSummary(getString(R.string.app_permissions_group_summary,
                        group.getGranted(), group.getTotal()));
            }
        }
        if (screen.getPreferenceCount() != 0) {
            setLoading(false /* loading */, true /* animate */);
        }

        return screen;
    }

    /**
     * A preference whose icons have the same fixed size.
     */
    private static final class FixedSizeIconPreference extends Preference {
        FixedSizeIconPreference(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            ImageView icon = ((ImageView) holder.findViewById(android.R.id.icon));
            icon.setAdjustViewBounds(true);
            int size = getContext().getResources().getDimensionPixelSize(
                    R.dimen.permission_icon_size);
            icon.setMaxWidth(size);
            icon.setMaxHeight(size);
            icon.getLayoutParams().width = size;
            icon.getLayoutParams().height = size;
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }
}
