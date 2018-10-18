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

package com.android.packageinstaller.permission.ui;

import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.permission.ui.handheld.ManageStandardPermissionsFragment;

public final class ManagePermissionsActivity extends FragmentActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        if (DeviceUtils.isTelevision(this)) {
            getFragmentManager().beginTransaction().replace(android.R.id.content,
                    com.android.packageinstaller.permission.ui.television.ManagePermissionsFragment
                            .newInstance()).commit();
        } else {
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                    ManageStandardPermissionsFragment.newInstance()).commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // in automotive mode, there's no system wide back button, so need to add that
        if (DeviceUtils.isAuto(this)) {
            switch (item.getItemId()) {
                case android.R.id.home:
                    onBackPressed();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
