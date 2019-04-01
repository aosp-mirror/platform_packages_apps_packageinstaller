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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.permissioncontroller.R;

import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        if (uri == null) {
            Log.w(TAG, "No uri in intent: " + getIntent());
            finish();
            return;
        }

        final IncidentManager.PendingReport pending = new IncidentManager.PendingReport(uri);
        final String appLabel = formatting.getAppLabel(pending.getRequestingPackage());

        final Resources res = getResources();
        final ArrayList<Drawable> images = getImages(uri, res);
        if (images == null) {
            // Null result from getImages means that there was an error in the input,
            // and we will just summarily reject the upload, since we can't get proper
            // approval.
            // Zero-length list means that we will proceed with the imageless consent
            // dialog.
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


        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.incident_report_dialog_title)
                .setPositiveButton(R.string.incident_report_dialog_allow_label, this)
                .setNegativeButton(R.string.incident_report_dialog_deny_label, this)
                .setOnDismissListener(this);

        final String message = getString(R.string.incident_report_dialog_text,
                    appLabel,
                    formatting.getDate(pending.getTimestamp()),
                    formatting.getTime(pending.getTimestamp()),
                    appLabel);


        final int imagesSize = images.size();
        if (imagesSize > 0) {
            final View content = getLayoutInflater().inflate(R.layout.incident_image_confirmation,
                    null);
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

            ((TextView) content.findViewById(R.id.message)).setText(message);

            builder.setView(content);
        } else {
            builder.setMessage(message);
        }

        builder.show();
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

    ArrayList<Drawable> getImages(final Uri uri, Resources res) {
        try {
            final IncidentManager incidentManager = getSystemService(IncidentManager.class);
            final IncidentManager.IncidentReport report = incidentManager.getIncidentReport(uri);

            final InputStream stream = report.getInputStream();
            if (stream != null) {
                return getImages(stream, res);
            }
        } catch (IOException ex) {
            Log.w(TAG, "Error while reading stream. The report will be rejected.", ex);
            return null;
        } catch (OutOfMemoryError ex) {
            Log.w(TAG, "Out of memory while creating confirmation images. The report will"
                    + " be rejected.", ex);
            return null;
        }
        return new ArrayList();
    }

    ArrayList<Drawable> getImages(InputStream stream, Resources res) throws IOException {
        final IncidentMinimal incident = IncidentMinimal.parseFrom(stream);
        if (incident != null) {
            return getImages(incident, res);
        } else {
            return new ArrayList();
        }
    }

    ArrayList<Drawable> getImages(IncidentMinimal incident, Resources res) {
        final ArrayList<Drawable> drawables = new ArrayList();

        final int totalImageCountLimit = 20;
        int totalImageCount = 0;

        if (incident.hasRestrictedImagesSection()) {
            final RestrictedImagesDumpProto section = incident.getRestrictedImagesSection();
            final int setsCount = section.getSetsCount();
            for (int i = 0; i < setsCount; i++) {
                final RestrictedImageSetProto set = section.getSets(i);
                if (set == null) {
                    continue;
                }
                final int imageCount = set.getImagesCount();
                for (int j = 0; j < imageCount; j++) {
                    // Hard cap on number of images, as a guardrail.
                    totalImageCount++;
                    if (totalImageCount > totalImageCountLimit) {
                        Log.w(TAG, "Image count is greater than the limit of "
                                + totalImageCountLimit + ". The report will be rejected.");
                        return null;
                    }

                    final RestrictedImageProto image = set.getImages(j);
                    if (image == null) {
                        continue;
                    }
                    final String mimeType = image.getMimeType();
                    if (!("image/jpeg".equals(mimeType)
                            || "image/png".equals(mimeType))) {
                        Log.w(TAG, "Unsupported image type " + mimeType
                                + ". The report will be rejected.");
                        return null;
                    }
                    final ByteString bytes = image.getImageData();
                    if (bytes == null) {
                        continue;
                    }
                    final byte[] buf = bytes.toByteArray();
                    if (buf.length == 0) {
                        continue;
                    }

                    // This will attempt to uncompress the image. If it's gigantic,
                    // this could fail with OutOfMemoryError, which will be caught
                    // by the caller, and turned into a report rejection.
                    final Drawable drawable = new android.graphics.drawable.BitmapDrawable(
                            res, new ByteArrayInputStream(buf));

                    // TODO: Scale bitmap to correct thumbnail size to save memory.

                    drawables.add(drawable);
                }
            }
        }

        // Test data
        if (true) {
            drawables.add(new android.graphics.drawable.BitmapDrawable(res,
                    new ByteArrayInputStream(new byte[] {
                        // png image data
                        -119, 80, 78, 71, 13, 10, 26, 10,
                        0, 0, 0, 13, 73, 72, 68, 82,
                        0, 0, 0, 100, 0, 0, 0, 100,
                        1, 3, 0, 0, 0, 74, 44, 7,
                        23, 0, 0, 0, 4, 103, 65, 77,
                        65, 0, 0, -79, -113, 11, -4, 97,
                        5, 0, 0, 0, 1, 115, 82, 71,
                        66, 0, -82, -50, 28, -23, 0, 0,
                        0, 6, 80, 76, 84, 69, -1, -1,
                        -1, 0, 0, 0, 85, -62, -45, 126,
                        0, 0, 0, -115, 73, 68, 65, 84,
                        56, -53, -19, -46, -79, 17, -128, 32,
                        12, 5, -48, 120, 22, -106, -116, -32,
                        40, -84, 101, -121, -93, 57, 10, 35,
                        88, 82, 112, 126, 3, -60, 104, 6,
                        -112, 70, 127, -59, -69, -53, 29, 33,
                        -127, -24, 79, -49, -52, -15, 41, 36,
                        34, -105, 85, 124, -14, 88, 27, 6,
                        28, 68, 1, 82, 62, 22, -95, -108,
                        55, -95, 40, -9, -110, -12, 98, -107,
                        76, -41, -105, -62, -50, 111, -60, 46,
                        -14, -4, 24, -89, 42, -103, 16, 63,
                        -72, -11, -15, 48, -62, 102, -44, 102,
                        -73, -56, 56, -21, -128, 92, -70, -124,
                        117, -46, -67, -77, 82, 80, 121, -44,
                        -56, 116, 93, -45, -90, -5, -29, -24,
                        -83, -75, 52, -34, 55, -22, 102, -21,
                        -105, -124, -23, 71, 87, -7, -25, -59,
                        -100, -73, -92, -122, -7, -109, -49, -80,
                        -89, 0, 0, 0, 0, 73, 69, 78,
                        68, -82, 66, 96, -126
                    })));
        }

        return drawables;
    }
}

