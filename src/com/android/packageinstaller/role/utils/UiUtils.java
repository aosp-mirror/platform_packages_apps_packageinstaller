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

package com.android.packageinstaller.role.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

/**
 * Utility methods about UI.
 */
public class UiUtils {

    private UiUtils() {}

    /**
     * Set whether a view is shown.
     *
     * @param view the view to be set to shown or not
     * @param shown whether the view should be shown
     */
    public static void setViewShown(@NonNull View view, boolean shown) {
        if (shown && view.getVisibility() == View.VISIBLE && view.getAlpha() == 1) {
            // This cancels any on-going animation.
            view.animate()
                    .alpha(1)
                    .setDuration(0);
            return;
        } else if (!shown && (view.getVisibility() != View.VISIBLE || view.getAlpha() == 0)) {
            // This cancels any on-going animation.
            view.animate()
                    .alpha(0)
                    .setDuration(0);
            view.setVisibility(View.INVISIBLE);
            return;
        }
        if (shown && view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0);
            view.setVisibility(View.VISIBLE);
        }
        int duration = view.getResources().getInteger(android.R.integer.config_mediumAnimTime);
        Interpolator interpolator = AnimationUtils.loadInterpolator(view.getContext(), shown
                ? android.R.interpolator.fast_out_slow_in
                : android.R.interpolator.fast_out_linear_in);
        view.animate()
                .alpha(shown ? 1 : 0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                // Always update the listener or the view will try to reuse the previous one.
                .setListener(shown ? null : new AnimatorListenerAdapter() {
                    private boolean mCanceled = false;
                    @Override
                    public void onAnimationCancel(@NonNull Animator animator) {
                        mCanceled = true;
                    }
                    @Override
                    public void onAnimationEnd(@NonNull Animator animator) {
                        if (!mCanceled) {
                            view.setVisibility(View.INVISIBLE);
                        }
                    }
                });
    }
}
