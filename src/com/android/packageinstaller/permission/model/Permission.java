/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.packageinstaller.permission.model;

public final class Permission {
    private final String mName;
    private final int mAppOp;

    private boolean mGranted;
    private boolean mAppOpAllowed;

    public Permission(String name, boolean granted,
            int appOp, boolean appOpAllowed) {
        mName = name;
        mGranted = granted;
        mAppOp = appOp;
        mAppOpAllowed = appOpAllowed;
    }

    public String getName() {
        return mName;
    }

    public int getAppOp() {
        return mAppOp;
    }

    public boolean isGranted() {
        return mGranted;
    }

    public void setGranted(boolean mGranted) {
        this.mGranted = mGranted;
    }

    public boolean isAppOpAllowed() {
        return mAppOpAllowed;
    }

    public void setAppOpAllowed(boolean mAppOpAllowed) {
        this.mAppOpAllowed = mAppOpAllowed;
    }
}