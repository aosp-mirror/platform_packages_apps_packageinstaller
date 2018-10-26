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

import android.util.ArraySet;

/**
 * Utility methods for dealing with {@link java.util.Collection}s.
 */
public final class CollectionUtils {

    private CollectionUtils() {}

    /**
     * Remove all values in the array set that do <b>not</b> exist in the given collection.
     *
     * @param <T> the class of the elements to retain and of the {@code ArraySet}
     * @param arraySet the {@code ArraySet} whose elements are to be removed or retained
     * @param valuesToRetain the values to be used to determine which elements to retain
     *
     * @return {@code true} if any values were removed from the array set, {@code false} otherwise.
     *
     * @see ArraySet#retainAll(java.util.Collection)
     */
    @SafeVarargs
    public static <T> boolean retainAll(ArraySet<T> arraySet, T... valuesToRetain) {
        boolean removed = false;
        for (int i = arraySet.size() - 1; i >= 0; i--) {
            if (!ArrayUtils.contains(valuesToRetain, arraySet.valueAt(i))) {
                arraySet.removeAt(i);
                removed = true;
            }
        }
        return removed;
    }
}
