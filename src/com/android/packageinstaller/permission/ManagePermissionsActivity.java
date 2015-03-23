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

package com.android.packageinstaller.permission;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public final class ManagePermissionsActivity extends Activity {
    private static final String LOG_TAG = "ManagePermissionsActivity";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            Log.i(LOG_TAG, "Missing mandatory argument ARG_PACKAGE_NAME");
            finish();
            return;
        }

        ManagePermissionsFragment fragment = ManagePermissionsFragment.newInstance(packageName);
        getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }
}
