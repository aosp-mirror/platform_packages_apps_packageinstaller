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

import static android.content.Context.MODE_PRIVATE;
import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_KEY;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_KEYWORDS;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_RANK;
import static android.provider.SearchIndexablesContract.RawData.COLUMN_TITLE;

import static com.android.packageinstaller.permission.model.PermissionGroups.getAllPermissionGroups;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexablesProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.model.PermissionGroup;
import com.android.permissioncontroller.R;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class PermissionSearchIndexablesProvider extends SearchIndexablesProvider {
    private static final String OS_PKG = "android";
    private static final String EXTRA_SETTINGS_SEARCH_KEY = ":settings:fragment_args_key";

    public static final String ACTION_MANAGE_PERMISSION_APPS =
            "com.android.permissioncontroller.settingssearch.action.MANAGE_PERMISSION_APPS";
    public static final String ACTION_REVIEW_PERMISSION_USAGE =
            "com.android.permissioncontroller.settingssearch.action.REVIEW_PERMISSION_USAGE";

    private static final String PASSWORD_FILE_NAME = "settings-search-password";
    private static final int PASSWORD_LENGTH = 36;

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
        String password = getPassword(getContext());

        List<PermissionGroup> permissionGroups = getAllPermissionGroups(getContext(), null, false);
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
                        .add(COLUMN_KEY, password + getContext().getPackageName()
                                + "," + group.getName())
                        .add(COLUMN_INTENT_ACTION, ACTION_MANAGE_PERMISSION_APPS);
            }
        }

        cursor.newRow().add(COLUMN_RANK, 0)
                .add(COLUMN_TITLE, getContext().getString(R.string.permission_usage_title))
                .add(COLUMN_KEYWORDS, getContext().getString(R.string.permission_search_keyword))
                .add(COLUMN_KEY, password + getContext().getPackageName()
                        + "," + "permissions usage")
                .add(COLUMN_INTENT_ACTION, ACTION_REVIEW_PERMISSION_USAGE);

        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        return new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
    }

    /**
     * Verify that the intent contains the properties expected from an intent launched from settings
     * search.
     *
     * @param context A context of this app
     * @param intent The intent to verify
     *
     * @return The payload of the intent or {@code null} if there no payload for the action
     */
    public static @Nullable String verifyIntent(@NonNull Context context, @NonNull Intent intent) {
        String key = intent.getStringExtra(EXTRA_SETTINGS_SEARCH_KEY);
        String passwordFromIntent = key.substring(0, PASSWORD_LENGTH);
        String password = getPassword(context);

        if (!passwordFromIntent.equals(password)) {
            throw new SecurityException("password " + passwordFromIntent + " is not valid");
        }

        switch (intent.getAction()) {
            case ACTION_MANAGE_PERMISSION_APPS:
                return key.substring(key.indexOf(",") + 1);
            case ACTION_REVIEW_PERMISSION_USAGE:
                return null;
            default:
                throw new IllegalArgumentException("Not a valid action");
        }
    }

    private static @NonNull String getPassword(@NonNull Context context) {
        try {
            try {
                try (FileInputStream passwordFile = context.openFileInput(PASSWORD_FILE_NAME)) {
                    byte[] password = new byte[PASSWORD_LENGTH];
                    passwordFile.read(password);

                    return new String(password);
                }
            } catch (FileNotFoundException e) {
                String password = UUID.randomUUID().toString();
                try (FileOutputStream passwordFile = context.openFileOutput(PASSWORD_FILE_NAME,
                        MODE_PRIVATE)) {
                    passwordFile.write(password.getBytes());
                }

                return password;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not create new password");
        }
    }
}
