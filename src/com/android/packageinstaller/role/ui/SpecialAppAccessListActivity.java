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

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.role.ui.auto.AutoSpecialAppAccessListFragment;
import com.android.packageinstaller.role.ui.handheld.HandheldSpecialAppAccessListFragment;
import com.android.permissioncontroller.R;

/**
 * Activity for the list of special app accesses.
 */
public class SpecialAppAccessListActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (DeviceUtils.isAuto(this)) {
            // Automotive relies on a different theme. Apply before calling super so that
            // fragments are restored properly on configuration changes.
            setTheme(R.style.CarSettings);
        }
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        if (savedInstanceState == null) {
            Fragment fragment;
            if (DeviceUtils.isAuto(this)) {
                fragment = AutoSpecialAppAccessListFragment.newInstance();
            } else {
                fragment = HandheldSpecialAppAccessListFragment.newInstance();
            }
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }
    }
}
