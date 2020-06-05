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
package com.android.permissioncontroller.permission.ui.handheld;

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Superclass for fragments allowing the user to manage permissions.
 */
abstract class ManagePermissionsFragment extends PermissionsFrameFragment
        implements Preference.OnPreferenceClickListener {
    private static final String LOG_TAG = "ManagePermissionsFragment";

    /**
     * Map<permission names, PermGroupPackagesUiInfo>, representing the data about which
     * apps are in which permission groups, which to show, and which are granted.
     */
    protected Map<String, PermGroupPackagesUiInfo> mPermissionGroups = new HashMap<>();
    private Collator mCollator;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        PermGroupPackagesUiInfo group = mPermissionGroups.get(key);
        if (group == null) {
            return false;
        }

        showPermissionApps(key);

        return true;
    }

    abstract void showPermissionApps(String permissionGroupName);

    /**
     * Add preferences for all permissions of a type to the preference screen.
     *
     * @return The preference screen the permissions were added to
     */
    protected PreferenceScreen updatePermissionsUi() {
        Context context = getPreferenceManager().getContext();
        if (context == null || getActivity() == null) {
            return null;
        }

        if (mPermissionGroups == null) {
            return null;
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }

        List<Preference> toRemove = new ArrayList<>();
        for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
            Preference group = screen.getPreference(i);
            if (!mPermissionGroups.containsKey(group.getKey())) {
                toRemove.add(group);
            }
        }

        for (Preference pref: toRemove) {
            screen.removePreference(pref);
        }

        for (String groupName : mPermissionGroups.keySet()) {

            PermGroupPackagesUiInfo group = mPermissionGroups.get(groupName);

            Preference preference = findPreference(groupName);

            if (preference == null) {
                preference = new FixedSizeIconPreference(context);
                preference.setOnPreferenceClickListener(this);
                preference.setKey(groupName);
                preference.setIcon(Utils.applyTint(context,
                        KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName),
                        android.R.attr.colorControlNormal));
                preference.setTitle(KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName));
                // Set blank summary so that no resizing/jumping happens when the summary is
                // loaded.
                preference.setSummary(" ");
                preference.setPersistent(false);
                screen.addPreference(preference);
            }
            String summary;
            if (group != null) {
                if (getResources().getBoolean(R.bool.config_useAlternativePermGroupSummary)) {
                    summary = getString(R.string.app_permissions_group_summary2,
                            group.getNonSystemGranted(), group.getNonSystemTotal());
                } else {
                    summary = getString(R.string.app_permissions_group_summary,
                            group.getNonSystemGranted(), group.getNonSystemTotal());
                }
            } else {
                summary = getString(R.string.loading);
            }
            preference.setSummary(summary);
        }

        KotlinUtils.INSTANCE.sortPreferenceGroup(screen, (Preference lhs, Preference rhs) ->
                mCollator.compare(lhs.getTitle().toString(), rhs.getTitle().toString()),
                false
        );

        return screen;
    }

    /**
     * A preference whose icons have the same fixed size. Allows the setting of dividers above and
     * below the preference.
     */
    protected static final class FixedSizeIconPreference extends Preference {
        private boolean mShowDividerAbove = true;
        private boolean mShowDividerBelow = false;

        FixedSizeIconPreference(Context context) {
            super(context);
        }

        FixedSizeIconPreference(Context context, boolean dividerAbove, boolean dividerBelow) {
            super(context);
            mShowDividerAbove = dividerAbove;
            mShowDividerBelow = dividerBelow;
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

            holder.setDividerAllowedAbove(mShowDividerAbove);
            holder.setDividerAllowedBelow(mShowDividerBelow);
        }
    }
}
