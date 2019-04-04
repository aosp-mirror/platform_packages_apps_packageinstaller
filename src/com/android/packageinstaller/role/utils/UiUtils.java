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
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Px;

/**
 * Utility methods about UI.
 */
public class UiUtils {

    private UiUtils() {}

    /**
     * Convert a dimension value in density independent pixels to pixels.
     *
     * @param dp the dimension value in density independent pixels
     * @param context the context to get the {@link DisplayMetrics}
     * @return the pixels
     *
     * @see TypedValue#complexToDimension(int, DisplayMetrics)
     */
    @Dimension
    public static float dpToPx(@Dimension(unit = Dimension.DP) float dp, @NonNull Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics);
    }

    /**
     * Convert a dimension value in density independent pixels to an integer pixel offset.
     *
     * @param dp the dimension value in density independent pixels
     * @param context the context to get the {@link DisplayMetrics}
     * @return the integer pixel offset
     *
     * @see TypedValue#complexToDimensionPixelOffset(int, DisplayMetrics)
     */
    @Px
    public static int dpToPxOffset(@Dimension(unit = Dimension.DP) float dp,
            @NonNull Context context) {
        return (int) dpToPx(dp, context);
    }

    /**
     * Convert a dimension value in density independent pixels to an integer pixel size.
     *
     * @param dp the dimension value in density independent pixels
     * @param context the context to get the {@link DisplayMetrics}
     * @return the integer pixel size
     *
     * @see TypedValue#complexToDimensionPixelSize(int, DisplayMetrics)
     */
    @Px
    public static int dpToPxSize(@Dimension(unit = Dimension.DP) float dp,
            @NonNull Context context) {
        float value = dpToPx(dp, context);
        int size = (int) (value >= 0 ? value + 0.5f : value - 0.5f);
        if (size != 0) {
            return size;
        } else if (value == 0) {
            return 0;
        } else if (value > 0) {
            return 1;
        } else {
            return -1;
        }
    }

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
