package com.android.packageinstaller.permission.ui.wear;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
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
        ViewTreeObserver.OnScrollChangedListener,
        ViewTreeObserver.OnGlobalLayoutListener {
    private static final String TAG = "ConfirmationViewHandler";
    
    public static final int MODE_HORIZONTAL_BUTTONS = 0;
    public static final int MODE_VERTICAL_BUTTONS = 1;

    private static final int MSG_SHOW_BUTTON_BAR = 1001;
    private static final int MSG_HIDE_BUTTON_BAR = 1002;
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
        mRoot = LayoutInflater.from(mContext).inflate(R.layout.confirmation_dialog, null);

        mMessage = (TextView) mRoot.findViewById(R.id.message);
        mCurrentPageText = (TextView) mRoot.findViewById(R.id.current_page_text);
        mIcon = (ImageView) mRoot.findViewById(R.id.icon);
        mButtonBarContainer = mRoot.findViewById(R.id.button_bar_container);
        mContent = (ViewGroup) mRoot.findViewById(R.id.content);
        mScrollingContainer = (ScrollView) mRoot.findViewById(R.id.scrolling_container);
        mHorizontalButtonBar = (ViewGroup) mRoot.findViewById(R.id.horizontal_button_bar);
        mVerticalButtonBar = (ViewGroup) mRoot.findViewById(R.id.vertical_button_bar);

        Button horizontalAllow = (Button) mRoot.findViewById(R.id.permission_allow_button);
        Button horizontalDeny = (Button) mRoot.findViewById(R.id.permission_deny_button);
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
        mHideHandler = new Handler(Looper.getMainLooper(), this);

        mScrollingContainer.getViewTreeObserver().addOnScrollChangedListener(this);
        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(this);

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

                mVerticalButton1.setCompoundDrawablesWithIntrinsicBounds(
                        getVerticalButton1Icon(), null, null, null);
                mVerticalButton2.setCompoundDrawablesWithIntrinsicBounds(
                        getVerticalButton2Icon(), null, null, null);

                CharSequence verticalButton3Text = getVerticalButton3Text();
                if (TextUtils.isEmpty(verticalButton3Text)) {
                    mVerticalButton3.setVisibility(View.GONE);
                } else {
                    mVerticalButton3.setText(getVerticalButton3Text());
                    mVerticalButton3.setCompoundDrawablesWithIntrinsicBounds(
                            getVerticalButton3Icon(), null, null, null);
                }
                break;
        }

        mScrollingContainer.scrollTo(0, 0);

        mHideHandler.removeMessages(MSG_HIDE_BUTTON_BAR);
        mHideHandler.removeMessages(MSG_SHOW_BUTTON_BAR);
    }

    @Override
    public void onGlobalLayout() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onGlobalLayout");
            Log.d(TAG, "    contentHeight: " + mContent.getHeight());
        }

        if (mButtonBarAnimator != null) {
            mButtonBarAnimator.cancel();
        }

        // In order to fake the buttons peeking at the bottom, need to do set the
        // padding properly.
        if (mContent.getPaddingBottom() != mButtonBarContainer.getHeight()) {
            mContent.setPadding(mContent.getPaddingLeft(), mContent.getPaddingTop(),
                    mContent.getPaddingRight(), mButtonBarContainer.getHeight());
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "    set mContent.PaddingBottom: " + mButtonBarContainer.getHeight());
            }
        }

        mButtonBarContainer.setTranslationY(mButtonBarContainer.getHeight());

        // Give everything a chance to render
        mHideHandler.removeMessages(MSG_HIDE_BUTTON_BAR);
        mHideHandler.removeMessages(MSG_SHOW_BUTTON_BAR);
        mHideHandler.sendEmptyMessageDelayed(MSG_SHOW_BUTTON_BAR, 50);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.permission_allow_button:
            case R.id.vertical_button1:
                onButton1();
                break;
            case R.id.permission_deny_button:
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
            case MSG_SHOW_BUTTON_BAR:
                showButtonBar();
                return true;
            case MSG_HIDE_BUTTON_BAR:
                hideButtonBar();
                return true;
        }
        return false;
    }

    @Override
    public void onScrollChanged () {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onScrollChanged");
        }
        mHideHandler.removeMessages(MSG_HIDE_BUTTON_BAR);
        hideButtonBar();
    }

    private void showButtonBar() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "showButtonBar");
        }

        // Setup Button animation.
        // pop the button bar back to full height, stop all animation
        if (mButtonBarAnimator != null) {
            mButtonBarAnimator.cancel();
        }

        // stop any calls to hide the button bar in the future
        mHideHandler.removeMessages(MSG_HIDE_BUTTON_BAR);
        mHiddenBefore = false;

        // Evaluate the max height the button bar can go
        final int screenHeight = mRoot.getHeight();
        final int halfScreenHeight = screenHeight / 2;
        final int buttonBarHeight = mButtonBarContainer.getHeight();
        final int contentHeight = mContent.getHeight() - buttonBarHeight;
        final int buttonBarMaxHeight =
                Math.min(buttonBarHeight, halfScreenHeight);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "    screenHeight: " + screenHeight);
            Log.d(TAG, "    contentHeight: " + contentHeight);
            Log.d(TAG, "    buttonBarHeight: " + buttonBarHeight);
            Log.d(TAG, "    buttonBarMaxHeight: " + buttonBarMaxHeight);
        }

        mButtonBarContainer.setTranslationZ(mButtonBarFloatingHeight);

        // Only hide the button bar if it is occluding the content or the button bar is bigger than
        // half the screen
        if (contentHeight > (screenHeight - buttonBarHeight)
                || buttonBarHeight > halfScreenHeight) {
            mHideHandler.sendEmptyMessageDelayed(MSG_HIDE_BUTTON_BAR, 3000);
        }

        generateButtonBarAnimator(buttonBarHeight,
                buttonBarHeight - buttonBarMaxHeight, 0, mButtonBarFloatingHeight, 1000);
    }

    private void hideButtonBar() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "hideButtonBar");
        }

        // The desired margin space between the button bar and the bottom of the dialog text
        final int topMargin = mContext.getResources().getDimensionPixelSize(
                R.dimen.conf_diag_button_container_top_margin);
        final int contentHeight = mContent.getHeight() + topMargin;
        final int screenHeight = mRoot.getHeight();
        final int buttonBarHeight = mButtonBarContainer.getHeight();

        final int offset = screenHeight + buttonBarHeight
                - contentHeight + Math.max(mScrollingContainer.getScrollY(), 0);
        final int translationY = (offset > 0 ?
                mButtonBarContainer.getHeight() - offset : mButtonBarContainer.getHeight());

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "    topMargin: " + topMargin);
            Log.d(TAG, "    contentHeight: " + contentHeight);
            Log.d(TAG, "    screenHeight: " + screenHeight);
            Log.d(TAG, "    offset: " + offset);
            Log.d(TAG, "    buttonBarHeight: " + buttonBarHeight);
            Log.d(TAG, "    mContent.getPaddingBottom(): " + mContent.getPaddingBottom());
            Log.d(TAG, "    mScrollingContainer.getScrollY(): " + mScrollingContainer.getScrollY());
            Log.d(TAG, "    translationY: " + translationY);
        }

        if (!mHiddenBefore || mButtonBarAnimator == null) {
            // Remove previous call to MSG_SHOW_BUTTON_BAR if the user scrolled or something before
            // the animation got a chance to play
            mHideHandler.removeMessages(MSG_SHOW_BUTTON_BAR);

            if(mButtonBarAnimator != null) {
                mButtonBarAnimator.cancel(); // stop current animation if there is one playing
            }

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
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "generateButtonBarAnimator");
            Log.d(TAG, "    startY: " + startY);
            Log.d(TAG, "    endY: " + endY);
            Log.d(TAG, "    startZ: " + startZ);
            Log.d(TAG, "    endZ: " + endZ);
            Log.d(TAG, "    duration: " + duration);
        }

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
