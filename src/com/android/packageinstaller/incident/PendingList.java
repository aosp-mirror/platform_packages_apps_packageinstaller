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

package com.android.packageinstaller.incident;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IncidentManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.packageinstaller.Constants;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Represents the current list of pending records.
 */
class PendingList {
    private static final String TAG = "PermissionController.incident";

    /**
     * Flag for {@link #UpdateState} to flag whether this update is coming from the
     * notification handling.  If it is, then no dialogs will be shown.
     */
    static final int FLAG_FROM_NOTIFICATION = 0x1;

    /**
     * Shared preferences file name.
     */
    private static final String SHARED_PREFS_NAME =
            "com.android.packageinstaller.incident.PendingList";

    /**
     * Key for the list of currently showing notifications.
     */
    private static final String SHARED_PREFS_KEY_NOTIFICATIONS = "notifications";

    /**
     * Singleton instance.
     */
    private static final PendingList sInstance = new PendingList();

    /**
     * Date format that will sort lexicographical, so we can have our notifications sorted.
     */
    private static final SimpleDateFormat sDateFormatter =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * List of currently pending records.
     */
    private static class Rec {
        /**
         * Constructor.
         */
        Rec(IncidentManager.PendingReport r, String l) {
            this.report = r;
            this.label = l;
        }

        /**
         * The incident report to show.
         */
        public final IncidentManager.PendingReport report;

        /**
         * The user-visible name of the entry.
         */
        public final String label;
    }

    /**
     * Class to update the state.  Holds the Context, and other system services for
     * the duration of the update.
     */
    private static class Updater {
        private final Context mContext;
        private final int mFlags;
        private final NotificationManager mNm;
        private final Formatting mFormatting;
        private Collator mCollator;

        /**
         * Constructor.
         */
        Updater(Context context, int flags) {
            mContext = context;
            mFlags = flags;
            mNm = context.getSystemService(NotificationManager.class);
            mFormatting = new Formatting(context);
            mCollator = Collator.getInstance(
                context.getResources().getConfiguration().getLocales().get(0));
        }

        /**
         * Perform the update.
         */
        void updateState() {
            final IncidentManager incidentManager =
                    mContext.getSystemService(IncidentManager.class);
            final List<IncidentManager.PendingReport> reports = incidentManager.getPendingReports();

            // Load whatever we previously displayed.  This may result in some spurious
            // cancel calls across reboots... but that's not an actual problem.
            final SharedPreferences prefs = mContext.getSharedPreferences(SHARED_PREFS_NAME,
                    Context.MODE_PRIVATE);
            final Set<String> prevNotifications =
                    prefs.getStringSet(SHARED_PREFS_KEY_NOTIFICATIONS, null);
            final ArraySet<String> remainingNotifications = new ArraySet<String>();
            if (prevNotifications != null) {
                for (final String s: prevNotifications) {
                    remainingNotifications.add(s);
                }
            }
            final ArraySet<String> currentNotifications = new ArraySet<String>();

            // Load everything we will need for display
            final List<Rec> recs = new ArrayList();
            final int recCount = reports.size();
            for (int i = 0; i < recCount; i++) {
                final IncidentManager.PendingReport report = reports.get(i);
                final String label = mFormatting.getAppLabel(report.getRequestingPackage());
                if (label == null) {
                    Log.w(TAG, "Application (or its label) could not be found. Summarily "
                            + " denying report: " + report.getRequestingPackage());
                    incidentManager.denyReport(report.getUri());
                    continue;
                }

                recs.add(new Rec(report, label));
            }

            // Sort by timestamp, then by label name (for a stable ordering, with the assumption
            // that apps only post one at a time).
            recs.sort((a, b) -> {
                long val = a.report.getTimestamp() - b.report.getTimestamp();
                if (val == 0) {
                    return mCollator.compare(a.label, b.label);
                } else {
                    return val < 0 ? -1 : 1;
                }
            });

            // Collect what we are going to do.
            Rec firstDialog = null;
            final List<Rec> notificationRecs = new ArrayList();
            final int notificationCount = recs.size();
            for (int i = 0; i < notificationCount; i++) {
                final Rec rec = recs.get(i);
                notificationRecs.add(rec);
                final String uri = rec.report.getUri().toString();
                remainingNotifications.remove(uri);
                currentNotifications.add(uri);
                if ((rec.report.getFlags() & IncidentManager.FLAG_CONFIRMATION_DIALOG) != 0) {
                    if (firstDialog == null) {
                        firstDialog = rec;
                    }
                }
            }

            if (false) {
                Log.d(TAG, "PermissionController pending list plan ... {");
                Log.d(TAG, "  showing {");
                for (int i = 0; i < notificationRecs.size(); i++) {
                    Log.d(TAG, "    [" + i + "] " + notificationRecs.get(i).report.getUri());
                }
                Log.d(TAG, "  }");
                Log.d(TAG, "  canceling {");
                for (int i = 0; i < remainingNotifications.size(); i++) {
                    Log.d(TAG, "    [" + i + "] " + remainingNotifications.valueAt(i));
                }
                Log.d(TAG, "  }");
                Log.d(TAG, "}");
            }

            // Show the notifications
            showNotifications(notificationRecs);

            // Cancel any previously remaining notifications
            final int remainingCount = remainingNotifications.size();
            for (int i = 0; i < remainingCount; i++) {
                mNm.cancel(remainingNotifications.valueAt(i), Constants.INCIDENT_NOTIFICATION_ID);
            }

            // The dialog
            if (firstDialog != null) {
                // Show the new dialog. The FLAG_ACTIVITY_CLEAR_TASK in the intent
                // will remove any previously showing dialog.  We check the static
                // on ConfirmationActivity so that if the dialog is currently on
                // top, for the same Uri, then we won't cause jank by re-showing
                // the same one.
                if (!firstDialog.report.getUri().equals(ConfirmationActivity.getCurrentUri())) {
                    if ((mFlags & FLAG_FROM_NOTIFICATION) == 0) {
                        mContext.startActivity(newDialogIntent(firstDialog));
                    }
                }
            } else {
                // Cancel any previously showing one.  The activity has the noHistory
                // flag set in the manifest, so we know that if won't be somewhere in
                // the background, waiting to come back.
                ConfirmationActivity.finishCurrent();
            }

            // Save this list, so we know what we did for next time.
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(SHARED_PREFS_KEY_NOTIFICATIONS, currentNotifications);
            editor.apply();
        }

