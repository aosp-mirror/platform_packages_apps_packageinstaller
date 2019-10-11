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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.ui.auto.AutoSpecialAppAccessFragment;
import com.android.packageinstaller.role.ui.handheld.HandheldSpecialAppAccessFragment;
import com.android.permissioncontroller.R;

/**
 * Activity for a special app access.
 */
public class SpecialAppAccessActivity extends FragmentActivity {

    private static final String LOG_TAG = SpecialAppAccessActivity.class.getSimpleName();

    /**
     * Create an intent for starting this activity.
     *
     * @param roleName the name of the role for the special app access
     * @param context  the context to create the intent
     * @return an intent to start this activity
     */
    @NonNull
    public static Intent createIntent(@NonNull String roleName, @NonNull Context context) {
        return new Intent(context, SpecialAppAccessActivity.class)
                .putExtra(Intent.EXTRA_ROLE_NAME, roleName);
    }

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

        String roleName = getIntent().getStringExtra(Intent.EXTRA_ROLE_NAME);

        Role role = Roles.get(this).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            finish();
            return;
        }
        if (!role.isAvailable(this)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            finish();
            return;
        }
        if (!role.isVisible(this)) {
            Log.e(LOG_TAG, "Role is invisible: " + roleName);
            finish();
            return;
        }

        if (savedInstanceState == null) {
            Fragment fragment;
            if (DeviceUtils.isAuto(this)) {
                fragment = AutoSpecialAppAccessFragment.newInstance(roleName);
            } else {
                fragment = HandheldSpecialAppAccessFragment.newInstance(roleName);
            }
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }
    }
}
