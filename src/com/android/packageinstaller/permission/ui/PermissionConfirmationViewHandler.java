package com.android.packageinstaller.permission.ui;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.packageinstaller.R;

public abstract class PermissionConfirmationViewHandler implements
        View.OnClickListener {
    public static final int MODE_HORIZONTAL_BUTTONS = 0;
    public static final int MODE_VERTICAL_BUTTONS = 1;

    private View mRoot;
    private TextView mCurrentPageText;
    private ImageView mIcon;
    private TextView mMessage;
    private ScrollView mScrollingContainer;
    private ViewGroup mContent;
    private ViewGroup mHorizontalButtonBar;
    private ViewGroup mVerticalButtonBar;
    private Button mVerticalAllow;
    private Button mVerticalDeny;
    private Button mVerticalDenyDoNotAskAgain;
    private View mButtonBarContainer;

    private Context mContext;

    // TODO: Move these into a builder
    public abstract void onAllow();
    public abstract void onDeny();
    public abstract void onDenyDoNotAskAgain();
    public abstract CharSequence getVerticalAllowText();
    public abstract CharSequence getVerticalDenyText();
    public abstract CharSequence getVerticalDenyDoNotAskAgainText();
    public abstract CharSequence getCurrentPageText();
    public abstract Icon getPermissionIcon();
    public abstract CharSequence getMessage();

    public PermissionConfirmationViewHandler(Context context) {
        mContext = context;
    }

    public View createView() {
        mRoot = LayoutInflater.from(mContext).inflate(R.layout.grant_permissions, null);

        mMessage = (TextView) mRoot.findViewById(R.id.message);
        mCurrentPageText = (TextView) mRoot.findViewById(R.id.current_page_text);
        mIcon = (ImageView) mRoot.findViewById(R.id.icon);
        mButtonBarContainer = mRoot.findViewById(R.id.button_bar_container);
        mContent = (ViewGroup) mRoot.findViewById(R.id.content);
        mScrollingContainer = (ScrollView) mRoot.findViewById(R.id.scrolling_container);
        mHorizontalButtonBar = (ViewGroup) mRoot.findViewById(R.id.horizontal_button_bar);
        mVerticalButtonBar = (ViewGroup) mRoot.findViewById(R.id.vertical_button_bar);

        Button horizontalAllow = (Button) mRoot.findViewById(R.id.horizontal_allow_button);
        Button horizontalDeny = (Button) mRoot.findViewById(R.id.horizontal_deny_button);
        horizontalAllow.setOnClickListener(this);
        horizontalDeny.setOnClickListener(this);

        mVerticalAllow = (Button) mRoot.findViewById(R.id.vertical_allow_button);
        mVerticalDeny = (Button) mRoot.findViewById(R.id.vertical_deny_button);
        mVerticalDenyDoNotAskAgain =
                (Button) mRoot.findViewById(R.id.vertical_deny_do_not_ask_again_button);
        mVerticalAllow.setOnClickListener(this);
        mVerticalDeny.setOnClickListener(this);
        mVerticalDenyDoNotAskAgain.setOnClickListener(this);

        return mRoot;
    }

    /**
     * Child class should override this for other modes.  Call invalidate() to update the UI to the
     * new button mode.
     * @return The current mode the layout should use for the buttons
     */
    public int getButtonBarMode() {
        return MODE_HORIZONTAL_BUTTONS;
    }

    public void invalidate() {
        CharSequence currentPageText = getCurrentPageText();
        if (!TextUtils.isEmpty(currentPageText)) {
            mCurrentPageText.setText(currentPageText);
            mCurrentPageText.setVisibility(View.VISIBLE);
        } else {
            mCurrentPageText.setVisibility(View.INVISIBLE);
        }

        Icon icon = getPermissionIcon();
        if (icon != null) {
            mIcon.setImageIcon(icon);
            mIcon.setVisibility(View.VISIBLE);
        } else {
            mIcon.setVisibility(View.INVISIBLE);
        }

        mMessage.setText(getMessage());

        switch (getButtonBarMode()) {
            case MODE_HORIZONTAL_BUTTONS:
                mHorizontalButtonBar.setVisibility(View.VISIBLE);
                mVerticalButtonBar.setVisibility(View.GONE);
                break;
            case MODE_VERTICAL_BUTTONS:
                mHorizontalButtonBar.setVisibility(View.GONE);
                mVerticalButtonBar.setVisibility(View.VISIBLE);
                mVerticalAllow.setText(getVerticalAllowText());
                mVerticalDeny.setText(getVerticalDenyText());
                mVerticalDenyDoNotAskAgain.setText(getVerticalDenyDoNotAskAgainText());

                mVerticalAllow.setCompoundDrawablesWithIntrinsicBounds(
                        mContext.getDrawable(R.drawable.confirm_button), null, null, null);
                mVerticalDeny.setCompoundDrawablesWithIntrinsicBounds(
                        mContext.getDrawable(R.drawable.cancel_button), null, null, null);
                mVerticalDenyDoNotAskAgain.setCompoundDrawablesWithIntrinsicBounds(
                        mContext.getDrawable(R.drawable.cancel_button), null, null, null);
                break;
        }

        mScrollingContainer.scrollTo(0, 0);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.horizontal_allow_button:
            case R.id.vertical_allow_button:
                onAllow();
                break;
            case R.id.horizontal_deny_button:
            case R.id.vertical_deny_button:
                onDeny();
                break;
            case R.id.vertical_deny_do_not_ask_again_button:
                onDenyDoNotAskAgain();
                break;
        }
    }
}
