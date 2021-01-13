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

package com.android.permissioncontroller.permission.ui.handheld;

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

import android.app.Activity;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.method.LinkMovementMethod;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsActivity;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;

public class GrantPermissionsViewHandlerImpl implements GrantPermissionsViewHandler,
        OnClickListener {

    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    private static final String ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE";
    private static final String ARG_DIALOG_BUTTON_VISIBILITIES = "ARG_DIALOG_BUTTON_VISIBILITIES";

    // Animation parameters.
    private static final long SWITCH_TIME_MILLIS = 75;
    private static final long ANIMATION_DURATION_MILLIS = 200;

    private static final SparseArray<Integer> BUTTON_RES_ID_TO_NUM = new SparseArray<>();
    static {
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_button, ALLOW_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_always_button,
                ALLOW_ALWAYS_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_foreground_only_button,
                ALLOW_FOREGROUND_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_button, DENY_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_and_dont_ask_again_button,
                DENY_AND_DONT_ASK_AGAIN_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_one_time_button, ALLOW_ONE_TIME_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_button, NO_UPGRADE_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_and_dont_ask_again_button,
                NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_one_time_button, NO_UPGRADE_OT_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_one_time_and_dont_ask_again_button,
                NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON);
    }

    private final Activity mActivity;
    private final String mAppPackageName;
    private final UserHandle mUserHandle;

    private ResultListener mResultListener;

    // Configuration of the current dialog
    private String mGroupName;
    private int mGroupCount;
    private int mGroupIndex;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private CharSequence mDetailMessage;
    private boolean[] mButtonVisibilities;

    // Views
    private ImageView mIconView;
    private TextView mMessageView;
    private TextView mDetailMessageView;
    private Button[] mButtons;
    private ViewGroup mRootView;

    public GrantPermissionsViewHandlerImpl(Activity activity, String appPackageName,
            @NonNull UserHandle userHandle) {
        mActivity = activity;
        mAppPackageName = appPackageName;
        mUserHandle = userHandle;
    }

    @Override
    public GrantPermissionsViewHandlerImpl setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public void saveInstanceState(Bundle arguments) {
        arguments.putString(ARG_GROUP_NAME, mGroupName);
        arguments.putInt(ARG_GROUP_COUNT, mGroupCount);
        arguments.putInt(ARG_GROUP_INDEX, mGroupIndex);
        arguments.putParcelable(ARG_GROUP_ICON, mGroupIcon);
        arguments.putCharSequence(ARG_GROUP_MESSAGE, mGroupMessage);
        arguments.putCharSequence(ARG_GROUP_DETAIL_MESSAGE, mDetailMessage);
        arguments.putBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES, mButtonVisibilities);
    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        mGroupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE);
        mGroupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON);
        mGroupCount = savedInstanceState.getInt(ARG_GROUP_COUNT);
        mGroupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX);
        mDetailMessage = savedInstanceState.getCharSequence(ARG_GROUP_DETAIL_MESSAGE);
        mButtonVisibilities = savedInstanceState.getBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES);

        if (mGroupName == null || mButtonVisibilities == null) {
            // The dialog wasn't shown when the activity was destroyed.
            return;
        }
        updateAll();
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, CharSequence detailMessage, boolean[] buttonVisibilities) {
        boolean isNewGroup = mGroupIndex != groupIndex;

        mGroupName = groupName;
        mGroupCount = groupCount;
        mGroupIndex = groupIndex;
        mGroupIcon = icon;
        mGroupMessage = message;
        mDetailMessage = detailMessage;
        mButtonVisibilities = buttonVisibilities;

        // If this is a second (or later) permission and the views exist, then animate.
        if (mIconView != null) {
            updateAll();
        }
    }

    private void updateAll() {
        updateDescription();
        updateDetailDescription();
        updateButtons();

//      Animate change in size
//      Grow or shrink the content container to size of new content
        ChangeBounds growShrinkToNewContentSize = new ChangeBounds();
        growShrinkToNewContentSize.setDuration(ANIMATION_DURATION_MILLIS);
        growShrinkToNewContentSize.setInterpolator(AnimationUtils.loadInterpolator(mActivity,
                android.R.interpolator.fast_out_slow_in));
        TransitionManager.beginDelayedTransition(mRootView, growShrinkToNewContentSize);
    }

    @Override
    public View createView() {
        mRootView = (ViewGroup) LayoutInflater.from(mActivity)
                .inflate(R.layout.grant_permissions, null);

        int h = mActivity.getResources().getDisplayMetrics().heightPixels;
        mRootView.setMinimumHeight(h);
        mRootView.findViewById(R.id.grant_singleton).setOnClickListener(this); // Cancel dialog
        mRootView.findViewById(R.id.grant_dialog).setOnClickListener(this); // Swallow click event

        mMessageView = mRootView.findViewById(R.id.permission_message);
        mDetailMessageView = mRootView.findViewById(R.id.detail_message);
        mDetailMessageView.setMovementMethod(LinkMovementMethod.getInstance());
        mIconView = mRootView.findViewById(R.id.permission_icon);

        mButtons = new Button[GrantPermissionsActivity.NEXT_BUTTON];

        int numButtons = BUTTON_RES_ID_TO_NUM.size();
        for (int i = 0; i < numButtons; i++) {
            Button button = mRootView.findViewById(BUTTON_RES_ID_TO_NUM.keyAt(i));
            button.setOnClickListener(this);
            mButtons[BUTTON_RES_ID_TO_NUM.valueAt(i)] = button;
        }

        if (mGroupName != null) {
            updateAll();
        }

        return mRootView;
    }

    @Override
    public void updateWindowAttributes(LayoutParams outLayoutParams) {
        // No-op
    }

    private void updateDescription() {
        if (mGroupIcon != null) {
            mIconView.setImageDrawable(mGroupIcon.loadDrawable(mActivity));
        }
        mMessageView.setText(mGroupMessage);
    }

    private void updateDetailDescription() {
        if (mDetailMessage == null) {
            mDetailMessageView.setVisibility(View.GONE);
        } else {
            mDetailMessageView.setText(mDetailMessage);
            mDetailMessageView.setVisibility(View.VISIBLE);
        }
    }

    private void updateButtons() {
        int numButtons = BUTTON_RES_ID_TO_NUM.size();
        for (int i = 0; i < numButtons; i++) {
            int pos = BUTTON_RES_ID_TO_NUM.valueAt(i);
            mButtons[pos].setVisibility(mButtonVisibilities[pos] ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.grant_singleton) {
            if (mResultListener != null) {
                mResultListener.onPermissionGrantResult(mGroupName, CANCELED);
            } else {
                mActivity.finish();
            }
            return;
        }
        int button = -1;
        try {
            button = BUTTON_RES_ID_TO_NUM.get(id);
        } catch (NullPointerException e) {
            // Clicked a view which is not one of the defined buttons
            return;
        }
        switch (button) {
            case ALLOW_BUTTON:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
                }
                break;
            case ALLOW_FOREGROUND_BUTTON:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName,
                            GRANTED_FOREGROUND_ONLY);
                }
                break;
            case ALLOW_ALWAYS_BUTTON:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName,
                            GRANTED_ALWAYS);
                }
                break;
            case ALLOW_ONE_TIME_BUTTON:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ONE_TIME);
                }
                break;
            case DENY_BUTTON:
            case NO_UPGRADE_BUTTON:
            case NO_UPGRADE_OT_BUTTON:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName, DENIED);
                }
                break;
            case DENY_AND_DONT_ASK_AGAIN_BUTTON:
            case NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON:
            case NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName,
                            DENIED_DO_NOT_ASK_AGAIN);
                }
                break;
        }

    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, CANCELED);
        } else {
            mActivity.finish();
        }
    }

}
