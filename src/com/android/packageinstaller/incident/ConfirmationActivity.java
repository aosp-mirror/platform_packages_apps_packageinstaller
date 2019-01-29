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

package com.android.packageinstaller.incident;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.IncidentManager;
import android.util.Log;

import com.android.permissioncontroller.R;

/**
 * Confirmation dialog for approving an incident or bug report for sharing off the device.
 */
public class ConfirmationActivity extends Activity implements OnClickListener, OnDismissListener {
    private static final String TAG = ConfirmationActivity.class.getSimpleName();

    /**
     * Currently displaying activity.
     */
    private static ConfirmationActivity sCurrentActivity;

    /**
     * Currently displaying uri.
     */
    private static Uri sCurrentUri;

    /**
     * If this activity is running in the current process, call finish() on it.
     */
    public static void finishCurrent() {
        if (sCurrentActivity != null) {
            sCurrentActivity.finish();
        }
    }

    /**
     * If the activity is in the resumed state, then record the Uri for the current
     * one, so PendingList can skip re-showing the same one.
     */
    public static Uri getCurrentUri() {
        return sCurrentUri;
    }

    /**
     * Create the activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Formatting formatting = new Formatting(this);

        final Uri uri = getIntent().getData();
        if (uri == null) {
            Log.w(TAG, "No uri in intent: " + getIntent());
            finish();
            return;
        }

        final IncidentManager.PendingReport report = new IncidentManager.PendingReport(uri);
        final String appLabel = formatting.getAppLabel(report.getRequestingPackage());

        new AlertDialog.Builder(this)
                .setTitle(R.string.incident_report_dialog_title)
                .setMessage(getString(R.string.incident_report_dialog_text,
                            appLabel,
                            formatting.getDate(report.getTimestamp()),
                            formatting.getTime(report.getTimestamp()),
                            appLabel))
                .setPositiveButton(R.string.incident_report_dialog_allow_label, this)
                .setNegativeButton(R.string.incident_report_dialog_deny_label, this)
                .setOnDismissListener(this)
                .show();
    }

    /**
     * Activity lifecycle callback.  Now visible.
     */
    @Override
    protected void onStart() {
        super.onStart();
        sCurrentActivity = this;
        sCurrentUri = getIntent().getData();
    }

    /**
     * Activity lifecycle callback.  Now not visible.
     */
    @Override
    protected void onStop() {
        super.onStop();
        sCurrentActivity = null;
        sCurrentUri = null;
    }

    /**
     * Dialog canceled.
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        finish();
    }

    /**
     * Explicit button click.
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        final IncidentManager incidentManager = getSystemService(IncidentManager.class);

        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                incidentManager.approveReport(getIntent().getData());
                PendingList.getInstance().updateState(this, 0);
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                incidentManager.denyReport(getIntent().getData());
                PendingList.getInstance().updateState(this, 0);
                break;
        }
        finish();
    }
}

