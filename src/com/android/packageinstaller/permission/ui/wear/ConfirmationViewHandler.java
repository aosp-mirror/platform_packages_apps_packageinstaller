package com.android.packageinstaller.permission.ui.wear;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.packageinstaller.R;

public abstract class ConfirmationViewHandler implements
        Handler.Callback,
        View.OnClickListener,
        ViewTreeObserver.OnScrollChangedListener  {
    public static final int MODE_HORIZONTAL_BUTTONS = 0;
    public static final int MODE_VERTICAL_BUTTONS = 1;

    private static final int MSG_HIDE_BUTTON_BAR = 1001;
    private static final long HIDE_ANIM_DURATION = 500;

    private View mRoot;
    private TextView mCurrentPageText;
    private ImageView mIcon;
    private TextView mMessage;
    private ScrollView mScrollingContainer;
    private ViewGroup mContent;
    private ViewGroup mHorizontalButtonBar;
    private ViewGroup mVerticalButtonBar;
    private Button mVerticalButton1;
    private Button mVerticalButton2;
    private Button mVerticalButton3;
    private View mButtonBarContainer;

    private Context mContext;

    private Handler mHideHandler;
    private Interpolator mInterpolator;
    private float mButtonBarFloatingHeight;
    private ObjectAnimator mButtonBarAnimator;
    private float mCurrentTranslation;
    private boolean mHiddenBefore;

    // TODO: Move these into a builder
    /** In the 2 button layout, this is allow button */
    public abstract void onButton1();
    /** In the 2 button layout, this is deny button */
    public abstract void onButton2();
    public abstract void onButton3();
    public abstract CharSequence getVerticalButton1Text();
    public abstract CharSequence getVerticalButton2Text();
    public abstract CharSequence getVerticalButton3Text();
    public abstract Drawable getVerticalButton1Icon();
    public abstract Drawable getVerticalButton2Icon();
    public abstract Drawable getVerticalButton3Icon();
    public abstract CharSequence getCurrentPageText();
    public abstract Icon getPermissionIcon();
    public abstract CharSequence getMessage();

    public ConfirmationViewHandler(Context context) {
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

        mVerticalButton1 = (Button) mRoot.findViewById(R.id.vertical_button1);
        mVerticalButton2 = (Button) mRoot.findViewById(R.id.vertical_button2);
        mVerticalButton3 = (Button) mRoot.findViewById(R.id.vertical_button3);
        mVerticalButton1.setOnClickListener(this);
        mVerticalButton2.setOnClickListener(this);
        mVerticalButton3.setOnClickListener(this);

        mInterpolator = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.fast_out_slow_in);
        mButtonBarFloatingHeight = mContext.getResources().getDimension(
                R.dimen.conf_diag_floating_height);
        mHideHandler = new Handler(this);

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
            mCurrentPageText.setVisibility(View.GONE);
        }

        Icon icon = getPermissionIcon();
        if (icon != null) {
            mIcon.setImageIcon(icon);
            mIcon.setVisibility(View.VISIBLE);
        } else {
            mIcon.setVisibility(View.GONE);
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
                mVerticalButton1.setText(getVerticalButton1Text());
                mVerticalButton2.setText(getVerticalButton2Text());
                mVerticalButton3.setText(getVerticalButton3Text());

                mVerticalButton1.setCompoundDrawablesWithIntrinsicBounds(
                        getVerticalButton1Icon(), null, null, null);
                mVerticalButton2.setCompoundDrawablesWithIntrinsicBounds(
                        getVerticalButton2Icon(), null, null, null);
                mVerticalButton3.setCompoundDrawablesWithIntrinsicBounds(
                        getVerticalButton3Icon(), null, null, null);
                break;
        }

        mScrollingContainer.scrollTo(0, 0);

        mScrollingContainer.getViewTreeObserver().addOnScrollChangedListener(this);

        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Setup Button animation.
                        // pop the button bar back to full height, stop all animation
                        if (mButtonBarAnimator != null) {
                            mButtonBarAnimator.cancel();
                        }

                        // In order to fake the buttons peeking at the bottom, need to do set the
                        // padding properly.
                        if (mContent.getPaddingBottom() != mButtonBarContainer.getHeight()) {
                            mContent.setPadding(0, 0, 0, mButtonBarContainer.getHeight());
                        }

                        // stop any calls to hide the button bar in the future
                        mHideHandler.removeMessages(MSG_HIDE_BUTTON_BAR);
                        mHiddenBefore = false;

                        // determine which mode the scrolling should work at.
                        if (mContent.getHeight() > mScrollingContainer.getHeight()) {
                            mButtonBarContainer.setTranslationZ(mButtonBarFloatingHeight);
                            mHideHandler.sendEmptyMessageDelayed(MSG_HIDE_BUTTON_BAR, 3000);
                            generateButtonBarAnimator(mButtonBarContainer.getHeight(), 0, 0,
                                    mButtonBarFloatingHeight, 1000);
                        } else {
                            mButtonBarContainer.setTranslationY(0);
                            mButtonBarContainer.setTranslationZ(0);
                        }
                        mRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.horizontal_allow_button:
            case R.id.vertical_button1:
                onButton1();
                break;
            case R.id.horizontal_deny_button:
            case R.id.vertical_button2:
                onButton2();
                break;
            case R.id.vertical_button3:
                onButton3();
                break;
        }
    }

    @Override
    public boolean handleMessage (Message msg) {
        switch (msg.what) {
            case MSG_HIDE_BUTTON_BAR:
                hideButtonBar();
                return true;
        }
        return false;
    }

    @Override
    public void onScrollChanged() {
        mHideHandler.removeMessages(MSG_HIDE_BUTTON_BAR);
        hideButtonBar();
    }

    private void hideButtonBar() {
        // get the offset to the top of the button bar
        int offset = mScrollingContainer.getHeight() + mButtonBarContainer.getHeight() -
                mContent.getHeight() + Math.max(mScrollingContainer.getScrollY(), 0);
        // The desired margin space between the button bar and the bottom of the dialog text
        int topMargin = mContext.getResources().getDimensionPixelSize(
                R.dimen.conf_diag_button_container_top_margin);
        int translationY = topMargin + (offset > 0 ?
                mButtonBarContainer.getHeight() - offset : mButtonBarContainer.getHeight());

        if (!mHiddenBefore || mButtonBarAnimator == null) {
            // hasn't hidden the bar yet, just hide now to the right height
            generateButtonBarAnimator(
                    mButtonBarContainer.getTranslationY(), translationY,
                    mButtonBarFloatingHeight, 0, HIDE_ANIM_DURATION);
        } else if (mButtonBarAnimator.isRunning()) {
            // we are animating the button bar closing, change to animate to the right place
            if (Math.abs(mCurrentTranslation - translationY) > 1e-2f) {
                mButtonBarAnimator.cancel(); // stop current animation

                if (Math.abs(mButtonBarContainer.getTranslationY() - translationY) > 1e-2f) {
                    long duration = Math.max((long) (
                            (float) HIDE_ANIM_DURATION
                                    * (translationY - mButtonBarContainer.getTranslationY())
                                    / mButtonBarContainer.getHeight()), 0);
                    generateButtonBarAnimator(
                            mButtonBarContainer.getTranslationY(), translationY,
                            mButtonBarFloatingHeight, 0, duration);
                } else {
                    mButtonBarContainer.setTranslationY(translationY);
                    mButtonBarContainer.setTranslationZ(0);
                }
            }
        } else {
            // not currently animating, have already hidden, snap to the right offset
            mButtonBarContainer.setTranslationY(translationY);
            mButtonBarContainer.setTranslationZ(0);
        }

        mHiddenBefore = true;
    }

    private void generateButtonBarAnimator(
            float startY, float endY, float startZ, float endZ, long duration) {
        mButtonBarAnimator =
                ObjectAnimator.ofPropertyValuesHolder(
                        mButtonBarContainer,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startY, endY),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Z, startZ, endZ));
        mCurrentTranslation = endY;
        mButtonBarAnimator.setDuration(duration);
        mButtonBarAnimator.setInterpolator(mInterpolator);
        mButtonBarAnimator.start();
    }
}