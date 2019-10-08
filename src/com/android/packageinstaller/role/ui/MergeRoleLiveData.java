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

import android.content.pm.ApplicationInfo;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MediatorLiveData} that merges multiple {@link RoleLiveData} instances.
 */
public class MergeRoleLiveData extends MediatorLiveData<List<Pair<ApplicationInfo, Boolean>>> {

    @NonNull
    private final RoleLiveData[] mLiveDatas;

    public MergeRoleLiveData(@NonNull RoleLiveData... liveDatas) {
        mLiveDatas = liveDatas;

        int liveDatasLength = mLiveDatas.length;
        for (int i = 0; i < liveDatasLength; i++) {
            RoleLiveData liveData = mLiveDatas[i];

            addSource(liveData, roleItems -> onRoleChanged());
        }
    }

    private void onRoleChanged() {
        List<Pair<ApplicationInfo, Boolean>> mergedQualifyingApplications = new ArrayList<>();
        int liveDatasLength = mLiveDatas.length;
        for (int i = 0; i < liveDatasLength; i++) {
            RoleLiveData liveData = mLiveDatas[i];

            List<Pair<ApplicationInfo, Boolean>> qualifyingApplications = liveData.getValue();
            if (qualifyingApplications == null) {
                return;
            }
            mergedQualifyingApplications.addAll(qualifyingApplications);
        }

        setValue(mergedQualifyingApplications);
    }
}
