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

package com.android.packageinstaller.permission.service;

import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_KEY;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_KEYWORDS;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_RANK;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_TITLE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.util.Log;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.util.List;

/**
 * {@link android.provider.SearchIndexablesProvider} for permissions.
 */
public class PermissionSearchIndexablesProvider extends BaseSearchIndexablesProvider {
    private static final String LOG_TAG = PermissionSearchIndexablesProvider.class.getSimpleName();

    public static final String ACTION_MANAGE_PERMISSION_APPS =
            "com.android.permissioncontroller.settingssearch.action.MANAGE_PERMISSION_APPS";

    @Override
    public Cursor queryRawData(String[] projection) {
        Context context = getContext();
        PackageManager pm = context.getPackageManager();

        List<String> permissionGroupNames = Utils.getPlatformPermissionGroups();
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);

        int numPermissionGroups = permissionGroupNames.size();
        for (int i = 0; i < numPermissionGroups; i++) {
            String groupName = permissionGroupNames.get(i);

            CharSequence label = getPermissionGroupLabel(groupName, pm);

            cursor.newRow().add(COLUMN_RANK, 0)
                    .add(COLUMN_TITLE, label)
                    .add(COLUMN_KEYWORDS, label + ", " + context.getString(
                            R.string.permission_search_keyword))
                    .add(COLUMN_KEY, createRawDataKey(groupName, context))
                    .add(COLUMN_INTENT_ACTION, ACTION_MANAGE_PERMISSION_APPS);
        }

        return cursor;
    }

    private CharSequence getPermissionGroupLabel(String groupName, PackageManager pm) {
        try {
            return pm.getPermissionGroupInfo(groupName, 0).loadLabel(pm);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(LOG_TAG, "Cannot find group label for " + groupName, e);
        }
        return null;
    }
}
