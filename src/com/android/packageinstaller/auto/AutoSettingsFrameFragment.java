/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.auto;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;

/** Common settings frame for car related settings in permission controller. */
public abstract class AutoSettingsFrameFragment extends PreferenceFragmentCompat {

    private TextView mLabelView;
    private ProgressBar mProgressBar;
    private Button mAction;

    private CharSequence mLabel;
    private boolean mIsLoading;
    private CharSequence mActionLabel;
    private View.OnClickListener mActionOnClickListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);

        View backButton = rootView.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> getActivity().onBackPressed());

        mLabelView = rootView.findViewById(R.id.label);
        updateHeaderLabel();

        mProgressBar = rootView.findViewById(R.id.progress_bar);
        updateLoading();

        mAction = rootView.findViewById(R.id.action);
        updateAction();

        return rootView;
    }

    /** Sets the header text of this fragment. */
    public void setHeaderLabel(CharSequence label) {
        mLabel = label;
        updateHeaderLabel();
    }

    /** Gets the header text of this fragment. */
    public CharSequence getHeaderLabel() {
        return mLabel;
    }

    private void updateHeaderLabel() {
        if (mLabelView != null) {
            mLabelView.setText(mLabel);
        }
    }

    /**
     * Shows a progress view while content is loading.
     *
     * @param isLoading {@code true} if the progress view should be visible.
     */
    public void setLoading(boolean isLoading) {
        mIsLoading = isLoading;
        updateLoading();
    }

    private void updateLoading() {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(mIsLoading ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Shows a button with the given {@code label} that when clicked will call the given {@code
     * onClickListener}.
     */
    public void setAction(CharSequence label, View.OnClickListener onClickListener) {
        mActionLabel = label;
        mActionOnClickListener = onClickListener;
        updateAction();
    }

    private void updateAction() {
        if (mAction == null) {
            return;
        }
        if (!TextUtils.isEmpty(mActionLabel) && mActionOnClickListener != null) {
            mAction.setText(mActionLabel);
            mAction.setOnClickListener(mActionOnClickListener);
            mAction.setVisibility(View.VISIBLE);
        } else {
            mAction.setVisibility(View.GONE);
        }
    }
}
