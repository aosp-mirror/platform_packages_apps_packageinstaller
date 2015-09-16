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

import android.animation.ObjectAnimator;
import android.support.wearable.view.WearableListView;
import android.view.View;


public class ExtendedViewHolder extends WearableListView.ViewHolder {
    public static final long DEFAULT_ANIMATION_DURATION = 150;

    private ObjectAnimator mScalingUpAnimator;

    private ObjectAnimator mScalingDownAnimator;

    private float mMinValue;

    private float mMaxValue;

    public ExtendedViewHolder(View itemView) {
        super(itemView);
        if (itemView instanceof ExtendedOnCenterProximityListener) {
            ExtendedOnCenterProximityListener item =
                    (ExtendedOnCenterProximityListener) itemView;
            mMinValue = item.getProximityMinValue();
            item.setScalingAnimatorValue(mMinValue);
            mMaxValue = item.getProximityMaxValue();
            mScalingUpAnimator = ObjectAnimator.ofFloat(item, "scalingAnimatorValue", mMinValue,
                    mMaxValue);
            mScalingUpAnimator.setDuration(DEFAULT_ANIMATION_DURATION);
            mScalingDownAnimator = ObjectAnimator.ofFloat(item, "scalingAnimatorValue",
                    mMaxValue, mMinValue);
            mScalingDownAnimator.setDuration(DEFAULT_ANIMATION_DURATION);
        }
    }

    public void onCenterProximity(boolean isCentralItem, boolean animate) {
        if (!(itemView instanceof ExtendedOnCenterProximityListener)) {
            return;
        }
        ExtendedOnCenterProximityListener item = (ExtendedOnCenterProximityListener) itemView;
        if (isCentralItem) {
            if (animate) {
                mScalingDownAnimator.cancel();
                if (!mScalingUpAnimator.isRunning()) {
                    mScalingUpAnimator.setFloatValues(item.getCurrentProximityValue(),
                            mMaxValue);
                    mScalingUpAnimator.start();
                }
            } else {
                mScalingUpAnimator.cancel();
                item.setScalingAnimatorValue(item.getProximityMaxValue());
            }
        } else {
            mScalingUpAnimator.cancel();
            if (animate) {
                if (!mScalingDownAnimator.isRunning()) {
                    mScalingDownAnimator.setFloatValues(item.getCurrentProximityValue(),
                            mMinValue);
                    mScalingDownAnimator.start();
                }
            } else {
                mScalingDownAnimator.cancel();
                item.setScalingAnimatorValue(item.getProximityMinValue());
            }
        }
        super.onCenterProximity(isCentralItem, animate);
    }
}
