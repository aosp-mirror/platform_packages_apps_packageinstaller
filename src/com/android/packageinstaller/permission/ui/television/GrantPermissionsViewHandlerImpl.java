package com.android.packageinstaller.permission.ui.television;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler;

/**
 * TV-specific view handler for the grant permissions activity.
 */
public final class GrantPermissionsViewHandlerImpl implements GrantPermissionsViewHandler, OnClickListener {

    private static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";

    private final Context mContext;

    private ResultListener mResultListener;

    private String mGroupName;

    private LinearLayout mRootView;
    private TextView mMessageView;
    private ImageView mIconView;
    private TextView mCurrentGroupView;
    private Button mAllowButton;
    private Button mSoftDenyButton;
    private Button mHardDenyButton;

    public GrantPermissionsViewHandlerImpl(Context context) {
        mContext = context;
    }

    @Override
    public GrantPermissionsViewHandlerImpl setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public View createView() {
        mRootView = (LinearLayout) LayoutInflater.from(mContext)
                .inflate(R.layout.grant_permissions, null);

        mMessageView = (TextView) mRootView.findViewById(R.id.permission_message);
        mIconView = (ImageView) mRootView.findViewById(R.id.permission_icon);
        mCurrentGroupView = (TextView) mRootView.findViewById(R.id.current_page_text);
        mAllowButton = (Button) mRootView.findViewById(R.id.permission_allow_button);
        mSoftDenyButton = (Button) mRootView.findViewById(R.id.permission_deny_button);
        mHardDenyButton = (Button) mRootView.findViewById(
                R.id.permission_deny_dont_ask_again_button);

        mAllowButton.setOnClickListener(this);
        mSoftDenyButton.setOnClickListener(this);
        mHardDenyButton.setOnClickListener(this);

        return mRootView;
    }

    @Override
    public void updateWindowAttributes(WindowManager.LayoutParams outLayoutParams) {
        outLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        outLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        outLayoutParams.format = PixelFormat.OPAQUE;
        outLayoutParams.gravity = Gravity.BOTTOM;
        outLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
        outLayoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, boolean showDoNotAsk) {
        mGroupName = groupName;

        mMessageView.setText(message);
        mIconView.setImageIcon(icon);
        mHardDenyButton.setVisibility(showDoNotAsk ? View.VISIBLE : View.GONE);
        if (groupCount > 1) {
            mCurrentGroupView.setVisibility(View.VISIBLE);
            mCurrentGroupView.setText(mContext.getString(R.string.current_permission_template,
                    groupIndex + 1, groupCount));
        } else {
            mCurrentGroupView.setVisibility(View.INVISIBLE);
        }
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
    public void onClick(View view) {
        boolean granted = false;
        boolean doNotAskAgain = false;
        switch (view.getId()) {
            case R.id.permission_allow_button:
                granted = true;
                break;
            case R.id.permission_deny_dont_ask_again_button:
                doNotAskAgain = true;
                break;
        }
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, granted, doNotAskAgain);
        }
    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, false, false);
        }
    }
}
