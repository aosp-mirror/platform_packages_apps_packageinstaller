/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller;

/**
 * App-global constants
 */
public class Constants {
    /**
     * ID for the periodic job in
     * {@link com.android.packageinstaller.permission.service.LocationAccessCheck}.
     */
    public static final int PERIODIC_LOCATION_ACCESS_CHECK_JOB_ID = 0;

    /**
     * ID for the on-demand, but delayed job in
     * {@link com.android.packageinstaller.permission.service.LocationAccessCheck}.
     */
    public static final int LOCATION_ACCESS_CHECK_JOB_ID = 1;

    /**
     * Name of file to containing the packages we already showed a notificaiton for.
     *
     * @see com.android.packageinstaller.permission.service.LocationAccessCheck
     */
    public static final String LOCATION_ACCESS_CHECK_ALREADY_NOTIFIED_FILE =
            "packages_already_notified_location_access";

    /**
     * ID for notification shown by
     * {@link com.android.packageinstaller.permission.service.LocationAccessCheck}.
     */
    public static final int LOCATION_ACCESS_CHECK_NOTIFICATION_ID = 0;

    /**
     * Channel of the notifications shown by
     * {@link com.android.packageinstaller.permission.service.LocationAccessCheck}.
     */
    public static final String PERMISSION_REMINDER_CHANNEL_ID = "permission reminders";

    /**
     * Name of generic shared preferences file.
     */
    public static final String PREFERENCES_FILE = "preferences";

    /**
     * Key in the generic shared preferences that stores when the last notification was shown by
     * {@link com.android.packageinstaller.permission.service.LocationAccessCheck}
     */
    public static final String KEY_LAST_LOCATION_ACCESS_NOTIFICATION_SHOWN =
            "last_location_access_notification_shown";
}
