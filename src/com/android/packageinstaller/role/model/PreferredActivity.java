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

package com.android.packageinstaller.role.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Process;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Specifies a preferred {@code Activity} configuration to be configured by a {@link Role}.
 */
public class PreferredActivity {

    /**
     * The specification of the {@code Activity} to be preferred.
     */
    @NonNull
    private final RequiredActivity mActivity;

    /**
     * The list of {@code IntentFilter} specifications to be configured to prefer this
     * {@code Activity}.
     */
    @NonNull
    private final List<IntentFilterData> mIntentFilterDatas;

    public PreferredActivity(@NonNull RequiredActivity activity,
            @NonNull List<IntentFilterData> intentFilterDatas) {
        mActivity = activity;
        mIntentFilterDatas = intentFilterDatas;
    }

    @NonNull
    public RequiredActivity getActivity() {
        return mActivity;
    }

    @NonNull
    public List<IntentFilterData> getIntentFilterDatas() {
        return mIntentFilterDatas;
    }

    /**
     * Configure this preferred activity specification for an application.
     *
     * @param packageName the package name of the application
     * @param context the {@code Context} to retrieve system services
     */
    public void configure(@NonNull String packageName, @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName packageActivity = mActivity.getQualifyingComponentForPackage(
                packageName, context);
        if (packageActivity == null) {
            // We might be running into some race condition here, but we can't do anything about it.
            // This should be handled by a future reconciliation started by the package change.
            return;
        }

        int intentFilterDatasSize = mIntentFilterDatas.size();
        for (int i = 0; i < intentFilterDatasSize; i++) {
            IntentFilterData intentFilterData = mIntentFilterDatas.get(i);

            IntentFilter intentFilter = intentFilterData.createIntentFilter();
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
            // PackageManager.replacePreferredActivity() expects filter to have no data authorities,
            // paths, or types; and at most one scheme.
            int match = intentFilterData.getDataScheme() != null
                    ? IntentFilter.MATCH_CATEGORY_SCHEME : IntentFilter.MATCH_CATEGORY_EMPTY;
            List<ComponentName> activities = mActivity.getQualifyingComponentsAsUser(
                    Process.myUserHandle(), context);
            packageManager.replacePreferredActivity(intentFilter, match, activities,
                    packageActivity);
        }
    }

    @Override
    public String toString() {
        return "PreferredActivity{"
                + "mActivity=" + mActivity
                + ", mIntentFilterDatas=" + mIntentFilterDatas
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        PreferredActivity that = (PreferredActivity) object;
        return Objects.equals(mActivity, that.mActivity)
                && Objects.equals(mIntentFilterDatas, that.mIntentFilterDatas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mActivity, mIntentFilterDatas);
    }
}
