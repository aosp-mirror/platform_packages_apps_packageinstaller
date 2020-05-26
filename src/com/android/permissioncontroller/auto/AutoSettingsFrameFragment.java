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

package com.android.permissioncontroller.auto;

import static com.android.car.ui.core.CarUi.getToolbar;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.ui.preference.PreferenceFragment;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;

import java.util.Collections;

/** Common settings frame for car related settings in permission controller. */
public abstract class AutoSettingsFrameFragment extends PreferenceFragment {

    private ToolbarController mToolbar;

    private CharSequence mLabel;
    private boolean mIsLoading;
    private CharSequence mActionLabel;
    private View.OnClickListener mActionOnClickListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);

        mToolbar = getToolbar(requireActivity());

        updateHeaderLabel();
        updateLoading();
        updateAction();

        return rootView;
    }

    /** Sets the header text of this fragment. */
    public void setHeaderLabel(CharSequence label) {
        mLabel = label;
        if (getPreferenceScreen() != null) {
            // Needed because CarUi's preference fragment reads this title
            getPreferenceScreen().setTitle(mLabel);
        }
        updateHeaderLabel();
    }

    /** Gets the header text of this fragment. */
    public CharSequence getHeaderLabel() {
        return mLabel;
    }

    private void updateHeaderLabel() {
        if (mToolbar != null) {
            mToolbar.setTitle(mLabel);
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
        if (mToolbar != null) {
            mToolbar.getProgressBar().setVisible(mIsLoading);
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
        if (mToolbar == null) {
            return;
        }
        if (!TextUtils.isEmpty(mActionLabel) && mActionOnClickListener != null) {
            mToolbar.setMenuItems(Collections.singletonList(MenuItem.builder(getContext())
                    .setTitle(mActionLabel)
                    .setOnClickListener(i -> mActionOnClickListener.onClick(null))
                    .build()));
        } else {
            mToolbar.setMenuItems(null);
        }
    }
}
