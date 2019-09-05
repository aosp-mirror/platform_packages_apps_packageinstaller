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

import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MediatorLiveData} that merges multiple {@link RoleListLiveData} instances.
 */
public class MergeRoleListLiveData extends MediatorLiveData<List<RoleItem>> {

    @NonNull
    private final RoleListLiveData[] mLiveDatas;

    public MergeRoleListLiveData(@NonNull RoleListLiveData... liveDatas) {
        mLiveDatas = liveDatas;

        int liveDatasLength = mLiveDatas.length;
        for (int i = 0; i < liveDatasLength; i++) {
            RoleListLiveData liveData = mLiveDatas[i];

            addSource(liveData, roleItems -> onRoleListChanged());
        }
    }

    private void onRoleListChanged() {
        ArrayMap<String, RoleItem> mergedRoleItemMap = new ArrayMap<>();
        int liveDatasLength = mLiveDatas.length;
        for (int liveDatasIndex = 0; liveDatasIndex < liveDatasLength; liveDatasIndex++) {
            RoleListLiveData liveData = mLiveDatas[liveDatasIndex];

            List<RoleItem> roleItems = liveData.getValue();
            if (roleItems == null) {
                return;
            }
            int roleItemsSize = roleItems.size();
            for (int roleItemsIndex = 0; roleItemsIndex < roleItemsSize; roleItemsIndex++) {
                RoleItem roleItem = roleItems.get(roleItemsIndex);

                String roleName = roleItem.getRole().getName();
                RoleItem mergedRoleItem = mergedRoleItemMap.get(roleName);
                if (mergedRoleItem == null) {
                    mergedRoleItem = new RoleItem(roleItem.getRole(), new ArrayList<>(
                            roleItem.getHolderApplicationInfos()));
                    mergedRoleItemMap.put(roleName, mergedRoleItem);
                } else {
                    mergedRoleItem.getHolderApplicationInfos().addAll(
                            roleItem.getHolderApplicationInfos());
                }
            }
        }

        List<RoleItem> mergedRoleItems = new ArrayList<>(mergedRoleItemMap.values());
        setValue(mergedRoleItems);
    }
}
