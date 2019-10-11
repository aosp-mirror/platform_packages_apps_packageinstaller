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

package com.android.packageinstaller.role.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.icu.text.Collator;
import android.os.UserHandle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import com.android.packageinstaller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A function for {@link androidx.lifecycle.Transformations#map(androidx.lifecycle.LiveData,
 * Function)} that sorts a live data for role.
 */
public class RoleSortFunction implements Function<List<Pair<ApplicationInfo, Boolean>>,
        List<Pair<ApplicationInfo, Boolean>>> {

    @NonNull
    private final Comparator<Pair<ApplicationInfo, Boolean>> mComparator;

    public RoleSortFunction(@NonNull Context context) {
        Collator collator = Collator.getInstance(context.getResources().getConfiguration()
                .getLocales().get(0));
        Comparator<Pair<ApplicationInfo, Boolean>> labelComparator = Comparator.comparing(role ->
                Utils.getAppLabel(role.first, context), collator);
        Comparator<Pair<ApplicationInfo, Boolean>> userIdComparator = Comparator.comparingInt(role
                -> UserHandle.getUserHandleForUid(role.first.uid).getIdentifier());
        mComparator = labelComparator.thenComparing(userIdComparator);
    }

    @NonNull
    @Override
    public List<Pair<ApplicationInfo, Boolean>> apply(
            @NonNull List<Pair<ApplicationInfo, Boolean>> input) {
        List<Pair<ApplicationInfo, Boolean>> sorted = new ArrayList<>(input);
        sorted.sort(mComparator);
        return sorted;
    }
}
