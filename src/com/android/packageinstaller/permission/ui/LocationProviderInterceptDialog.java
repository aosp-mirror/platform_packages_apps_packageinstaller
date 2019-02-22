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

package com.android.packageinstaller.permission.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

/**
 * A dialog saying that you cannot change the location provider's location permission.
 */
public final class LocationProviderInterceptDialog extends FragmentActivity {
    private static final String LOG_TAG = LocationProviderInterceptDialog.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.i(LOG_TAG, "Missing mandatory argument EXTRA_PACKAGE_NAME");
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_dialog_alert_material)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(getString(R.string.location_warning,
                        Utils.getAppLabel(getPackageInfo(packageName).applicationInfo, this)))
                .setNegativeButton(R.string.ok, null)
                .setPositiveButton(R.string.location_settings, (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setOnDismissListener((dialog) -> finish())
                .show();
    }

    private @Nullable PackageInfo getPackageInfo(@NonNull String packageName) {
        try {
            return getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package: " + packageName, e);
            finish();
            return null;
        }
    }
}
