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
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.utils.UserUtils;

import java.util.List;

/**
 * {@link ViewModel} for a default app.
 */
public class SpecialAppAccessViewModel extends AndroidViewModel {

    @NonNull
    private final LiveData<List<Pair<ApplicationInfo, Boolean>>> mRoleLiveData;

    @NonNull
    private final ArrayMap<String, ManageRoleHolderStateLiveData> mManageRoleHolderStateLiveDatas =
            new ArrayMap<>();

    private ManageRoleHolderStateObserver mManageRoleHolderStateObserver;

    public SpecialAppAccessViewModel(@NonNull Role role, @NonNull Application application) {
        super(application);

        UserHandle user = Process.myUserHandle();
        RoleLiveData roleLiveData = new RoleLiveData(role, user, application);
        UserHandle workProfile = UserUtils.getWorkProfile(application);
        if (workProfile == null) {
            mRoleLiveData = roleLiveData;
        } else {
            RoleLiveData workRoleLiveData = new RoleLiveData(role, workProfile, application);
            mRoleLiveData = new MergeRoleLiveData(roleLiveData, workRoleLiveData);
        }
    }

    @NonNull
    public LiveData<List<Pair<ApplicationInfo, Boolean>>> getRoleLiveData() {
        return mRoleLiveData;
    }

    /**
     * Observe all the {@link ManageRoleHolderStateLiveData} instances.
     *
     * @param owner the {@link LifecycleOwner} which controls the observer
     * @param observer the observer that will receive the events
     */
    public void observeManageRoleHolderState(@NonNull LifecycleOwner owner,
            @NonNull ManageRoleHolderStateObserver observer) {
        mManageRoleHolderStateObserver = observer;

        int manageRoleHolderStateLiveDatasSize = mManageRoleHolderStateLiveDatas.size();
        for (int i = 0; i < manageRoleHolderStateLiveDatasSize; i++) {
            ManageRoleHolderStateLiveData liveData = mManageRoleHolderStateLiveDatas.valueAt(i);

            liveData.observe(owner, state -> mManageRoleHolderStateObserver
                    .onManageRoleHolderStateChanged(liveData, state));
        }
    }

    /**
     * Get or create a {@link ManageRoleHolderStateLiveData} instance for the specified key.
     *
     * @param key the key for the {@link ManageRoleHolderStateLiveData}
     * @param owner the {@link LifecycleOwner} which controls the observer
     *
     * @return the {@link ManageRoleHolderStateLiveData}
     */
    @NonNull
    public ManageRoleHolderStateLiveData getManageRoleHolderStateLiveData(
            @NonNull String key, @NonNull LifecycleOwner owner) {
        ManageRoleHolderStateLiveData liveData = mManageRoleHolderStateLiveDatas.get(key);
        if (liveData == null) {
            liveData = new ManageRoleHolderStateLiveData();
            ManageRoleHolderStateLiveData finalLiveData = liveData;
            liveData.observe(owner, state -> mManageRoleHolderStateObserver
                    .onManageRoleHolderStateChanged(finalLiveData, state));
            mManageRoleHolderStateLiveDatas.put(key, liveData);
        }
        return liveData;
    }

    /**
     * Observer for multiple {@link ManageRoleHolderStateLiveData} instances.
     */
    public interface ManageRoleHolderStateObserver {

        /**
         * Callback when any {@link ManageRoleHolderStateLiveData} changed.
         *
         * @param liveData the {@link ManageRoleHolderStateLiveData} that changed
         * @param state the state after the change
         */
        void onManageRoleHolderStateChanged(@NonNull ManageRoleHolderStateLiveData liveData,
                int state);
    }

    /**
     * {@link ViewModelProvider.Factory} for {@link SpecialAppAccessViewModel}.
     */
    public static class Factory implements ViewModelProvider.Factory {

        @NonNull
        private Role mRole;

        @NonNull
        private Application mApplication;

        public Factory(@NonNull Role role, @NonNull Application application) {
            mRole = role;
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection unchecked
            return (T) new SpecialAppAccessViewModel(mRole, mApplication);
        }
    }
}
