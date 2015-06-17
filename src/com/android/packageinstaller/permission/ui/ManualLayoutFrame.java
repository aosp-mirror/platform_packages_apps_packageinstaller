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
package com.android.packageinstaller.permission.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Allows one standard layout pass, but afterwards holds getMeasuredHeight constant,
 * however still allows drawing larger at the size needed by its children.  This allows
 * a dialog to tell the window the height is constant (with keeps its position constant)
 * but allows the view to grow downwards for animation.
 */
public class ManualLayoutFrame extends FrameLayout {

    private int mDesiredHeight;
    private int mHeight;

    private View mOffsetView;

    public ManualLayoutFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public int getLayoutHeight() {
        return mDesiredHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mDesiredHeight = getMeasuredHeight();
        if (mHeight == 0 && mDesiredHeight != 0) {
            // Record the first non-zero height, this will be the height henceforth.
            mHeight = mDesiredHeight;
        }
        if (mHeight != 0) {
            // Always report the same height
            setMeasuredDimension(getMeasuredWidth(), mHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mDesiredHeight != 0) {
            // Draw at height we expect to be.
            setBottom(getTop() + mDesiredHeight);
            bottom = top + mDesiredHeight;
        }
        super.onLayout(changed, left, top, right, bottom);
    }
}
