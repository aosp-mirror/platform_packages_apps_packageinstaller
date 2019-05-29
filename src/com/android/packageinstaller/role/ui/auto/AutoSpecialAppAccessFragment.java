/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.packageinstaller.role.ui.auto;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.packageinstaller.auto.AutoSettingsFrameFragment;
import com.android.packageinstaller.role.ui.SpecialAppAccessChildFragment;
import com.android.permissioncontroller.R;

/** Automotive fragment for displaying special app access for a role. */
public class AutoSpecialAppAccessFragment extends AutoSettingsFrameFragment implements
        SpecialAppAccessChildFragment.Parent {

    private String mRoleName;

    /**
     * Returns a new instance of {@link AutoSpecialAppAccessFragment} for the given {@code
     * roleName}.
     */
    @NonNull
    public static AutoSpecialAppAccessFragment newInstance(@NonNull String roleName) {
        AutoSpecialAppAccessFragment fragment = new AutoSpecialAppAccessFragment();
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
    public void onCreatePreferences(Bundle bundle, String s) {
        // Preferences will be added by the child fragment.
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState == null) {
            SpecialAppAccessChildFragment fragment = SpecialAppAccessChildFragment.newInstance(
                    mRoleName);
            getChildFragmentManager().beginTransaction()
                    .add(fragment, null)
                    .commit();
        }
    }

    @Override
    public void setTitle(@NonNull CharSequence title) {
        setHeaderLabel(title);
    }

    @NonNull
    @Override
    public TwoStatePreference createApplicationPreference(@NonNull Context context) {
        return new SwitchPreference(context);
    }

    @NonNull
    @Override
    public Preference createFooterPreference(@NonNull Context context) {
        Preference preference = new Preference(context);
        preference.setIcon(R.drawable.ic_info_outline);
        preference.setSelectable(false);
        return preference;
    }

    @Override
    public void onPreferenceScreenChanged() {
    }
}
