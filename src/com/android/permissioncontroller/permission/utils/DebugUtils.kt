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

import java.util.Collections.reverse

/**
 * A short version of the permission-only stack trace, suitable to use in debug logs.
 *
 * See [toShortString]
 */
fun shortStackTrace() = permissionsStackTrace().toShortString()

/**
 * [StackTraceElement]s of only the permission-related frames
 */
fun permissionsStackTrace() = stackTraceWithin("com.android.permissioncontroller")
    .dropLastWhile { it.className.contains(".DebugUtils") }

/**
 * [StackTraceElement]s of only frames who's [full class name][StackTraceElement.getClassName]
 * starts with [pkgPrefix]
 */
fun stackTraceWithin(pkgPrefix: String) = Thread
    .currentThread()
    .stackTrace
    .dropWhile {
        !it.className.startsWith(pkgPrefix)
    }.takeWhile {
        it.className.startsWith(pkgPrefix)
    }

/**
 * Renders a stack trace slice to a short-ish single-line string.
 *
 * Suitable for debugging when full stack trace can be too spammy.
 */
fun List<StackTraceElement>.toShortString(): String {
    reverse(this)
    return joinToString(" -> ") {
        val fullSimpleClassName = it.className.substringAfterLast(".")
        var simpleClassName = fullSimpleClassName.substringAfterLast("\$")
        if (simpleClassName.isNotEmpty() && simpleClassName[0].isDigit()) {
            simpleClassName = fullSimpleClassName
        }
        "$simpleClassName.${it.methodName}:${it.lineNumber}"
    }
}
