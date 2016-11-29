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

import android.os.SystemProperties;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * SystemProperties with configurable build type
 */
@Implements(SystemProperties.class)
public class ShadowSystemProperties extends org.robolectric.shadows.ShadowSystemProperties {
    /** If the build type is 'user' */
    private static boolean sIsUserBuild = false;

    /**
     * Set the build type to user of eng.
     *
     * @param isUserBuild if the build type should be user
     */
    public static void setUserBuild(boolean isUserBuild) {
        sIsUserBuild = isUserBuild;
    }

    @Implementation
    public static String get(String key) {
        if ("ro.build.type".equals(key)) {
            if (sIsUserBuild) {
                return "user";
            } else {
                return "eng";
            }
        } else {
            return org.robolectric.shadows.ShadowSystemProperties.get(key);
        }
    }
}
