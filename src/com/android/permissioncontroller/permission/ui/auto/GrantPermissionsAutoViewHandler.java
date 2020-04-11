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
        DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
    private static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    private static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    private static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    private static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    private static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    private static final String ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE";
    private static final String ARG_BUTTON_VISIBILITIES = "ARG_BUTTON_VISIBILITIES";

    private final Context mContext;
    private ResultListener mResultListener;
    private AlertDialog mDialog;
    private String mGroupName;
    private int mGroupCount;
    private int mGroupIndex;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private CharSequence mDetailMessage;
    private boolean[] mButtonVisibilities;

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
        mGroupName = groupName;
        mGroupCount = groupCount;
        mGroupIndex = groupIndex;
        mGroupIcon = icon;
        mGroupMessage = message;
        mDetailMessage = detailMessage;
        mButtonVisibilities = buttonVisibilities;

        update();
    }

    private void update() {
        if (mDialog != null) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
        }

        AlertDialogBuilder builder = new AlertDialogBuilder(mContext)
                .setTitle(mContext.getString(R.string.permission_request_title))
                .setMessage(mGroupMessage)
                .setIcon(mGroupIcon.loadDrawable(mContext))
                .setIconTinted(true)
                .setOnDismissListener(this)
                .setNeutralButton(mContext.getString(R.string.grant_dialog_button_deny), this)
                .setPositiveButton(mContext.getString(R.string.grant_dialog_button_allow), this);
        if (mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON]) {
            builder.setNegativeButton(mContext.getString(R.string.never_ask_again), this);
        }
        if (mGroupCount > 1) {
            builder.setSubtitle(mContext.getString(R.string.current_permission_template,
                    mGroupIndex + 1, mGroupCount));
        }
        mDialog = builder.create();
        mDialog.show();
    }

    @Override
    public void saveInstanceState(Bundle arguments) {
        arguments.putString(ARG_GROUP_NAME, mGroupName);
        arguments.putInt(ARG_GROUP_COUNT, mGroupCount);
        arguments.putInt(ARG_GROUP_INDEX, mGroupIndex);
        arguments.putParcelable(ARG_GROUP_ICON, mGroupIcon);
        arguments.putCharSequence(ARG_GROUP_MESSAGE, mGroupMessage);
        arguments.putCharSequence(ARG_GROUP_DETAIL_MESSAGE, mDetailMessage);
        arguments.putBooleanArray(ARG_BUTTON_VISIBILITIES, mButtonVisibilities);

    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        mGroupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE);
        mGroupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON);
        mGroupCount = savedInstanceState.getInt(ARG_GROUP_COUNT);
        mGroupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX);
        mDetailMessage = savedInstanceState.getCharSequence(ARG_GROUP_DETAIL_MESSAGE);
        mButtonVisibilities = savedInstanceState.getBooleanArray(ARG_BUTTON_VISIBILITIES);

        update();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        mDialog.setOnDismissListener(null);
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                mResultListener.onPermissionGrantResult(mGroupName, DENIED);
                break;
            case DialogInterface.BUTTON_NEGATIVE:
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

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED);
        }
    }
}
