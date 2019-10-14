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
     * Key for Notification.Builder.setGroup() for the incident report approval notification.
     */
    public static final String INCIDENT_NOTIFICATION_GROUP_KEY = "incident confirmation";

    /**
     * Key for Notification.Builder.setChannelId() for the incident report approval notification.
     */
    public static final String INCIDENT_NOTIFICATION_CHANNEL_ID = "incident_confirmation";

    /**
     * ID for our notification.  We always post it with a tag which is the uri in string form.
     */
    public static final int INCIDENT_NOTIFICATION_ID = 66900652;

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
     * Key in the generic shared preferences that stores when the location access feature
     * was enabled, specifically when it was picked up by the code managing the feature.
     */
    public static final String KEY_LOCATION_ACCESS_CHECK_ENABLED_TIME =
            "location_access_check_enabled_time";

    /**
     * Key in the generic shared preferences that stores when the last notification was shown by
     * {@link com.android.packageinstaller.permission.service.LocationAccessCheck}
     */
    public static final String KEY_LAST_LOCATION_ACCESS_NOTIFICATION_SHOWN =
            "last_location_access_notification_shown";

    /**
     * Key in the generic shared preferences that stores if the user manually selected the "none"
     * role holder for a role.
     */
    public static final String IS_NONE_ROLE_HOLDER_SELECTED_KEY = "is_none_role_holder_selected:";

    /**
     * Key in the generic shared preferences that stores if the user manually selected the "none"
     * role holder for a role.
     */
    public static final String SEARCH_INDEXABLE_PROVIDER_PASSWORD_KEY =
            "search_indexable_provider_password";

    /**
     * Key in the generic shared preferences that stores the name of the packages that are currently
     * have an overridden user sensitivity.
     */
    public static final String FORCED_USER_SENSITIVE_UIDS_KEY = "forced_user_sensitive_uids_key";

    /**
     * Key in the generic shared preferences that stores if all packages should be considered user
     * sensitive
     */
    public static final String ALLOW_OVERRIDE_USER_SENSITIVE_KEY =
            "allow_override_user_sensitive_key";

    /**
     * Key in the generic shared preferences that controls if the
     * {@link android.Manifest.permission#RECORD_AUDIO} of the currently registered assistant is
     * user sensitive.
     */
    public static final String ASSISTANT_RECORD_AUDIO_IS_USER_SENSITIVE_KEY =
            "assistant_record_audio_is_user_sensitive_key";

    /**
     * Name of file containing the permissions that should be restored, but have not been restored
     * yet.
     */
    public static final String DELAYED_RESTORE_PERMISSIONS_FILE = "delayed_restore_permissions.xml";

    /**
     * Name of file containing the user denied status for requesting roles.
     */
    public static final String REQUEST_ROLE_USER_DENIED_FILE = "request_role_user_denied";

    /**
     * Key in the user denied status for requesting roles shared preferences that stores a string
     * set for the names of the roles that an application has been denied for once.
     */
    public static final String REQUEST_ROLE_USER_DENIED_ONCE_KEY_PREFIX = "denied_once:";

    /**
     * Key in the user denied status for requesting roles shared preferences that stores a string
     * set for the names of the roles that an application is always denied for.
     */
    public static final String REQUEST_ROLE_USER_DENIED_ALWAYS_KEY_PREFIX = "denied_always:";

    /**
     * Intent extra used to pass current sessionId between Permission Controller fragments.
     */
    public static final String EXTRA_SESSION_ID =
            "com.android.packageinstaller.extra.SESSION_ID";

    /**
     * Invalid session id.
     */
    public static final long INVALID_SESSION_ID = 0;
}
