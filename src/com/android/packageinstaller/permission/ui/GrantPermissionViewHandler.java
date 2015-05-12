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

package com.android.packageinstaller.permission.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.packageinstaller.R;

public final class GrantPermissionViewHandler implements OnClickListener {

    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    public static final String ARG_GROUP_SHOW_DO_NOT_ASK = "ARG_GROUP_SHOW_DO_NOT_ASK";
    public static final String ARG_GROUP_DO_NOT_ASK_CHECKED = "ARG_GROUP_DO_NOT_ASK_CHECKED";

    // Animation parameters.
    private static final long SIZE_START_DELAY = 300;
    private static final long SIZE_START_LENGTH = 233;
    private static final long FADE_OUT_START_DELAY = 300;
    private static final long FADE_OUT_START_LENGTH = 217;
    private static final long TRANSLATE_START_DELAY = 367;
    private static final long TRANSLATE_LENGTH = 317;
    private static final long GROUP_UPDATE_DELAY = 400;

    private final OnRequestGrantPermissionGroupResult mResultListener;
    private final Context mContext;

    private String mGroupName;
    private int mGroupCount;
    private int mGroupIndex;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private boolean mShowDonNotAsk;
    private boolean mDoNotAskChecked;

    private ImageView mIconView;
    private TextView mCurrentGroupView;
    private TextView mMessageView;
    private CheckBox mDoNotAskCheckbox;
    private Button mAllowButton;

    private ViewHeightController mRootViewHeightController;
    private ManualLayoutFrame mRootView;

    // Needed for animation
    private ViewGroup mDescContainer;
    private ViewGroup mCurrentDesc;
    private ViewGroup mNextDesc;

    private ViewGroup mDialogContainer;

    private final Runnable mUpdateGroup = new Runnable() {
        @Override
        public void run() {
            updateGroup();
        }
    };

    public interface OnRequestGrantPermissionGroupResult {
        public void onRequestGrantPermissionGroupResult(String name, boolean granted,
                boolean doNotAskAgain);
    }

    public GrantPermissionViewHandler(OnRequestGrantPermissionGroupResult resultListener,
            Context context) {
        mResultListener = resultListener;
        mContext = context;
    }

    public void onSaveInstanceState(Bundle arguments) {
        arguments.putString(ARG_GROUP_NAME, mGroupName);
        arguments.putInt(ARG_GROUP_COUNT, mGroupCount);
        arguments.putInt(ARG_GROUP_INDEX, mGroupIndex);
        arguments.putParcelable(ARG_GROUP_ICON, mGroupIcon);
        arguments.putCharSequence(ARG_GROUP_MESSAGE, mGroupMessage);
        arguments.putBoolean(ARG_GROUP_SHOW_DO_NOT_ASK, mShowDonNotAsk);
        arguments.putBoolean(ARG_GROUP_DO_NOT_ASK_CHECKED, mDoNotAskCheckbox.isChecked());
    }