        /**
         * Show the list of notifications and cancel any unneeded ones.
         */
        private void showNotifications(List<Rec> recs) {
            createNotificationChannel();

            final int recCount = recs.size();
            for (int i = 0; i < recCount; i++) {
                final Rec rec = recs.get(i);

                // Intent for the confirmation dialog.
                final PendingIntent dialog = PendingIntent.getActivity(mContext, 0,
                        newDialogIntent(rec), 0);

                // Intent for the approval and denial.
                final PendingIntent deny = PendingIntent.getBroadcast(mContext, 0,
                        new Intent(ApprovalReceiver.ACTION_DENY, rec.report.getUri(),
                            mContext, ApprovalReceiver.class),
                        0);

                // Construct the notification
                final Notification notification = new Notification.Builder(mContext)
                        .setStyle(new Notification.BigTextStyle())
                        .setContentTitle(
                                mContext.getString(R.string.incident_report_notification_title))
                        .setContentText(
                                mContext.getString(R.string.incident_report_notification_text,
                                    rec.label))
                        .setSmallIcon(R.drawable.ic_bug_report_black_24dp)
                        .setWhen(rec.report.getTimestamp())
                        .setGroup(Constants.INCIDENT_NOTIFICATION_GROUP_KEY)
                        .setChannelId(Constants.INCIDENT_NOTIFICATION_CHANNEL_ID)
                        .setSortKey(getSortKey(rec.report.getTimestamp()))
                        .setContentIntent(dialog)
                        .setDeleteIntent(deny)
                        .setColor(mContext.getColor(
                                    android.R.color.system_notification_accent_color))
                        .build();

                // Show the notification
                mNm.notify(rec.report.getUri().toString(), Constants.INCIDENT_NOTIFICATION_ID,
                        notification);
            }
        }

        /**
         * Create the notification channel for {@link #NOTIFICATION_CHANNEL_ID}.
         */
        private void createNotificationChannel() {
            final NotificationChannel channel = new NotificationChannel(
                    Constants.INCIDENT_NOTIFICATION_CHANNEL_ID,
                    mContext.getString(R.string.incident_report_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);

            // TODO: Not in SystemApi, so we can't use it.
            // channel.setBlockableSystem(true);

            mNm.createNotificationChannel(channel);
        }

        /**
         * Get the sort key for the order of our notifications.
         */
        private String getSortKey(long timestamp) {
            return sDateFormatter.format(new Date(timestamp));
        }

        /**
         * Create the intent to launch the dialog activity for the Rec.
         */
        private Intent newDialogIntent(Rec rec) {
            final Intent result = new Intent(Intent.ACTION_MAIN, rec.report.getUri(),
                    mContext, ConfirmationActivity.class);
            result.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return result;
        }
    }

    /**
     * Get the singleton instance. Note that there is no Context associated
     * with this object. The context should be passed in to updateState, and
     * the assumption is that it could be a background context (i.e. the one for a
     * BroadcastReceiver), so no direct UI can be done on it as it would be with
     * an Activity object.
     */
    public static PendingList getInstance() {
        return sInstance;
    }

    /**
     * Constructor.
     */
    private PendingList() {
    }

    /**
     * Update the notifications and dialog to reflect the current state of affairs.
     */
    public void updateState(Context context, int flags) {
        (new Updater(context, flags)).updateState();
    }
}
