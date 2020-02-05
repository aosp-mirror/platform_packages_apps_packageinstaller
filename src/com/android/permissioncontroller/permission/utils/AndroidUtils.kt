/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper

/**
 * Gets an [Application] instance form a regular [Context]
 */
val Context.application: Application get() = when (this) {
    is Activity -> application
    is Service -> application
    is ContextWrapper -> baseContext.application
    else -> applicationContext as Application
}

/**
 * Assert that an operation is running on main thread
 */
fun ensureMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) {
    "Only meant to be used on the main thread"
}
