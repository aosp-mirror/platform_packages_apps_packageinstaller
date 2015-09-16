/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.packageinstaller.permission.ui.wear.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Utility to determine screen shape
 */
public class ViewUtils {

    public static boolean getIsCircular(Context context) {
        return context.getResources().getConfiguration().isScreenRound();
    }

    /**
     * Set the given {@code view} and all descendants to the given {@code enabled} state.
     *
     * @param view the parent view of a subtree of components whose enabled state must be set
     * @param enabled the new enabled state of the subtree of components
     */
    public static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);

        if (view instanceof ViewGroup) {
            final ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setEnabled(viewGroup.getChildAt(i), enabled);
            }
        }
    }
}
