/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.auto;

import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.android.car.ui.AlertDialogBuilder;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;

/**
 * A {@link GrantPermissionsViewHandler} that is specific for the auto use-case. In this case, the
 * permissions dialog is displayed using car-ui-lib {@link AlertDialogBuilder}
 */
public class GrantPermissionsAutoViewHandler implements GrantPermissionsViewHandler,
        DialogInterface.OnClickListener {
    private static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";

    private final Context mContext;

    private ResultListener mResultListener;
    private AlertDialog mDialog;

    private String mGroupName;

    public GrantPermissionsAutoViewHandler(Context context, String appPackageName) {
        mContext = context;
    }

    @Override
    public GrantPermissionsViewHandler setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public View createView() {
        // We will use a system dialog instead of a locally defined view.
        return new View(mContext);
    }

    @Override
    public void updateWindowAttributes(WindowManager.LayoutParams outLayoutParams) {
        // Nothing to do
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, CharSequence detailMessage, boolean[] buttonVisibilities) {
        if (mDialog != null) {
            mDialog.dismiss();
        }

        AlertDialogBuilder builder = new AlertDialogBuilder(mContext)
                .setTitle(mContext.getString(R.string.permission_request_title))
                .setMessage(message)
                .setIcon(icon.loadDrawable(mContext))
                .setIconTinted(true)
                .setNegativeButton(mContext.getString(R.string.grant_dialog_button_deny), this)
                .setPositiveButton(mContext.getString(R.string.grant_dialog_button_allow), this);
        if (buttonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON]) {
            builder.setNeutralButton(
                    mContext.getString(R.string.grant_dialog_button_deny_and_dont_ask_again), this);
        }
        if (groupCount > 1) {
            builder.setSubtitle(mContext.getString(R.string.current_permission_template,
                    groupIndex + 1, groupCount));
        }
        mGroupName = groupName;
        mDialog = builder.create();
        mDialog.show();
    }

    @Override
    public void saveInstanceState(Bundle outState) {
        outState.putString(ARG_GROUP_NAME, mGroupName);
    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                mResultListener.onPermissionGrantResult(mGroupName, DENIED);
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                mResultListener.onPermissionGrantResult(mGroupName, DENIED_DO_NOT_ASK_AGAIN);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED);
        }
    }
}