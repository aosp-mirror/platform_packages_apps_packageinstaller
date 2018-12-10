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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Specifies the value of a meta data for an application to qualify for a {@link Role}.
 */
public class RequiredMetaData {

    /**
     * The name of this meta data.
     */
    @NonNull
    private final String mName;

    /**
     * The value of this meta data.
     */
    @Nullable
    private final Object mValue;

    /**
     * Whether this meta data is optional.
     *
     * @see #isQualified(Bundle)
     */
    private final boolean mOptional;

    public RequiredMetaData(@NonNull String name, @Nullable Object value, boolean optional) {
        mName = name;
        mValue = value;
        mOptional = optional;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @Nullable
    public Object getValue() {
        return mValue;
    }

    public boolean isOptional() {
        return mOptional;
    }

    /**
     * Check whether the meta data of a component is qualified.
     *
     * @param metaData the meta data of the component
     *
     * @return whether the meta data of the component is qualified
     */
    public boolean isQualified(@NonNull Bundle metaData) {
        if (metaData.containsKey(mName)) {
            Object metaDataValue = metaData.get(mName);
            return Objects.equals(metaDataValue, mValue);
        } else {
            return mOptional;
        }
    }

    @Override
    public String toString() {
        return "RequiredMetaData{"
                + "mName='" + mName + '\''
                + ", mValue=" + mValue
                + ", mOptional=" + mOptional
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
        RequiredMetaData that = (RequiredMetaData) object;
        return mOptional == that.mOptional
                && Objects.equals(mName, that.mName)
                && Objects.equals(mValue, that.mValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mValue, mOptional);
    }
}
