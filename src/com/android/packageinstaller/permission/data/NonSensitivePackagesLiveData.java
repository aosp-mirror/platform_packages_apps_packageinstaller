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

import static com.android.packageinstaller.permission.utils.Utils.FLAGS_ALWAYS_USER_SENSITIVE;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;

/**
 * Live data of packages that are not fully user sensitive by default.
 *
 * <p>Data source: {@link UidToSensitivityLiveData}
 */
public class NonSensitivePackagesLiveData extends MediatorLiveData<ArrayList<ApplicationInfo>> {
    private static NonSensitivePackagesLiveData sInstance;

    /**
     * Get a (potentially shared) live data.
     *
     * @param application The application context
     *
     * @return The live data
     */
    @MainThread
    public static NonSensitivePackagesLiveData get(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new NonSensitivePackagesLiveData(application);
        }

        return sInstance;
    }

    private NonSensitivePackagesLiveData(@NonNull Application application) {
        UidToSensitivityLiveData uidLiveData = UidToSensitivityLiveData.get(application);

        addSource(uidLiveData, uidToSensitivity -> AsyncTask.execute(() -> {
            PackageManager pm = application.getPackageManager();

            ArrayList<ApplicationInfo> pkgs = new ArrayList<>();

            int numUids = uidToSensitivity.size();
            for (int uidNum = 0; uidNum < numUids; uidNum++) {
                int uid = uidToSensitivity.keyAt(uidNum);
                UserHandle user = getUserHandleForUid(uid);
                ArrayMap<String, Integer> sensitivity = uidToSensitivity.valueAt(uidNum);

                int numPerms = sensitivity.size();
                for (int permNum = 0; permNum < numPerms; permNum++) {
                    if (sensitivity.valueAt(permNum) != FLAGS_ALWAYS_USER_SENSITIVE) {
                        String[] uidPkgs = pm.getPackagesForUid(uid);

                        if (uidPkgs != null) {
                            for (String pkg : uidPkgs) {
                                ApplicationInfo appInfo;
                                try {
                                    appInfo = pm.getApplicationInfoAsUser(pkg, 0, user);
                                } catch (PackageManager.NameNotFoundException e) {
                                    continue;
                                }

                                pkgs.add(appInfo);
                            }
                        }
                        break;
                    }
                }

            }

            postValue(pkgs);
        }));
    }
}
