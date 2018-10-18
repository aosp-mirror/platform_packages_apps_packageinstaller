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

import static android.content.Intent.ACTION_MANAGE_PERMISSIONS;
import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_KEY;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_KEYWORDS;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_RANK;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_TITLE;

import static com.android.packageinstaller.permission.model.PermissionGroups.getAllPermissionGroups;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexablesProvider;

import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.permissioncontroller.R;

import java.util.List;

public class PermissionSearchIndexablesProvider extends SearchIndexablesProvider {
    private static final String OS_PKG = "android";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        return new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        List<PermissionGroup> permissionGroups = getAllPermissionGroups(getContext(), null);
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);

        int numPermissionGroups = permissionGroups.size();
        for (int i = 0; i < numPermissionGroups; i++) {
            PermissionGroup group = permissionGroups.get(i);

            if (OS_PKG.equals(group.getDeclaringPackage())) {
                CharSequence label = group.getLabel();

                cursor.newRow().add(COLUMN_RANK, 0)
                        .add(COLUMN_TITLE, label)
                        .add(COLUMN_KEYWORDS, label + ", "
                                + getContext().getString(R.string.permission_search_keyword))
                        .add(COLUMN_KEY, getContext().getPackageName() + " - " + group.getName())
                        .add(COLUMN_INTENT_ACTION, ACTION_MANAGE_PERMISSIONS);
            }
        }

        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        return new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
    }
}
