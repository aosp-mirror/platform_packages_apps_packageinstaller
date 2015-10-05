package com.android.packageinstaller.permission.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.ui.wear.ConfirmationViewHandler;

/**
 * Watch-specific view handler for the grant permissions activity.
 */
final class GrantPermissionsWatchViewHandler extends ConfirmationViewHandler
        implements GrantPermissionsViewHandler {
    private static final String TAG = "GrantPermsWatchViewH";

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
        outLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
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

    @Override // ConfirmationViewHandler
    public void onButton1() {
        onClick(true /* granted */, false /* doNotAskAgain */);
    }

    @Override // ConfirmationViewHandler
    public void onButton2() {
        onClick(false /* granted */, false /* doNotAskAgain */);
    }

    @Override // ConfirmationViewHandler
    public void onButton3() {
        onClick(false /* granted */, true /* doNotAskAgain */);
    }

    @Override // ConfirmationViewHandler
    public CharSequence getCurrentPageText() {
        return mCurrentPageText;
    }

    @Override // ConfirmationViewHandler
    public Icon getPermissionIcon() {
        return mIcon;
    }

    @Override // ConfirmationViewHandler
    public CharSequence getMessage() {
        return mMessage;
    }

    @Override // ConfirmationViewHandler
    public int getButtonBarMode() {
        return mShowDoNotAsk ? MODE_VERTICAL_BUTTONS : MODE_HORIZONTAL_BUTTONS;
    }

    @Override // ConfirmationViewHandler
    public CharSequence getVerticalButton1Text() {
        return mContext.getString(R.string.grant_dialog_button_allow);
    }

    @Override // ConfirmationViewHandler
    public CharSequence getVerticalButton2Text() {
        return mContext.getString(R.string.grant_dialog_button_deny);
    }

    @Override // ConfirmationViewHandler
    public CharSequence getVerticalButton3Text() {
        return mContext.getString(R.string.grant_dialog_button_deny_dont_ask_again);
    }

    @Override // ConfirmationViewHandler
    public Drawable getVerticalButton1Icon(){
        return mContext.getDrawable(R.drawable.confirm_button);
    }

    @Override // ConfirmationViewHandler
    public Drawable getVerticalButton2Icon(){
        return mContext.getDrawable(R.drawable.cancel_button);
    }

    @Override // ConfirmationViewHandler
    public Drawable getVerticalButton3Icon(){
        return mContext.getDrawable(R.drawable.deny_button);
    }

    private void onClick(boolean granted, boolean doNotAskAgain) {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, granted, doNotAskAgain);
        }
    }
}
