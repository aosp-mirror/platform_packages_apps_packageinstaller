/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

/**
 * A button which doesn't allow clicking when any part of the window is obscured
 */
public class SecureButton extends Button {

    private static final int FLAGS_WINDOW_IS_OBSCURED =
            MotionEvent.FLAG_WINDOW_IS_OBSCURED | MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED;

    public SecureButton(Context context) {
        super(context);
    }

    public SecureButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecureButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SecureButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onFilterTouchEventForSecurity(MotionEvent event) {
        return (event.getFlags() & FLAGS_WINDOW_IS_OBSCURED) == 0
                && super.onFilterTouchEventForSecurity(event);
    }
}
