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

package com.android.packageinstaller.permission.ui.auto;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import com.android.packageinstaller.permission.ui.handheld.ReviewOngoingUsageFragment;

/**
 * A dialog listing the currently uses of camera, microphone, and location.
 */
public class ReviewOngoingUsageAutoFragment extends ReviewOngoingUsageFragment {

    /**
     * @return A new {@link ReviewOngoingUsageAutoFragment}
     */
    public static ReviewOngoingUsageAutoFragment newInstance(long numMillis) {
        ReviewOngoingUsageAutoFragment fragment = new ReviewOngoingUsageAutoFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(Intent.EXTRA_DURATION_MILLIS, numMillis);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    protected void setNeutralButton(AlertDialog.Builder builder) {
        // do nothing
    }
}
