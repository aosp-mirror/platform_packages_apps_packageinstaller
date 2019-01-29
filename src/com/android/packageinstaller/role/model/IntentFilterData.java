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

import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Specifies an {@code Intent} or an {@code IntentFilter} for matching application components.
 */
public class IntentFilterData {

    /**
     * The action of this {@code Intent} or {@code IntentFilter} specification. Exactly one action
     * is required so that we can create a single {@code Intent} with it.
     */
    @NonNull
    private final String mAction;

    /**
     * The categories of the {@code Intent} or {@code IntentFilter} specification. Should not
     * contain {@link Intent#CATEGORY_DEFAULT} as it should be automatically added when used for
     * activities.
     */
    @NonNull
    private final List<String> mCategories;

    /**
     * Optional data scheme of the {@code Intent} or {@code IntentFilter} specification. At most one
     * data scheme is supported so that we can create a single {@code Intent} with it.
     */
    @Nullable
    private final String mDataScheme;

    /**
     * Optional data type of the {@code Intent} or {@code IntentFilter} specification. At most one
     * data type is supported so that we can create a single {@code Intent} with it.
     */
    @Nullable
    private final String mDataType;

    public IntentFilterData(@NonNull String action, @NonNull List<String> categories,
            @Nullable String dataScheme, @Nullable String dataType) {
        mAction = action;
        mCategories = categories;
        mDataScheme = dataScheme;
        mDataType = dataType;
    }

    @NonNull
    public String getAction() {
        return mAction;
    }

    @NonNull
    public List<String> getCategories() {
        return mCategories;
    }

    @Nullable
    public String getDataScheme() {
        return mDataScheme;
    }

    @Nullable
    public String getDataType() {
        return mDataType;
    }

    /**
     * Create an {@code Intent} with this specification.
     *
     * @return the {@code Intent} created
     */
    @NonNull
    public Intent createIntent() {
        Intent intent = new Intent(mAction);
        Uri data = mDataScheme != null ? Uri.fromParts(mDataScheme, "", null) : null;
        int categoriesSize = mCategories.size();
        for (int i = 0; i < categoriesSize; i++) {
            String category = mCategories.get(i);
            intent.addCategory(category);
        }
        intent.setDataAndType(data, mDataType);
        return intent;
    }

    /**
     * Create an {@code IntentFilter} with this specification.
     *
     * @return the {@code IntentFilter} created
     */
    @NonNull
    public IntentFilter createIntentFilter() {
        IntentFilter intentFilter = new IntentFilter(mAction);
        int categoriesSize = mCategories.size();
        for (int i = 0; i < categoriesSize; i++) {
            String category = mCategories.get(i);
            intentFilter.addCategory(category);
        }
        if (mDataScheme != null) {
            intentFilter.addDataScheme(mDataScheme);
        }
        if (mDataType != null) {
            try {
                intentFilter.addDataType(mDataType);
            } catch (IntentFilter.MalformedMimeTypeException e) {
                // Should have been validated when parsing roles.
                throw new IllegalStateException(e);
            }
        }
        return intentFilter;
    }

    @Override
    public String toString() {
        return "IntentFilterData{"
                + "mAction='" + mAction + '\''
                + ", mCategories='" + mCategories + '\''
                + ", mDataScheme='" + mDataScheme + '\''
                + ", mDataType='" + mDataType + '\''
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
        IntentFilterData that = (IntentFilterData) object;
        return Objects.equals(mAction, that.mAction)
                && Objects.equals(mCategories, that.mCategories)
                && Objects.equals(mDataScheme, that.mDataScheme)
                && Objects.equals(mDataType, that.mDataType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAction, mCategories, mDataScheme, mDataType);
    }
}
