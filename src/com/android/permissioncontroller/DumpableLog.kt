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

package com.android.permissioncontroller

import android.util.Log
import com.android.permissioncontroller.Constants.LOGS_TO_DUMP_FILE
import java.io.File
import java.io.PrintWriter

/**
 * Like {@link Log} but stores the logs in a file which can later be dumped via {@link #dump}
 */
object DumpableLog {
    private const val MAX_FILE_SIZE = 32 * 1024

    private val lock = Any()
    private val file = File(PermissionControllerApplication.get().filesDir, LOGS_TO_DUMP_FILE)

    init {
        file.createNewFile()
    }

    /**
     * Equivalent to {@link Log.v}
     */
    fun v(tag: String, message: String, exception: Exception? = null) {
        Log.v(tag, message, exception)
        addLogToDump("v", tag, message, exception)
    }

    /**
     * Equivalent to {@link Log.d}
     */
    fun d(tag: String, message: String, exception: Exception? = null) {
        Log.d(tag, message, exception)
        addLogToDump("d", tag, message, exception)
    }

    /**
     * Equivalent to {@link Log.i}
     */
    fun i(tag: String, message: String, exception: Exception? = null) {
        Log.i(tag, message, exception)
        addLogToDump("i", tag, message, exception)
    }

    /**
     * Equivalent to {@link Log.w}
     */
    fun w(tag: String, message: String, exception: Exception? = null) {
        Log.w(tag, message, exception)
        addLogToDump("w", tag, message, exception)
    }

    /**
     * Equivalent to {@link Log.e}
     */
    fun e(tag: String, message: String, exception: Exception? = null) {
        Log.e(tag, message, exception)
        addLogToDump("e", tag, message, exception)
    }

    private fun addLogToDump(level: String, tag: String, message: String, exception: Exception?) {
        synchronized(lock) {
            // TODO: Needs to be replaced by proper log rotation
            if (file.length() > MAX_FILE_SIZE) {
                val dump = file.readLines()

                file.writeText("truncated at ${System.currentTimeMillis()}\n")
                dump.subList(dump.size / 2, dump.size).forEach { file.appendText(it + "\n") }
            }

            file.appendText("${System.currentTimeMillis()} $tag:$level $message " +
                    "${exception?.let { it.message + Log.getStackTraceString(it) } ?: ""}\n")
        }
    }

    /**
     * Write the previously logged entries to the print writer.
     *
     * @param pw the writer to dump to
     */
    fun dump(pw: PrintWriter) {
        synchronized(lock) {
            file.forEachLine { pw.println(it) }
        }
    }
}