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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IncidentManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BulletSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.permissioncontroller.R;

import java.util.ArrayList;

/**
 * Confirmation dialog for approving an incident or bug report for sharing off the device.
 */
public class ConfirmationActivity extends Activity implements OnClickListener, OnDismissListener {
    private static final String TAG = "ConfirmationActivity";

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
        Log.d(TAG, "uri=" + uri);
        if (uri == null) {
            Log.w(TAG, "No uri in intent: " + getIntent());
            finish();
            return;
        }

        final IncidentManager.PendingReport pending = new IncidentManager.PendingReport(uri);
        final String appLabel = formatting.getAppLabel(pending.getRequestingPackage());

        final Resources res = getResources();

        ReportDetails details;
        try {
            details = ReportDetails.parseIncidentReport(this, uri);
        } catch (ReportDetails.ParseException ex) {
            Log.w("Rejecting report because it couldn't be parsed", ex);
            // If there was an error in the input we will just summarily reject the upload,
            // since we can't get proper approval. (Zero-length images or reasons means that
            // we will proceed with the imageless consent dialog).
            final IncidentManager incidentManager = getSystemService(IncidentManager.class);
            incidentManager.denyReport(getIntent().getData());

            // Show a message to the user saying... nevermind.
            new AlertDialog.Builder(this)
                .setTitle(R.string.incident_report_dialog_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .setMessage(getString(R.string.incident_report_error_dialog_text, appLabel))
                .setOnDismissListener(this)
                .show();
            return;

        }

        final View content = getLayoutInflater().inflate(R.layout.incident_confirmation,
                null);

        final ArrayList<String> reasons = details.getReasons();
        final int reasonsSize = reasons.size();
        if (reasonsSize > 0) {
            content.findViewById(R.id.reasonIntro).setVisibility(View.VISIBLE);

            final TextView reasonTextView = (TextView) content.findViewById(R.id.reasons);
            reasonTextView.setVisibility(View.VISIBLE);

            final int bulletSize =
                    (int) (res.getDimension(R.dimen.incident_reason_bullet_size) + 0.5f);
            final int bulletIndent =
                    (int) (res.getDimension(R.dimen.incident_reason_bullet_indent) + 0.5f);
            final int bulletColor =
                    getColor(R.color.incident_reason_bullet_color);

            final StringBuilder text = new StringBuilder();
            for (int i = 0; i < reasonsSize; i++) {
                text.append(reasons.get(i));
                if (i != reasonsSize - 1) {
                    text.append("\n");
                }
            }
            final SpannableString spannable = new SpannableString(text.toString());
            int spanStart = 0;
            for (int i = 0; i < reasonsSize; i++) {
                final int length = reasons.get(i).length();
                spannable.setSpan(new BulletSpan(bulletIndent, bulletColor, bulletSize),
                        spanStart, spanStart + length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spanStart += length + 1;
            }

            reasonTextView.setText(spannable);
        }

        final String message = getString(R.string.incident_report_dialog_text,
                    appLabel,
                    formatting.getDate(pending.getTimestamp()),
                    formatting.getTime(pending.getTimestamp()),
                    appLabel);
        ((TextView) content.findViewById(R.id.message)).setText(message);

        final ArrayList<Drawable> images = details.getImages();
        final int imagesSize = images.size();
        if (imagesSize > 0) {
            content.findViewById(R.id.imageScrollView).setVisibility(View.VISIBLE);

            final LinearLayout imageList = (LinearLayout) content.findViewById(R.id.imageList);

            final int width = res.getDimensionPixelSize(R.dimen.incident_image_width);
            final int height = res.getDimensionPixelSize(R.dimen.incident_image_height);

            for (int i = 0; i < imagesSize; i++) {
                final Drawable drawable = images.get(i);
                final ImageView imageView = new ImageView(this);
                imageView.setImageDrawable(images.get(i));
                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                imageList.addView(imageView, new LinearLayout.LayoutParams(width, height));
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.incident_report_dialog_title)
                .setPositiveButton(R.string.incident_report_dialog_allow_label, this)
                .setNegativeButton(R.string.incident_report_dialog_deny_label, this)
                .setOnDismissListener(this)
                .setView(content)
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

