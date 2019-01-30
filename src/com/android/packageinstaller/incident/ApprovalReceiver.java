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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IncidentManager;

/**
 * BroadcastReceiver to handle clicking on the approval and rejection buttons
 * in the notification.
 */
public class ApprovalReceiver extends BroadcastReceiver {
    /**
     * Action for an approval.
     */
    public static final String ACTION_APPROVE = "com.android.packageinstaller.incident.APPROVE";

    /**
     * Action for a denial.
     */
    public static final String ACTION_DENY = "com.android.packageinstaller.incident.DENY";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Uri uri = intent.getData();
        if (uri != null) {
            final IncidentManager incidentManager = context.getSystemService(IncidentManager.class);
            if (ACTION_APPROVE.equals(intent.getAction())) {
                incidentManager.approveReport(uri);
            } else if (ACTION_DENY.equals(intent.getAction())) {
                incidentManager.denyReport(uri);
            }
        }
        PendingList.getInstance().updateState(context, PendingList.FLAG_FROM_NOTIFICATION);
    }
}