    public void loadSavedInstance(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        mGroupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE);
        mGroupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON);
        mGroupCount = savedInstanceState.getInt(ARG_GROUP_COUNT);
        mGroupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX);
        mShowDonNotAsk = savedInstanceState.getBoolean(ARG_GROUP_SHOW_DO_NOT_ASK);
        mDoNotAskChecked = savedInstanceState.getBoolean(ARG_GROUP_DO_NOT_ASK_CHECKED);
    }

    public void showPermission(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, boolean showDonNotAsk) {
        mGroupName = groupName;
        mGroupCount = groupCount;
        mGroupIndex = groupIndex;
        mGroupIcon = icon;
        mGroupMessage = message;
        mShowDonNotAsk = showDonNotAsk;
        mDoNotAskChecked = false;
        // If this is a second (or later) permission and the views exist, then animate.
        if (mIconView != null) {
            if (mGroupIndex > 0) {
                animateToPermission();
            } else {
                updateDescription();
                updateGroup();
            }
        }

        updateDoNotAskCheckBox();
    }

    private void animateToPermission() {
        if (mRootViewHeightController == null) {
            // Allow height control of the real root view, not the root of what we inflate.
            // Need to do it on the root view so that the background drawable of the dialog
            // moves with the animation.
            View realRootView = mRootView.getViewRootImpl().getView();
            mRootViewHeightController = new ViewHeightController(realRootView);
            mRootViewHeightController.setHeight(realRootView.getHeight());
        }

        // Grab the current height/y positions, then wait for the layout to change,
        // so we can get the end height/y positions.
        final SparseArray<Float> startPositions = getViewPositions();
        final int startHeight = mRootView.getLayoutHeight();
        mRootView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                    int oldTop, int oldRight, int oldBottom) {
                mRootView.removeOnLayoutChangeListener(this);
                SparseArray<Float> endPositions = getViewPositions();
                int endHeight = mRootView.getLayoutHeight();
                animateYPos(startPositions, endPositions, endHeight - startHeight);
            }
        });

        // Fade out old description group and scale out the icon for it.
        Interpolator interpolator = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.fast_out_linear_in);
        mIconView.animate()
                .scaleX(0)
                .scaleY(0)
                .setStartDelay(FADE_OUT_START_DELAY)
                .setDuration(FADE_OUT_START_LENGTH)
                .setInterpolator(interpolator)
                .start();
        mCurrentDesc.animate()
                .alpha(0)
                .setStartDelay(FADE_OUT_START_DELAY)
                .setDuration(FADE_OUT_START_LENGTH)
                .setInterpolator(interpolator)
                .setListener(null)
                .start();

        // Update the index of the permission after the animations have started.
        mCurrentGroupView.getHandler().postDelayed(mUpdateGroup, GROUP_UPDATE_DELAY);

        // Add the new description and translate it in.
        mNextDesc = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.permission_description, mDescContainer, false);

        mMessageView = (TextView) mNextDesc.findViewById(R.id.permission_message);
        mIconView = (ImageView) mNextDesc.findViewById(R.id.permission_icon);
        updateDescription();

        int width = mDescContainer.getRootView().getWidth();
        mDescContainer.addView(mNextDesc);
        mNextDesc.setTranslationX(width);
        final View oldDesc = mCurrentDesc;
        mCurrentDesc = mNextDesc;
        mNextDesc.animate()
                .translationX(0)
                .setStartDelay(TRANSLATE_START_DELAY)
                .setDuration(TRANSLATE_LENGTH)
                .setInterpolator(AnimationUtils.loadInterpolator(mContext,
                        android.R.interpolator.linear_out_slow_in))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // This is the longest animation, when it finishes, we are done.
                        mDescContainer.removeView(oldDesc);
                    }
                })
                .start();
    }

    private SparseArray<Float> getViewPositions() {
        SparseArray<Float> locMap = new SparseArray<>();
        final int N = mDialogContainer.getChildCount();
        for (int i = 0; i < N; i++) {
            View child = mDialogContainer.getChildAt(i);
            if (child.getId() <= 0) {
                // Only track views with ids.
                continue;
            }
            locMap.put(child.getId(), child.getY());
        }
        return locMap;
    }

    private void animateYPos(SparseArray<Float> startPositions, SparseArray<Float> endPositions,
            int heightDiff) {
        final int N = startPositions.size();
        for (int i = 0; i < N; i++) {
            int key = startPositions.keyAt(i);
            float start = startPositions.get(key);
            float end = endPositions.get(key);
            if (start != end) {
                final View child = mDialogContainer.findViewById(key);
                child.setTranslationY(start - end);
                child.animate()
                        .setStartDelay(SIZE_START_DELAY)
                        .setDuration(SIZE_START_LENGTH)
                        .translationY(0)
                        .start();
            }
        }
        mRootViewHeightController.animateAddHeight(heightDiff);
    }

    public View creatView() {
        mRootView = (ManualLayoutFrame) LayoutInflater.from(mContext)
                .inflate(R.layout.grant_permissions, null);

        mDialogContainer = (ViewGroup) mRootView.findViewById(R.id.dialog_container);
        mMessageView = (TextView) mRootView.findViewById(R.id.permission_message);
        mIconView = (ImageView) mRootView.findViewById(R.id.permission_icon);
        mCurrentGroupView = (TextView) mRootView.findViewById(R.id.current_page_text);
        mDoNotAskCheckbox = (CheckBox) mRootView.findViewById(R.id.do_not_ask_checkbox);
        mAllowButton = (Button) mRootView.findViewById(R.id.permission_allow_button);

        mDescContainer = (ViewGroup) mRootView.findViewById(R.id.desc_container);
        mCurrentDesc = (ViewGroup) mRootView.findViewById(R.id.perm_desc_root);

        mRootView.findViewById(R.id.permission_allow_button).setOnClickListener(this);
        mRootView.findViewById(R.id.permission_deny_button).setOnClickListener(this);
        mRootView.findViewById(R.id.do_not_ask_checkbox).setOnClickListener(this);

        if (mGroupName != null) {
            updateDescription();
            updateGroup();
            updateDoNotAskCheckBox();
        }

        return mRootView;
    }

    private void updateDescription() {
        mIconView.setImageDrawable(mGroupIcon.loadDrawable(mContext));
        mMessageView.setText(mGroupMessage);
    }

    private void updateGroup() {
        if (mGroupCount > 1) {
            mCurrentGroupView.setVisibility(View.VISIBLE);
            mCurrentGroupView.setText(mContext.getString(R.string.current_permission_template,
                    mGroupIndex + 1, mGroupCount));
        } else {
            mCurrentGroupView.setVisibility(View.INVISIBLE);
        }
    }

    private void updateDoNotAskCheckBox() {
        if (mShowDonNotAsk) {
            mDoNotAskCheckbox.setVisibility(View.VISIBLE);
            mDoNotAskCheckbox.setOnClickListener(this);
            mDoNotAskCheckbox.setChecked(mDoNotAskChecked);
        } else {
            mDoNotAskCheckbox.setVisibility(View.GONE);
            mDoNotAskCheckbox.setOnClickListener(null);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.permission_allow_button:
                mResultListener.onRequestGrantPermissionGroupResult(mGroupName, true, false);
                break;
            case R.id.permission_deny_button:
                mResultListener.onRequestGrantPermissionGroupResult(mGroupName, false,
                        mDoNotAskCheckbox.isChecked());
                break;
            case R.id.do_not_ask_checkbox:
                mAllowButton.setEnabled(!mDoNotAskCheckbox.isChecked());
                break;
        }
    }

    /**
     * Manually controls the height of a view through getBottom/setTop.  Also listens
     * for layout changes and sets the height again to be sure it doesn't change.
     */
    public static final class ViewHeightController implements OnLayoutChangeListener {
        private final View mView;
        private int mHeight;

        public ViewHeightController(View view) {
            mView = view;
            mView.addOnLayoutChangeListener(this);
        }

        public void animateAddHeight(int heightDiff) {
            if (heightDiff != 0) {
                final int startHeight = mHeight;
                final int endHeight = startHeight + heightDiff;
                ObjectAnimator animator = ObjectAnimator.ofInt(this, "height",
                        startHeight, endHeight);
                animator.setStartDelay(SIZE_START_DELAY);
                animator.setDuration(SIZE_START_LENGTH);
                animator.start();
            }
        }

        public void setHeight(int height) {
            mHeight = height;
            updateTop();
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            // Ensure that the height never changes.
            updateTop();
        }

        private void updateTop() {
            mView.setTop(mView.getBottom() - mHeight);
        }
    }
}
