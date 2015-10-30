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

package com.android.packageinstaller.permission.ui.wear;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import com.android.packageinstaller.R;

public final class WarningConfirmationActivity extends Activity {
    public final static String EXTRA_WARNING_MESSAGE = "EXTRA_WARNING_MESSAGE";
    // Saved index that will be returned in the onActivityResult() callback
    public final static String EXTRA_INDEX = "EXTRA_INDEX";

    private ConfirmationViewHandler mViewHandler;
    private String mMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMessage = getIntent().getStringExtra(EXTRA_WARNING_MESSAGE);

        mViewHandler = new ConfirmationViewHandler(this) {
            @Override // ConfirmationViewHandler
            public int getButtonBarMode() {
                return MODE_VERTICAL_BUTTONS;
            }

            @Override
            public void onButton1() {
                setResultAndFinish(Activity.RESULT_CANCELED);
            }

            @Override
            public void onButton2() {
                setResultAndFinish(Activity.RESULT_OK);
            }

            @Override
            public void onButton3() {
                // no-op
            }

            @Override
            public CharSequence getVerticalButton1Text() {
                return getString(R.string.cancel);
            }

            @Override
            public CharSequence getVerticalButton2Text() {
                return getString(R.string.grant_dialog_button_deny);
            }

            @Override
            public CharSequence getVerticalButton3Text() {
                return null;
            }

            @Override
            public Drawable getVerticalButton1Icon() {
                return getDrawable(R.drawable.cancel_button);
            }

            @Override
            public Drawable getVerticalButton2Icon() {
                return getDrawable(R.drawable.confirm_button);
            }

            @Override
            public Drawable getVerticalButton3Icon() {
                return null;
            }

            @Override
            public CharSequence getCurrentPageText() {
                return null;
            }

            @Override
            public Icon getPermissionIcon() {
                return null;
            }

            @Override
            public CharSequence getMessage() {
                return mMessage;
            }
        };

        setContentView(mViewHandler.createView());
        mViewHandler.invalidate();
    }

    private void setResultAndFinish(int result) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_INDEX, getIntent().getIntExtra(EXTRA_INDEX, -1));
        setResult(result, intent);
        finish();
    }
}
