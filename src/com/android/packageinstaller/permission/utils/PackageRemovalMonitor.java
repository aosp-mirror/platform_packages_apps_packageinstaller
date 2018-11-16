/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller.permission.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;

/**
 * Monitors the state of a package (esp. if it gets uninstalled)
 */
public abstract class PackageRemovalMonitor extends BroadcastReceiver {
    private final @NonNull Context mContext;
    private final @NonNull String mPackageName;

    public PackageRemovalMonitor(@NonNull Context context, @NonNull String packageName) {
        mContext = context;
        mPackageName = packageName;
    }

    protected abstract void onPackageRemoved();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                && mPackageName.equals(intent.getData().getSchemeSpecificPart())) {
            onPackageRemoved();
        }
    }

    /**
     * Enable monitoring
     */
    public void register() {
        IntentFilter packageRemovedFilter = new IntentFilter();
        packageRemovedFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageRemovedFilter.addDataScheme("package");

        mContext.registerReceiver(this, packageRemovedFilter);
    }

    /**
     * Disable monitoring
     */
    public void unregister() {
        mContext.unregisterReceiver(this);
    }
}
