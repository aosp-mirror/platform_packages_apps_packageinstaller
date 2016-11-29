/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.packageinstaller.shadows;

import android.content.Context;

import com.android.internal.logging.MetricsLogger;

import libcore.util.Objects;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;

/**
 * MetricsLogger that just adds logs to a list
 */
@Implements(MetricsLogger.class)
public class ShadowMetricsLogger {
    /** Collected logs */
    private static ArrayList<Log> sLogs = new ArrayList<>();

    /**
     * Clear all previously collected logs
     */
    public static void clearLogs() {
        sLogs.clear();
    }

    /**
     * @return All logs collected since the last {@link #clearLogs()}.
     */
    public static ArrayList<Log> getLogs() {
        return sLogs;
    }

    @Implementation
    public static void action(Context context, int category, String pkg) {
        sLogs.add(new Log(context, category, pkg));
    }

    public static class Log {
        public final Context context;
        public final int category;
        public final String pkg;

        public Log(Context context, int category, String pkg) {
            this.context = context;
            this.category = category;
            this.pkg = pkg;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof Log)) {
                return false;
            } else {
                Log otherLog = (Log) obj;

                return Objects.equal(otherLog.context, context) && otherLog.category == category
                        && Objects.equal(otherLog.pkg, pkg);
            }
        }

        @Override
        public int hashCode() {
            return ((context == null) ? 0 : context.hashCode()) + category + ((pkg == null) ? 0
                    : pkg.hashCode());
        }
    }
}
