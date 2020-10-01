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

package com.android.permissioncontroller.permission.service;


import android.content.Intent;
import android.permission.PermissionControllerService;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

/**
 * A slightly modified version of the AndroidX LifecycleService. The only change is there is no
 * onBind override, since it is final in PermissionControllerService.
 */
public abstract class PermissionControllerLifecycleService extends
        PermissionControllerService implements LifecycleOwner {

    private final ServiceLifecycleDispatcher mDispatcher = new ServiceLifecycleDispatcher(this);

    @CallSuper
    @Override
    public void onCreate() {
        mDispatcher.onServicePreSuperOnCreate();
        super.onCreate();
    }

    @SuppressWarnings("deprecation")
    @CallSuper
    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        mDispatcher.onServicePreSuperOnStart();
        super.onStart(intent, startId);
    }

    /**
      * onBind is final in PermissionControllerService, so we have to wait for a request to set
      * our state to "started".
     */
    void setLifecycleToStarted() {
        mDispatcher.onServicePreSuperOnBind();
    }

    /**
     * This method is added only to annotate it with @CallSuper.
     * In usual service super.onStartCommand is no-op, but in LifecycleService
     * it results in mDispatcher.onServicePreSuperOnBind() call, because
     * super.onStartCommand calls onStart().
     */
    @CallSuper
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @CallSuper
    @Override
    public boolean onUnbind(@Nullable Intent intent) {
        mDispatcher.onServicePreSuperOnUnbind();
        return true;
    }

    @CallSuper
    @Override
    public void onRebind(@Nullable Intent intent) {
        mDispatcher.onServicePreSuperOnBind();
    }

    @CallSuper
    @Override
    public void onDestroy() {
        mDispatcher.onServicePreSuperOnDestroy();
        super.onDestroy();
    }

    @Override
    @NonNull
    public Lifecycle getLifecycle() {
        return mDispatcher.getLifecycle();
    }
}
