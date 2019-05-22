/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IncidentManager;

import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * The pieces of an incident report that should be confirmed by the user.
 */
public class ReportDetails {
    private static final String TAG = "ReportDetails";

    private ArrayList<String> mReasons = new ArrayList<String>();
    private ArrayList<Drawable> mImages = new ArrayList<Drawable>();

    /**
     * Thrown when there is an error parsing the incident report.  Incident reports
     * that can't be parsed can not be properly shown to the user and are summarily
     * rejected.
     */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable ex) {
            super(message, ex);
        }
    }

    private ReportDetails() {
    }

    /**
     * Parse an incident report into a ReportDetails object.  This function drops most
     * of the fields in an incident report
     */
    public static ReportDetails parseIncidentReport(final Context context, final Uri uri)
            throws ParseException {
        final ReportDetails details = new ReportDetails();
        try {
            final IncidentManager incidentManager = context.getSystemService(IncidentManager.class);
            final IncidentManager.IncidentReport report = incidentManager.getIncidentReport(uri);
            if (report == null) {
                // There is no incident report, so nothing to show, so return empty object.
                // Other errors below are invalid images, which we reject, because they're there
                // but we can't let the user confirm it, but nothing to show is okay.  This is
                // also the dumpstate / bugreport case.
                return details;
            }

            final InputStream stream = report.getInputStream();
            if (stream != null) {
                final IncidentMinimal incident = IncidentMinimal.parseFrom(stream);
                if (incident != null) {
                    parseImages(details.mImages, incident, context.getResources());
                    parseReasons(details.mReasons, incident);
                }
            }
        } catch (IOException ex) {
            throw new ParseException("Error while reading stream.", ex);
        } catch (OutOfMemoryError ex) {
            throw new ParseException("Out of memory while loading incident report.", ex);
        }
        return details;
    }

    /**
     * Reads the reasons from the incident headers.  Does not throw any exceptions
     * about validity, because the headers are optional.
     */
    private static void parseReasons(ArrayList<String> result, IncidentMinimal incident) {
        final int headerSize = incident.getHeaderCount();
        for (int i = 0; i < headerSize; i++) {
            final IncidentHeaderProto header = incident.getHeader(i);
            if (header.hasReason()) {
                final String reason = header.getReason();
                if (reason != null && reason.length() > 0) {
                    result.add(reason);
                }
            }
        }
    }

    /**
     * Read images from the IncidentMinimal.
     *
     * @throw ParseException if there was an error reading them.
     */
    private static void parseImages(ArrayList<Drawable> result, IncidentMinimal incident,
            Resources res) throws ParseException {
        final int totalImageCountLimit = 200;
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
                        throw new ParseException("Image count is greater than the limit of "
                                + totalImageCountLimit);
                    }

                    final RestrictedImageProto image = set.getImages(j);
                    if (image == null) {
                        continue;
                    }
                    final String mimeType = image.getMimeType();
                    if (!("image/jpeg".equals(mimeType)
                            || "image/png".equals(mimeType))) {
                        throw new ParseException("Unsupported image type " + mimeType);
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

                    result.add(drawable);
                }
            }
        }
    }

    /**
     * The "reason" field from any incident report headers, which could contain
     * explanitory text for why the incident report was taken.
     */
    public ArrayList<String> getReasons() {
        return mReasons;
    }

    /**
     * Images that must be approved by the user.
     */
    public ArrayList<Drawable> getImages() {
        return mImages;
    }
}
