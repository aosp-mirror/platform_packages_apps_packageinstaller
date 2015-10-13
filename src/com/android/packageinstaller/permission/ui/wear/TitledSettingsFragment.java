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

package com.android.packageinstaller.permission.ui.wear;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.view.WearableListView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.packageinstaller.permission.ui.wear.settings.ViewUtils;
import com.android.packageinstaller.R;

/**
 * Base settings Fragment that shows a title at the top of the page.
 */
public abstract class TitledSettingsFragment extends Fragment implements
        View.OnLayoutChangeListener, WearableListView.ClickListener {

    private static final int ITEM_CHANGE_DURATION_MS = 120;

    private static final String TAG = "TitledSettingsFragment";
    private int mInitialHeaderHeight;

    protected TextView mHeader;
    protected WearableListView mWheel;

    private int mCharLimitShortTitle;
    private int mCharLimitLine;
    private int mChinOffset;

    private TextWatcher mHeaderTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable editable) {
            adjustHeaderSize();
        }
    };

    private void adjustHeaderTranslation() {
        int translation = 0;
        if (mWheel.getChildCount() > 0) {
            translation = mWheel.getCentralViewTop() - mWheel.getChildAt(0).getTop();
        }

        float newTranslation = Math.min(Math.max(-mInitialHeaderHeight, -translation), 0);

        int position = mWheel.getChildAdapterPosition(mWheel.getChildAt(0));
        if (position == 0 || newTranslation < 0) {
            mHeader.setTranslationY(newTranslation);
        }
    }

    @Override
    public void onTopEmptyRegionClick() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCharLimitShortTitle = getResources().getInteger(R.integer.short_title_length);
        mCharLimitLine = getResources().getInteger(R.integer.char_limit_per_line);
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft,
                               int oldTop, int oldRight, int oldBottom) {
        if (view == mHeader) {
            mInitialHeaderHeight = bottom - top;
            if (ViewUtils.getIsCircular(getContext())) {
                // We are adding more margin on circular screens, so we need to account for it and use
                // it for hiding the header.
                mInitialHeaderHeight +=
                        ((ViewGroup.MarginLayoutParams) view.getLayoutParams()).topMargin;
            }
        } else if (view == mWheel) {
            adjustHeaderTranslation();
        }
    }

    protected void initializeLayout(RecyclerView.Adapter adapter) {
        View v = getView();
        mWheel = (WearableListView) v.findViewById(R.id.wheel);

        mHeader = (TextView) v.findViewById(R.id.header);
        mHeader.addOnLayoutChangeListener(this);
        mHeader.addTextChangedListener(mHeaderTextWatcher);

        mWheel.setAdapter(adapter);
        mWheel.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                adjustHeaderTranslation();
            }
        });
        mWheel.setClickListener(this);
        mWheel.addOnLayoutChangeListener(this);

        // Decrease item change animation duration to approximately half of the default duration.
        RecyclerView.ItemAnimator itemAnimator = mWheel.getItemAnimator();
        itemAnimator.setChangeDuration(ITEM_CHANGE_DURATION_MS);

        adjustHeaderSize();

        positionOnCircular(getContext(), mHeader, mWheel);
    }

    public void positionOnCircular(Context context, View header, final ViewGroup wheel) {
        if (ViewUtils.getIsCircular(context)) {
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) header.getLayoutParams();
            params.topMargin = (int) context.getResources().getDimension(
                    R.dimen.settings_header_top_margin_circular);
            // Note that the margins are made symmetrical here. Since they're symmetrical we choose
            // the smaller value to maximize usable width.
            final int margin = (int) Math.min(context.getResources().getDimension(
                    R.dimen.round_content_padding_left), context.getResources().getDimension(
                    R.dimen.round_content_padding_right));
            params.leftMargin = margin;
            params.rightMargin = margin;
            params.gravity = Gravity.CENTER_HORIZONTAL;
            header.setLayoutParams(params);

            if (header instanceof TextView) {
                ((TextView) header).setGravity(Gravity.CENTER);
            }

            final int leftPadding = (int) context.getResources().getDimension(
                    R.dimen.round_content_padding_left);
            final int rightPadding = (int) context.getResources().getDimension(
                    R.dimen.round_content_padding_right);
            final int topPadding = (int) context.getResources().getDimension(
                    R.dimen.settings_wearable_list_view_vertical_padding_round);
            final int bottomPadding = (int) context.getResources().getDimension(
                    R.dimen.settings_wearable_list_view_vertical_padding_round);
            wheel.setPadding(leftPadding, topPadding, rightPadding, mChinOffset + bottomPadding);
            wheel.setClipToPadding(false);

            wheel.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    mChinOffset = insets.getSystemWindowInsetBottom();
                    wheel.setPadding(leftPadding, topPadding, rightPadding,
                            mChinOffset + bottomPadding);
                    // This listener is invoked after each time we navigate to SettingsActivity and
                    // it keeps adding padding. We need to disable it after the first update.
                    v.setOnApplyWindowInsetsListener(null);
                    return insets.consumeSystemWindowInsets();
                }
            });
        } else {
            int leftPadding = (int) context.getResources().getDimension(
                    R.dimen.content_padding_left);
            wheel.setPadding(leftPadding, wheel.getPaddingTop(), wheel.getPaddingRight(),
                    wheel.getPaddingBottom());
        }
    }

    private void adjustHeaderSize() {
        int length = mHeader.length();

        if (length <= mCharLimitShortTitle) {
            mHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(
                            R.dimen.setting_short_header_text_size));
        } else {
            mHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(
                            R.dimen.setting_long_header_text_size));
        }

        boolean singleLine = length <= mCharLimitLine;

        float height = getResources().getDimension(R.dimen.settings_header_base_height);
        if (!singleLine) {
            height += getResources().getDimension(R.dimen.setting_header_extra_line_height);
        }
        mHeader.setMinHeight((int) height);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mHeader.getLayoutParams();
        final Context context = getContext();
        if (!singleLine) {
            // Make the top margin a little bit smaller so there is more space for the title.
            if (ViewUtils.getIsCircular(context)) {
                params.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.settings_header_top_margin_circular_multiline);
            } else {
                params.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.settings_header_top_margin_multiline);
            }
        } else {
            if (ViewUtils.getIsCircular(context)) {
                params.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.settings_header_top_margin_circular);
            } else {
                params.topMargin = getResources().getDimensionPixelSize(
                        R.dimen.settings_header_top_margin);
            }
        }
        mHeader.setLayoutParams(params);
    }
}
