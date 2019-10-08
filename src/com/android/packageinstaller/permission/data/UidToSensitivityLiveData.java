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

package com.android.packageinstaller.permission.data;

import static android.os.UserHandle.getUserHandleForUid;

import android.app.Application;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.SparseArray;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;

/**
 * Live data of the user sensitivity of all uids that belong to the current profile group.
 *
 * <p>Data source {@link PerUserUidToSensitivityLiveData}, {@link UsersLiveData}
 */
class UidToSensitivityLiveData extends MediatorLiveData<SparseArray<ArrayMap<String, Integer>>> {
    private static UidToSensitivityLiveData sInstance;

    /** Data sources, one per user in the same profile group */
    private final ArrayMap<UserHandle, PerUserUidToSensitivityLiveData> mUsersToLiveData =
            new ArrayMap<>();

    /**
     * Combined uid sensitivity data from all users
     *
     * <p>{@code uid -> permission -> flags}
     */
    private final SparseArray<ArrayMap<String, Integer>> mUidToSensitivity = new SparseArray<>();

    /**
     * Get a (potentially shared) live data.
     *
     * @param application The application context
     *
     * @return The live data
     */
    @MainThread
    public static UidToSensitivityLiveData get(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new UidToSensitivityLiveData(application);
        }

        return sInstance;
    }

    private UidToSensitivityLiveData(@NonNull Application application) {
        addSource(UsersLiveData.get(application), users -> {
            int numPreviousUsers = mUsersToLiveData.size();
            for (int i = numPreviousUsers - 1; i >= 0; i--) {
                if (!users.contains(mUsersToLiveData.keyAt(i))) {
                    removeSource(mUsersToLiveData.valueAt(i));
                    mUsersToLiveData.removeAt(i);
                }
            }

            int numNewUsers = users.size();
            for (int i = 0; i < numNewUsers; i++) {
                UserHandle user = users.get(i);

                if (!mUsersToLiveData.containsKey(user)) {
                    PerUserUidToSensitivityLiveData newSource = PerUserUidToSensitivityLiveData.get(
                            user, application);
                    mUsersToLiveData.put(user, newSource);

                    addSource(newSource, data -> {
                        int numUids = mUidToSensitivity.size();

                        // remove all entries from this user
                        for (int uidNum = numUids - 1; uidNum >= 0; uidNum--) {
                            if (getUserHandleForUid(mUidToSensitivity.keyAt(uidNum)).equals(user)) {
                                mUidToSensitivity.removeAt(uidNum);
                            }
                        }

                        // Add new entries for this user
                        numUids = data.size();
                        for (int uidNum = 0; uidNum < numUids; uidNum++) {
                            mUidToSensitivity.put(data.keyAt(uidNum), data.valueAt(uidNum));
                        }
                        setValue(mUidToSensitivity);
                    });
                }
            }
        });

    }
}
