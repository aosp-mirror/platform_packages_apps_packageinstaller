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

import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_BUTTON;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.android.car.ui.AlertDialogBuilder;
import com.android.car.ui.recyclerview.CarUiContentListItem;
import com.android.car.ui.recyclerview.CarUiListItem;
import com.android.car.ui.recyclerview.CarUiListItemAdapter;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A {@link GrantPermissionsViewHandler} that is specific for the auto use-case. In this case, the
 * permissions dialog is displayed using car-ui-lib {@link AlertDialogBuilder}
 */
public class GrantPermissionsAutoViewHandler implements GrantPermissionsViewHandler {
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
            mDialog = null;
        }

        AlertDialogBuilder builder = new AlertDialogBuilder(mContext)
                .setTitle(mGroupMessage)
                .setSubtitle(mDetailMessage)
                .setNegativeButton(R.string.grant_dialog_button_dismiss, (dialog, which) ->
                        dialog.dismiss())
                .setOnDismissListener((dialog) -> {
                    mDialog = null;
                    mResultListener.onPermissionGrantResult(mGroupName, DENIED);
                });
        if (mGroupIcon != null) {
            builder.setIcon(mGroupIcon.loadDrawable(mContext));
        }

        List<CarUiListItem> itemList = new ArrayList<>();

        // Don't show the allow one time button as per automotive design decisions
        createListItem(itemList, R.string.grant_dialog_button_allow,
                GRANTED_ALWAYS, ALLOW_BUTTON, ALLOW_ONE_TIME_BUTTON);
        createListItem(itemList, R.string.grant_dialog_button_allow_always,
                GRANTED_ALWAYS, ALLOW_ALWAYS_BUTTON);
        createListItem(itemList, R.string.grant_dialog_button_allow_foreground,
                GRANTED_FOREGROUND_ONLY, ALLOW_FOREGROUND_BUTTON);
        createListItem(itemList, R.string.grant_dialog_button_deny,
                DENIED, DENY_BUTTON);
        createListItem(itemList, R.string.grant_dialog_button_deny_and_dont_ask_again,
                DENIED_DO_NOT_ASK_AGAIN, DENY_AND_DONT_ASK_AGAIN_BUTTON);

        // It seems even on phone the same strings are used for "no upgrade" and
        // "no upgrade don't ask again"
        createListItem(itemList, R.string.grant_dialog_button_no_upgrade, DENIED,
                NO_UPGRADE_BUTTON, NO_UPGRADE_OT_BUTTON);
        createListItem(itemList, R.string.grant_dialog_button_no_upgrade, DENIED_DO_NOT_ASK_AGAIN,
                NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON, NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON);

        builder.setAdapter(new CarUiListItemAdapter(itemList));

        mDialog = builder.create();
        mDialog.show();
    }

    private void createListItem(List<CarUiListItem> list,
            int stringId, @Result int result, int... indices) {
        // Check that at least one of the indices is shown.
        if (IntStream.of(indices)
                .mapToObj(i -> i >= 0 && i < mButtonVisibilities.length && mButtonVisibilities[i])
                .noneMatch(b -> b)) {
            return;
        }

        CarUiContentListItem item = new CarUiContentListItem(CarUiContentListItem.Action.NONE);
        item.setTitle(mContext.getString(stringId));
        item.setOnItemClickedListener(i -> {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
            mResultListener.onPermissionGrantResult(mGroupName, result);
        });
        list.add(item);
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
    public void onBackPressed() {
        if (mDialog != null) {
            mDialog.dismiss();
        } else if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED);
        }
    }
}
