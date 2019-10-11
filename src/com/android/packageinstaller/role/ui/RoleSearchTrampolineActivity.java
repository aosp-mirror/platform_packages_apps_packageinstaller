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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

import com.android.packageinstaller.role.service.RoleSearchIndexablesProvider;

/**
 * Trampoline activity for activities exposed from
 * {@link com.android.packageinstaller.role.service.RoleSearchIndexablesProvider}.
 */
public class RoleSearchTrampolineActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (!RoleSearchIndexablesProvider.isIntentValid(intent, this)) {
            finish();
            return;
        }

        String action = intent.getAction();
        if (action == null) {
            finish();
            return;
        }

        Intent newIntent;
        switch (action) {
            case RoleSearchIndexablesProvider.ACTION_MANAGE_DEFAULT_APP:
                newIntent = DefaultAppActivity.createIntent(
                        // We don't support work profile in search.
                        RoleSearchIndexablesProvider.getOriginalKey(intent), Process.myUserHandle(),
                        this);
                break;
            case RoleSearchIndexablesProvider.ACTION_MANAGE_SPECIAL_APP_ACCESS:
                newIntent = SpecialAppAccessActivity.createIntent(
                        RoleSearchIndexablesProvider.getOriginalKey(intent), this);
                break;
            default:
                finish();
                return;
        }

        newIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        startActivity(newIntent);
        finish();
    }
}
