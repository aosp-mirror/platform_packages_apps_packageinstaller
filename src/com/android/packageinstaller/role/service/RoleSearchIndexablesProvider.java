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

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Binder;
import android.provider.SearchIndexablesContract;
import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.service.BaseSearchIndexablesProvider;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.permissioncontroller.R;

/**
 * {@link android.provider.SearchIndexablesProvider} for roles.
 */
public class RoleSearchIndexablesProvider extends BaseSearchIndexablesProvider {

    public static final String ACTION_MANAGE_DEFAULT_APP =
            "com.android.permissioncontroller.settingssearch.action.MANAGE_DEFAULT_APP";

    public static final String ACTION_MANAGE_SPECIAL_APP_ACCESS =
            "com.android.permissioncontroller.settingssearch.action.MANAGE_SPECIAL_APP_ACCESS";

    @Nullable
    @Override
    public Cursor queryRawData(@Nullable String[] projection) {
        MatrixCursor cursor = new MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        Context context = getContext();
        ArrayMap<String, Role> roles = Roles.get(context);
        int rolesSize = roles.size();
        for (int i = 0; i < rolesSize; i++) {
            Role role = roles.valueAt(i);

            long token = Binder.clearCallingIdentity();
            try {
                if (!role.isAvailable(context) || !role.isVisible(context)) {
                    continue;
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }

            String label = context.getString(role.getLabelResource());
            boolean isExclusive = role.isExclusive();
            cursor.newRow()
                    .add(SearchIndexablesContract.RawData.COLUMN_RANK, 0)
                    .add(SearchIndexablesContract.RawData.COLUMN_TITLE, label)
                    .add(SearchIndexablesContract.RawData.COLUMN_KEYWORDS, label + ", "
                            + getContext().getString(isExclusive
                            ? R.string.default_app_search_keyword
                            : R.string.special_app_access_search_keyword))
                    .add(SearchIndexablesContract.RawData.COLUMN_KEY, createRawDataKey(
                            role.getName(), context))
                    .add(SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION, isExclusive
                            ? ACTION_MANAGE_DEFAULT_APP : ACTION_MANAGE_SPECIAL_APP_ACCESS);
        }
        return cursor;
    }
}
