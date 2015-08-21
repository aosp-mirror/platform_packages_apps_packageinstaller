package com.android.packageinstaller.permission.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.android.packageinstaller.R;

/**
 * Watch-specific view handler for the grant permissions activity.
 */
final class GrantPermissionsWatchViewHandler extends PermissionConfirmationViewHandler
        implements GrantPermissionsViewHandler {
    private static final String TAG = "GrantPermissionsViewH";

    private static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";

    private final Context mContext;

    private ResultListener mResultListener;

    private String mGroupName;
    private boolean mShowDoNotAsk;

    private CharSequence mMessage;
    private String mCurrentPageText;
    private Icon mIcon;

    GrantPermissionsWatchViewHandler(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public GrantPermissionsWatchViewHandler setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public View createView() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "createView()");
        }

        mShowDoNotAsk = false;

        return super.createView();
    }

    @Override
    public void updateWindowAttributes(WindowManager.LayoutParams outLayoutParams) {
        outLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        outLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        outLayoutParams.format = PixelFormat.OPAQUE;
        outLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG;
        outLayoutParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, boolean showDoNotAsk) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "updateUi() - groupName: " + groupName
                            + ", groupCount: " + groupCount
                            + ", groupIndex: " + groupIndex
                            + ", icon: " + icon
                            + ", message: " + message
                            + ", showDoNotAsk: " + showDoNotAsk);
        }

        mGroupName = groupName;
        mShowDoNotAsk = showDoNotAsk;
        mMessage = message;
        mIcon = icon;
        mCurrentPageText = (groupCount > 1 ?
                mContext.getString(R.string.current_permission_template, groupIndex + 1, groupCount)
                : null);

        invalidate();
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
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, false, false);
        }
    }

    @Override // PermissionConfirmationViewHandler
    public void onAllow() {
        onClick(true /* granted */, false /* doNotAskAgain */);
    }

    @Override // PermissionConfirmationViewHandler
    public void onDeny() {
        onClick(false /* granted */, false /* doNotAskAgain */);
    }

    @Override // PermissionConfirmationViewHandler
    public void onDenyDoNotAskAgain() {
        onClick(false /* granted */, true /* doNotAskAgain */);
    }

    @Override // PermissionConfirmationViewHandler
    public CharSequence getCurrentPageText() {
        return mCurrentPageText;
    }

    @Override // PermissionConfirmationViewHandler
    public Icon getPermissionIcon() {
        return mIcon;
    }

    @Override // PermissionConfirmationViewHandler
    public CharSequence getMessage() {
        return mMessage;
    }

    @Override // PermissionConfirmationViewHandler
    public int getButtonBarMode() {
        return mShowDoNotAsk ? MODE_VERTICAL_BUTTONS : MODE_HORIZONTAL_BUTTONS;
    }

    @Override // PermissionConfirmationViewHandler
    public CharSequence getVerticalAllowText() {
        return mContext.getString(R.string.grant_dialog_button_allow);
    }

    @Override // PermissionConfirmationViewHandler
    public CharSequence getVerticalDenyText() {
        return mContext.getString(R.string.grant_dialog_button_deny);
    }

    @Override // PermissionConfirmationViewHandler
    public CharSequence getVerticalDenyDoNotAskAgainText() {
        return mContext.getString(R.string.grant_dialog_button_deny_dont_ask_again);
    }

    private void onClick(boolean granted, boolean doNotAskAgain) {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, granted, doNotAskAgain);
        }
    }
}
