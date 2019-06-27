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

package com.android.packageinstaller.permission.model;

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads all permission usages for a set of apps and permission groups.
 */
public final class PermissionUsages implements LoaderCallbacks<List<AppPermissionUsage>> {
    public static final int USAGE_FLAG_LAST = 1 << 0;
    public static final int USAGE_FLAG_HISTORICAL = 1 << 2;

    private final ArrayList<AppPermissionUsage> mUsages = new ArrayList<>();
    private final @NonNull Context mContext;

    private static final String KEY_FILTER_UID =  "KEY_FILTER_UID";
    private static final String KEY_FILTER_PACKAGE_NAME =  "KEY_FILTER_PACKAGE_NAME";
    private static final String KEY_FILTER_PERMISSION_GROUP =  "KEY_FILTER_PERMISSION_GROUP";
    private static final String KEY_FILTER_BEGIN_TIME_MILLIS =  "KEY_FILTER_BEGIN_TIME_MILLIS";
    private static final String KEY_FILTER_END_TIME_MILLIS =  "KEY_FILTER_END_TIME_MILLIS";
    private static final String KEY_USAGE_FLAGS =  "KEY_USAGE_FLAGS";
    private static final String KEY_GET_UI_INFO =  "KEY_GET_UI_INFO";
    private static final String KEY_GET_NON_PLATFORM_PERMISSIONS =
            "KEY_GET_NON_PLATFORM_PERMISSIONS";

    private @Nullable PermissionsUsagesChangeCallback mCallback;

    public interface PermissionsUsagesChangeCallback {
        void onPermissionUsagesChanged();
    }

    public PermissionUsages(@NonNull Context context) {
        mContext = context;
    }

    public void load(@Nullable String filterPackageName,
            @Nullable String[] filterPermissionGroups, long filterBeginTimeMillis,
            long filterEndTimeMillis, int usageFlags, @NonNull LoaderManager loaderManager,
            boolean getUiInfo, boolean getNonPlatformPermissions,
            @NonNull PermissionsUsagesChangeCallback callback, boolean sync) {
        load(Process.INVALID_UID, filterPackageName, filterPermissionGroups, filterBeginTimeMillis,
                filterEndTimeMillis, usageFlags, loaderManager, getUiInfo,
                getNonPlatformPermissions, callback, sync);
    }

    public void load(int filterUid, @Nullable String filterPackageName,
            @Nullable String[] filterPermissionGroups, long filterBeginTimeMillis,
            long filterEndTimeMillis, int usageFlags, @NonNull LoaderManager loaderManager,
            boolean getUiInfo, boolean getNonPlatformPermissions,
            @NonNull PermissionsUsagesChangeCallback callback, boolean sync) {
        mCallback = callback;
        final Bundle args = new Bundle();
        args.putInt(KEY_FILTER_UID, filterUid);
        args.putString(KEY_FILTER_PACKAGE_NAME, filterPackageName);
        args.putStringArray(KEY_FILTER_PERMISSION_GROUP, filterPermissionGroups);
        args.putLong(KEY_FILTER_BEGIN_TIME_MILLIS, filterBeginTimeMillis);
        args.putLong(KEY_FILTER_END_TIME_MILLIS, filterEndTimeMillis);
        args.putInt(KEY_USAGE_FLAGS, usageFlags);
        args.putBoolean(KEY_GET_UI_INFO, getUiInfo);
        args.putBoolean(KEY_GET_NON_PLATFORM_PERMISSIONS, getNonPlatformPermissions);
        if (sync) {
            final UsageLoader loader = new UsageLoader(mContext, args);
            final List<AppPermissionUsage> usages = loader.loadInBackground();
            onLoadFinished(loader, usages);
        } else {
            loaderManager.restartLoader(1, args, this);
        }
    }

    @Override
    public Loader<List<AppPermissionUsage>> onCreateLoader(int id, Bundle args) {
        return new UsageLoader(mContext, args);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<AppPermissionUsage>> loader,
            List<AppPermissionUsage> usages) {
        mUsages.clear();
        mUsages.addAll(usages);
        if (mCallback != null) {
            mCallback.onPermissionUsagesChanged();
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<AppPermissionUsage>> loader) {
        mUsages.clear();
        mCallback.onPermissionUsagesChanged();
    }

    public @NonNull List<AppPermissionUsage> getUsages() {
        return mUsages;
    }

    public void stopLoader(@NonNull LoaderManager loaderManager) {
        loaderManager.destroyLoader(1);
    }

    public static @Nullable AppPermissionUsage.GroupUsage loadLastGroupUsage(
            @NonNull Context context, @NonNull AppPermissionGroup group) {
        return null;
    }

    private static final class UsageLoader extends AsyncTaskLoader<List<AppPermissionUsage>> {
        private final int mFilterUid;
        private @Nullable String mFilterPackageName;
        private @Nullable String[] mFilterPermissionGroups;
        private final long mFilterBeginTimeMillis;
        private final long mFilterEndTimeMillis;
        private final int mUsageFlags;
        private final boolean mGetUiInfo;
        private final boolean mGetNonPlatformPermissions;

        UsageLoader(@NonNull Context context, @NonNull Bundle args) {
            super(context);
            mFilterUid = args.getInt(KEY_FILTER_UID);
            mFilterPackageName = args.getString(KEY_FILTER_PACKAGE_NAME);
            mFilterPermissionGroups = args.getStringArray(KEY_FILTER_PERMISSION_GROUP);
            mFilterBeginTimeMillis = args.getLong(KEY_FILTER_BEGIN_TIME_MILLIS);
            mFilterEndTimeMillis = args.getLong(KEY_FILTER_END_TIME_MILLIS);
            mUsageFlags = args.getInt(KEY_USAGE_FLAGS);
            mGetUiInfo = args.getBoolean(KEY_GET_UI_INFO);
            mGetNonPlatformPermissions = args.getBoolean(KEY_GET_NON_PLATFORM_PERMISSIONS);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public @NonNull List<AppPermissionUsage> loadInBackground() {
            return Collections.emptyList();
        }
    }
}
