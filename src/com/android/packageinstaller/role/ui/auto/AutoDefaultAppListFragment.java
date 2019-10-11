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

package com.android.packageinstaller.role.ui.auto;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.auto.AutoSettingsFrameFragment;
import com.android.packageinstaller.role.ui.DefaultAppListChildFragment;
import com.android.packageinstaller.role.ui.TwoTargetPreference;
import com.android.permissioncontroller.R;

/** Shows various roles for which a default app can be picked. */
public class AutoDefaultAppListFragment extends AutoSettingsFrameFragment implements
        DefaultAppListChildFragment.Parent {

    /** Create a new instance of this fragment. */
    @NonNull
    public static AutoDefaultAppListFragment newInstance() {
        return new AutoDefaultAppListFragment();
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // Preferences will be added via shared logic in {@link DefaultAppListChildFragment}.
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState == null) {
            DefaultAppListChildFragment fragment = DefaultAppListChildFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(fragment, null)
                    .commit();
        }

        setHeaderLabel(getString(R.string.default_apps));
    }

    @NonNull
    @Override
    public TwoTargetPreference createPreference(@NonNull Context context) {
        return new AutoSettingsPreference(context);
    }

    @Override
    public void onPreferenceScreenChanged() {
    }
}
