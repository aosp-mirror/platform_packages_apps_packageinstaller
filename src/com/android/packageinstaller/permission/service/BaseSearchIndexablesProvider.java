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

package com.android.packageinstaller.permission.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexablesContract;
import android.provider.SearchIndexablesProvider;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.Constants;
import com.android.packageinstaller.permission.utils.Utils;

import java.util.Objects;
import java.util.UUID;

/**
 * Base class for {@link SearchIndexablesProvider} inside permission controller, which allows using
 * a password in raw data key and verifying incoming intents afterwards.
 */
public abstract class BaseSearchIndexablesProvider extends SearchIndexablesProvider {

    private static final String LOG_TAG = BaseSearchIndexablesProvider.class.getSimpleName();

    private static final String EXTRA_SETTINGS_SEARCH_KEY = ":settings:fragment_args_key";

    private static final int PASSWORD_LENGTH = 36;

    @NonNull
    private static final Object sPasswordLock = new Object();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor queryXmlResources(@Nullable String[] projection) {
        return new MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS);
    }

    @Nullable
    @Override
    public Cursor queryNonIndexableKeys(@Nullable String[] projection) {
        return new MatrixCursor(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
    }

    @NonNull
    private static String getPassword(@NonNull Context context) {
        synchronized (sPasswordLock) {
            SharedPreferences sharedPreferences = Utils.getDeviceProtectedSharedPreferences(
                    context);
            String password = sharedPreferences.getString(
                    Constants.SEARCH_INDEXABLE_PROVIDER_PASSWORD_KEY, null);
            if (password == null) {
                password = UUID.randomUUID().toString();
                sharedPreferences.edit()
                        .putString(Constants.SEARCH_INDEXABLE_PROVIDER_PASSWORD_KEY, password)
                        .apply();
            }
            return password;
        }
    }

    /**
     * Create a unique raw data key with password.
     *
     * @param key the original key, can be retrieved later with {@link #getOriginalKey(Intent)}
     * @param context the context to use
     * @return the created raw data key
     */
    @NonNull
    protected static String createRawDataKey(@NonNull String key, @NonNull Context context) {
        return getPassword(context) + context.getPackageName() + ',' + key;
    }

    /**
     * Check if the intent contains the properties expected from an intent launched from settings
     * search.
     *
     * @param intent the intent to check
     * @param context the context to get password
     *
     * @return whether the intent is valid
     */
    @CheckResult
    public static boolean isIntentValid(@NonNull Intent intent, @NonNull Context context) {
        String key = intent.getStringExtra(EXTRA_SETTINGS_SEARCH_KEY);
        String passwordFromIntent = key.substring(0, PASSWORD_LENGTH);
        String password = getPassword(context);
        boolean verified = Objects.equals(passwordFromIntent, password);
        if (!verified) {
            Log.w(LOG_TAG, "Invalid password: " + passwordFromIntent);
        }
        return verified;
    }

    /**
     * Get the original key passed to {@link #createRawDataKey(String, Context)}. Should only be
     * called after {@link #isIntentValid(Intent, Context)}.
     *
     * @param intent the intent to get the original key
     *
     * @return the original key from the intent, or {@code null} if none
     */
    @Nullable
    public static String getOriginalKey(@NonNull Intent intent) {
        String key = intent.getStringExtra(EXTRA_SETTINGS_SEARCH_KEY);
        if (key == null) {
            return null;
        }
        int keyStart = key.indexOf(',') + 1;
        return keyStart <= key.length() ? key.substring(keyStart) : null;
    }
}
