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

import android.app.Application;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.android.packageinstaller.role.utils.UserUtils;

import java.util.List;

/**
 * {@link ViewModel} for the list of special app accesses.
 */
public class SpecialAppAccessListViewModel extends AndroidViewModel {

    @NonNull
    private final LiveData<List<RoleItem>> mLiveData;

    public SpecialAppAccessListViewModel(@NonNull Application application) {
        super(application);

        UserHandle user = Process.myUserHandle();
        RoleListLiveData liveData = new RoleListLiveData(false, user, application);
        UserHandle workProfile = UserUtils.getWorkProfile(application);
        RoleListSortFunction sortFunction = new RoleListSortFunction(application);
        if (workProfile == null) {
            mLiveData = Transformations.map(liveData, sortFunction);
        } else {
            RoleListLiveData workLiveData = new RoleListLiveData(false, workProfile, application);
            mLiveData = Transformations.map(new MergeRoleListLiveData(liveData, workLiveData),
                    sortFunction);
        }
    }

    @NonNull
    public LiveData<List<RoleItem>> getLiveData() {
        return mLiveData;
    }
}
