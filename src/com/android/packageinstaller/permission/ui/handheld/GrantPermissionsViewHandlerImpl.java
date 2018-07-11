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

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Space;
import android.widget.TextView;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.ui.ButtonBarLayout;
import com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler;
import com.android.packageinstaller.permission.ui.ManagePermissionsActivity;
import com.android.packageinstaller.permission.ui.ManualLayoutFrame;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public class GrantPermissionsViewHandlerImpl implements GrantPermissionsViewHandler,
        OnClickListener, RadioGroup.OnCheckedChangeListener {

    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    private static final String ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE";

    public static final String ARG_GROUP_SHOW_DO_NOT_ASK = "ARG_GROUP_SHOW_DO_NOT_ASK";
    private static final String ARG_GROUP_SHOW_FOREGOUND_CHOOSER =
            "ARG_GROUP_SHOW_FOREGOUND_CHOOSER";

    public static final String ARG_GROUP_DO_NOT_ASK_CHECKED = "ARG_GROUP_DO_NOT_ASK_CHECKED";
    private static final String ARG_GROUP_ALWAYS_OPTION_CHECKED = "ARG_GROUP_ALWAYS_OPTION_CHECKED";

    // Animation parameters.
    private static final long OUT_DURATION = 200;
    private static final long IN_DURATION = 300;

    private final Activity mActivity;
    private final String mAppPackageName;
    private final boolean mPermissionReviewRequired;
    private final long mOutDuration;
    private final long mInDuration;

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
    private TextView mCurrentGroupView;
    private TextView mMessageView;
    private TextView mDetailMessageView;
    private CheckBox mDoNotAskCheckbox;
    private RadioGroup mForegroundChooser;
    private RadioButton mForegroundOnlyOption;
    private RadioButton mAlwaysOption;
    private RadioButton mDenyAndDontAskAgainOption;
    private Button mAllowButton;
    private Button mMoreInfoButton;
    private ManualLayoutFrame mRootView;
    private ViewGroup mDescContainer;
    private Space mSpacer;

    public GrantPermissionsViewHandlerImpl(Activity activity, String appPackageName) {
        float animationScale = 1f;
        try {
            Settings.Global.getFloat(activity.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE);
        } catch (Settings.SettingNotFoundException ignored) {
        }
        mOutDuration = (long) (OUT_DURATION * animationScale);
        mInDuration = (long) (IN_DURATION * animationScale);

        mActivity = activity;
        mAppPackageName = appPackageName;
        mPermissionReviewRequired = activity.getPackageManager().isPermissionReviewModeEnabled();
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

        arguments.putBoolean(ARG_GROUP_DO_NOT_ASK_CHECKED, isDoNotAskAgainChecked());
        arguments.putBoolean(ARG_GROUP_ALWAYS_OPTION_CHECKED, mAlwaysOption.isSelected());
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

        updateAll(savedInstanceState.getBoolean(ARG_GROUP_DO_NOT_ASK_CHECKED),
                savedInstanceState.getBoolean(ARG_GROUP_ALWAYS_OPTION_CHECKED));
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, CharSequence detailMessage, boolean showForegroundChooser,
            boolean showDonNotAsk) {
        boolean isNewGroup = mGroupIndex != groupIndex;
        boolean isDoNotAskAgainChecked = mDoNotAskCheckbox.isChecked();
        boolean isAlwaysOptionChecked = mAlwaysOption.isChecked();
        if (isNewGroup) {
            isDoNotAskAgainChecked = false;
            isAlwaysOptionChecked = false;
        }

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
            if (isNewGroup) {
                animateToPermission(isDoNotAskAgainChecked, isAlwaysOptionChecked);
            } else {
                updateAll(isDoNotAskAgainChecked, isAlwaysOptionChecked);
            }
        }
    }

    private void fadeOutView(View v, Runnable onAnimationFinished, Interpolator interpolator) {
        v.animate().alpha(0).setDuration(mOutDuration).setInterpolator(interpolator)
                .withEndAction(onAnimationFinished);
    }

    private void animateOldContent(IntConsumer callback) {
        // Fade out old description group and scale out the icon for it.
        Interpolator interpolator = AnimationUtils.loadInterpolator(mActivity,
                android.R.interpolator.fast_out_linear_in);

        AtomicInteger numAnimationsActive = new AtomicInteger(2);
        Runnable onAnimationFinished = () -> callback.accept(numAnimationsActive.decrementAndGet());

        // Icon scale to zero
        mIconView.animate()
                .scaleX(0)
                .scaleY(0)
                .setDuration(mOutDuration)
                .setInterpolator(interpolator)
                .withEndAction(onAnimationFinished);

        fadeOutView(mDescContainer, onAnimationFinished, interpolator);

        if (mShowForegroundChooser || !mShowDonNotAsk) {
            numAnimationsActive.incrementAndGet();
            fadeOutView(mDoNotAskCheckbox, onAnimationFinished, interpolator);
        }
        if (!TextUtils.equals(mDetailMessage, mDetailMessageView.getText())) {
            numAnimationsActive.incrementAndGet();
            fadeOutView(mDetailMessageView, onAnimationFinished, interpolator);
        }
        if (!mShowForegroundChooser || !mShowDonNotAsk) {
            numAnimationsActive.incrementAndGet();
            fadeOutView(mDenyAndDontAskAgainOption, onAnimationFinished, interpolator);
        }
        if (!mShowForegroundChooser) {
            numAnimationsActive.addAndGet(2);
            fadeOutView(mAlwaysOption, onAnimationFinished, interpolator);
            fadeOutView(mForegroundOnlyOption, onAnimationFinished, interpolator);
        }
    }

    private void updateAll(boolean isDoNotAskAgainChecked, boolean isAlwaysOptionChecked) {
        updateDescription();
        updateDetailDescription();
        updateGroup();
        updateDoNotAskCheckBoxAndForegroundOption(isDoNotAskAgainChecked, isAlwaysOptionChecked);
    }

    private void fadeInView(View v, Interpolator interpolator) {
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() < 1.0f) {
            v.animate().alpha(1.0f).setDuration(mInDuration).setInterpolator(interpolator);
        }
    }

    private void animateNewContent() {
        // Unhide description and slide it in
        mDescContainer.setTranslationX(mDescContainer.getWidth());
        mIconView.setScaleX(1);
        mIconView.setScaleY(1);
        mDescContainer.setAlpha(1);
        mDescContainer.animate()
                .translationX(0)
                .setDuration(mInDuration)
                .setInterpolator(AnimationUtils.loadInterpolator(mActivity,
                        android.R.interpolator.linear_out_slow_in));

        // Use heavily accelerating animator so that at the beginning of the animation nothing
        // is visible for a long time. This is needed as at this time the dialog is still growing
        // and we don't want elements to overlap.
        Interpolator interpolator = AnimationUtils.loadInterpolator(mActivity,
                android.R.interpolator.accelerate_cubic);

        if (mShowDonNotAsk) {
            fadeInView(mDoNotAskCheckbox, interpolator);
        }
        if (mShowForegroundChooser) {
            if (mShowDonNotAsk) {
                fadeInView(mDenyAndDontAskAgainOption, interpolator);
            }
            fadeInView(mAlwaysOption, interpolator);
            fadeInView(mForegroundOnlyOption, interpolator);
        }
        if (mDetailMessage != null) {
            fadeInView(mDetailMessageView, interpolator);
        }
    }

    private void animateToPermission(boolean isDoNotAskAgainChecked,
            boolean isAlwaysOptionChecked) {
        // Animate out the old content
        animateOldContent(numAnimationsActive -> {
            // Wait until all animations are done
            if (numAnimationsActive == 0) {
                // Add the new content
                updateAll(isDoNotAskAgainChecked, isAlwaysOptionChecked);

                // Animate in new content
                animateNewContent();
            }
        });
    }

    @Override
    public View createView() {
        mRootView = (ManualLayoutFrame) LayoutInflater.from(mActivity)
                .inflate(R.layout.grant_permissions, null);
        mMessageView = (TextView) mRootView.findViewById(R.id.permission_message);
        mDetailMessageView = mRootView.requireViewById(R.id.detail_message);
        mIconView = (ImageView) mRootView.findViewById(R.id.permission_icon);
        mCurrentGroupView = (TextView) mRootView.findViewById(R.id.current_page_text);
        mDoNotAskCheckbox = (CheckBox) mRootView.findViewById(R.id.do_not_ask_checkbox);
        mSpacer = mRootView.requireViewById(R.id.detail_message_do_not_ask_checkbox_space);
        mAllowButton = (Button) mRootView.findViewById(R.id.permission_allow_button);
        mAllowButton.setOnClickListener(this);

        if (mPermissionReviewRequired) {
            mMoreInfoButton = (Button) mRootView.findViewById(R.id.permission_more_info_button);
            mMoreInfoButton.setVisibility(View.VISIBLE);
            mMoreInfoButton.setOnClickListener(this);
        }

        mForegroundChooser = mRootView.requireViewById(R.id.foreground_or_always_radiogroup);
        mForegroundOnlyOption = mRootView.requireViewById(R.id.foreground_only_radio_button);
        mAlwaysOption = mRootView.requireViewById(R.id.always_radio_button);
        mDenyAndDontAskAgainOption = mRootView.requireViewById(
                R.id.deny_dont_ask_again_radio_button);
        mDescContainer = (ViewGroup) mRootView.findViewById(R.id.desc_container);

        mRootView.findViewById(R.id.permission_deny_button).setOnClickListener(this);
        mDoNotAskCheckbox.setOnClickListener(this);
        mDenyAndDontAskAgainOption.setOnClickListener(this);
        mForegroundChooser.setOnCheckedChangeListener(this);

        ((ButtonBarLayout) mRootView.requireViewById(R.id.button_group)).setAllowStacking(true);

        // The appearing + disappearing animations are controlled manually, hence disable the
        // automatic animations
        ViewGroup contentContainer = mRootView.requireViewById(R.id.content_container);
        contentContainer.getLayoutTransition().disableTransitionType(LayoutTransition.APPEARING);
        contentContainer.getLayoutTransition().disableTransitionType(LayoutTransition.DISAPPEARING);
        contentContainer.getLayoutTransition().setDuration(mInDuration);

        if (mGroupName != null) {
            updateAll(false, false);
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
            mSpacer.setVisibility(View.GONE);
        } else {
            if (mShowDonNotAsk) {
                mSpacer.setVisibility(View.VISIBLE);
            } else {
                mSpacer.setVisibility(View.GONE);
            }

            mDetailMessageView.setText(mDetailMessage);
            mDetailMessageView.setVisibility(View.VISIBLE);
        }
    }

    private void updateGroup() {
        if (mGroupCount > 1) {
            mCurrentGroupView.setVisibility(View.VISIBLE);
            mCurrentGroupView.setText(mActivity.getString(R.string.current_permission_template,
                    mGroupIndex + 1, mGroupCount));
        } else {
            mCurrentGroupView.setVisibility(View.GONE);
        }
    }

    private void updateDoNotAskCheckBoxAndForegroundOption(boolean isDoNotAskAgainChecked,
            boolean isAlwaysSelected) {
        if (mShowForegroundChooser) {
            mForegroundChooser.setVisibility(View.VISIBLE);
            mDoNotAskCheckbox.setVisibility(View.GONE);

            if (isAlwaysSelected) {
                mAlwaysOption.setSelected(true);
            }

            if (mShowDonNotAsk) {
                mDenyAndDontAskAgainOption.setSelected(isDoNotAskAgainChecked);
                mDenyAndDontAskAgainOption.setVisibility(View.VISIBLE);
            } else {
                mDenyAndDontAskAgainOption.setVisibility(View.GONE);
            }
        } else {
            mForegroundChooser.setVisibility(View.GONE);
            if (mShowDonNotAsk) {
                mDoNotAskCheckbox.setVisibility(View.VISIBLE);
                mDoNotAskCheckbox.setChecked(isDoNotAskAgainChecked);
            } else {
                mDoNotAskCheckbox.setVisibility(View.GONE);
            }
        }

        mAllowButton.setEnabled(!isDoNotAskAgainChecked() && isOptionChosenIfNeeded());
    }

    private boolean isDoNotAskAgainChecked() {
        return (mDoNotAskCheckbox.getVisibility() == View.VISIBLE
                && mDoNotAskCheckbox.isChecked())
                || (mDenyAndDontAskAgainOption.getVisibility() == View.VISIBLE
                && mDenyAndDontAskAgainOption.isChecked());
    }

    private boolean isOptionChosenIfNeeded() {
        return !mShowForegroundChooser
                || (mForegroundOnlyOption.isChecked()
                || (mDenyAndDontAskAgainOption.getVisibility() == View.VISIBLE
                && mDenyAndDontAskAgainOption.isChecked())
                || (mAlwaysOption.getVisibility() == View.VISIBLE && mAlwaysOption.isChecked()));
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.permission_allow_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    if (mShowForegroundChooser && mForegroundOnlyOption.isChecked()) {
                        mResultListener.onPermissionGrantResult(mGroupName,
                                GRANTED_FOREGROUND_ONLY);
                    } else {
                        mResultListener.onPermissionGrantResult(mGroupName, GRANTED_ALWAYS);
                    }
                }
                break;
            case R.id.permission_deny_button:
                if (mResultListener != null) {
                    view.performAccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
                    if (isDoNotAskAgainChecked()) {
                        mResultListener.onPermissionGrantResult(mGroupName,
                                DENIED_DO_NOT_ASK_AGAIN);
                    } else {
                        mResultListener.onPermissionGrantResult(mGroupName, DENIED);
                    }
                }
                break;
            case R.id.permission_more_info_button:
                Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mAppPackageName);
                intent.putExtra(ManagePermissionsActivity.EXTRA_ALL_PERMISSIONS, true);
                mActivity.startActivity(intent);
                break;
        }

        mAllowButton.setEnabled(!isDoNotAskAgainChecked() && isOptionChosenIfNeeded());

    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, DENIED);
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        mAllowButton.setEnabled(!isDoNotAskAgainChecked() && isOptionChosenIfNeeded());
    }
}
