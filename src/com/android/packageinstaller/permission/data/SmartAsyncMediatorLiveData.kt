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

package com.android.packageinstaller.permission.data

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.AsyncTask.THREAD_POOL_EXECUTOR

/**
 * A LiveData which loads its data in a background AsyncTask. It will cancel current tasks, if new
 * requests come during execution
 */
abstract class SmartAsyncMediatorLiveData<T> : SmartUpdateMediatorLiveData<T>() {

    private var backgroundLoadTask = BackgroundLoadTask()

    /**
     * The main function which will load data. It should periodically check isCancelled to see if
     * it should stop working.
     */
    abstract fun loadData(isCancelled: () -> Boolean): T?

    open fun updateAsync() {
        stopTaskIfRunningAndGetNewTask()
        backgroundLoadTask.executeOnExecutor(THREAD_POOL_EXECUTOR)
    }

    private fun stopTaskIfRunningAndGetNewTask() {
        if (backgroundLoadTask.status == AsyncTask.Status.RUNNING) {
            backgroundLoadTask.cancel(false)
        }
        backgroundLoadTask = BackgroundLoadTask()
    }

    override fun onInactive() {
        stopTaskIfRunningAndGetNewTask()
        super.onInactive()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class BackgroundLoadTask : AsyncTask<Void, Void, T>() {
        override fun doInBackground(vararg p0: Void?): T? {
            return loadData { isCancelled }
        }

        override fun onPostExecute(result: T?) {
            value = result
        }
    }
}