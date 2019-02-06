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

package com.android.packageinstaller.permission.ui.handheld;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler;
import com.android.packageinstaller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.R;

public class GrantPermissionsViewHandlerImpl implements GrantPermissionsViewHandler,
        OnClickListener {

    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    private static final String ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE";

    public static final String ARG_GROUP_SHOW_DO_NOT_ASK = "ARG_GROUP_SHOW_DO_NOT_ASK";
    private static final String ARG_GROUP_SHOW_FOREGOUND_CHOOSER =
            "ARG_GROUP_SHOW_FOREGOUND_CHOOSER";

    // Animation parameters.
    private static final long SWITCH_TIME_MILLIS = 75;
    private static final long ANIMATION_DURATION_MILLIS = 200;

    private final Activity mActivity;
    private final String mAppPackageName;

    private ResultListener mResultListener;

    // Configuration of the current dialog
    private String mGroupName;
    private int mGroupCount;
    private int mGroupIndex;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private CharSequence mDetailMessage;

    private boolean mShowDonNotAsk;
    private boolean mShowForegroundChooser;

    // Views
    private ImageView mIconView;
    private TextView mMessageView;
    private TextView mDetailMessageView;
    private Button mAllowButton;
    private Button mAllowAlwaysButton;
    private Button mAllowForegroundButton;
    private Button mDenyButton;
    private Button mDenyAndDontAskAgainButton;
    private ViewGroup mRootView;

    public GrantPermissionsViewHandlerImpl(Activity activity, String appPackageName) {
        mActivity = activity;
        mAppPackageName = appPackageName;
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

        arguments.putBoolean(ARG_GROUP_SHOW_DO_NOT_ASK, mShowDonNotAsk);
        arguments.putBoolean(ARG_GROUP_SHOW_FOREGOUND_CHOOSER, mShowForegroundChooser);

    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        mGroupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE);
        mGroupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON);
        mGroupCount = savedInstanceState.getInt(ARG_GROUP_COUNT);
        mGroupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX);
        mDetailMessage = savedInstanceState.getCharSequence(ARG_GROUP_DETAIL_MESSAGE);

        mShowDonNotAsk = savedInstanceState.getBoolean(ARG_GROUP_SHOW_DO_NOT_ASK);
        mShowForegroundChooser = savedInstanceState.getBoolean(ARG_GROUP_SHOW_FOREGOUND_CHOOSER);

        updateAll();
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, CharSequence detailMessage, boolean showForegroundChooser,
            boolean showDonNotAsk) {
        boolean isNewGroup = mGroupIndex != groupIndex;

        mGroupName = groupName;
        mGroupCount = groupCount;
        mGroupIndex = groupIndex;
        mGroupIcon = icon;
        mGroupMessage = message;
        mShowDonNotAsk = showDonNotAsk;
        mDetailMessage = detailMessage;
        mShowForegroundChooser = showForegroundChooser;

        // If this is a second (or later) permission and the views exist, then animate.
        if (mIconView != null) {
            updateAll();
        }
    }

    private void updateAll() {
        updateDescription();
        updateDetailDescription();
        updateDoNotAskAndForegroundOption();
    }

    @Override
    public View createView() {
        mRootView = (ViewGroup) LayoutInflater.from(mActivity)
                .inflate(R.layout.grant_permissions, null);

        mMessageView = (TextView) mRootView.findViewById(R.id.permission_message);
        mDetailMessageView = (TextView) mRootView.findViewById(R.id.detail_message);
        mIconView = (ImageView) mRootView.findViewById(R.id.permission_icon);
        mAllowButton = (Button) mRootView.findViewById(R.id.permission_allow_button);
        mAllowButton.setOnClickListener(this);
        mAllowAlwaysButton = (Button) mRootView.findViewById(R.id.permission_allow_always_button);
        mAllowAlwaysButton.setOnClickListener(this);
        mAllowForegroundButton =
                (Button) mRootView.findViewById(R.id.permission_allow_foreground_only_button);
        mAllowForegroundButton.setOnClickListener(this);
        mDenyButton = (Button) mRootView.findViewById(R.id.permission_deny_button);
        mDenyButton.setOnClickListener(this);
        mDenyAndDontAskAgainButton =
                (Button) mRootView.findViewById(R.id.permission_deny_and_dont_ask_again_button);
        mDenyAndDontAskAgainButton.setOnClickListener(this);

        mRootView.findViewById(R.id.permission_deny_button).setOnClickListener(this);

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

    private void updateDoNotAskAndForegroundOption() {
        if (mShowForegroundChooser) {
            mAllowButton.setVisibility(View.GONE);
            mAllowAlwaysButton.setVisibility(View.VISIBLE);
            mAllowForegroundButton.setVisibility(View.VISIBLE);
        } else {
            mAllowButton.setVisibility(View.VISIBLE);
            mAllowAlwaysButton.setVisibility(View.GONE);
            mAllowForegroundButton.setVisibility(View.GONE);
        }
        if (mShowDonNotAsk) {
            mDenyAndDontAskAgainButton.setVisibility(View.VISIBLE);
        } else {
            mDenyAndDontAskAgainButton.setVisibility(View.GONE);
        }

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.permission_allow_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
                }
                break;
            case R.id.permission_allow_always_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
                }
                break;
            case R.id.permission_allow_foreground_only_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName,
                            GRANTED_FOREGROUND_ONLY);
                }
                break;
            case R.id.permission_deny_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName, DENIED);
                }
                break;
            case R.id.permission_deny_and_dont_ask_again_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    mResultListener.onPermissionGrantResult(mGroupName,
                            DENIED_DO_NOT_ASK_AGAIN);
                }
                break;
            case R.id.permission_more_info_button:
                Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mAppPackageName);
                intent.putExtra(ManagePermissionsActivity.EXTRA_ALL_PERMISSIONS, true);
                mActivity.startActivity(intent);
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
