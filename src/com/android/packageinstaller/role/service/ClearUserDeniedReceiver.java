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

package com.android.packageinstaller.role.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.android.packageinstaller.role.model.UserDeniedManager;

import java.util.Objects;

/**
 * {@link BroadcastReceiver} to clear user denied state when a package is cleared data or fully
 * removed.
 */
public class ClearUserDeniedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        String action = intent.getAction();
        if (!(Objects.equals(action, Intent.ACTION_PACKAGE_DATA_CLEARED)
                || Objects.equals(action, Intent.ACTION_PACKAGE_FULLY_REMOVED))) {
            return;
        }
        String packageName = intent.getData().getSchemeSpecificPart();
        UserDeniedManager.getInstance(context).clearPackageDenied(packageName);
    }
}
