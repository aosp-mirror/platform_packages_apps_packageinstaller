/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller.role.ui.handheld;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.utils.UiUtils;
import com.android.permissioncontroller.R;
import com.android.settingslib.HelpUtils;

/**
 * Base class for settings fragments.
 */
abstract class SettingsFragment extends PreferenceFragmentCompat {

    private FrameLayout mContentLayout;
    private LinearLayout mPreferenceLayout;
    private View mLoadingView;
    private TextView mEmptyText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mContentLayout = (FrameLayout) inflater.inflate(R.layout.settings, container, false);
        mPreferenceLayout = (LinearLayout) super.onCreateView(inflater, container,
                savedInstanceState);
        mContentLayout.addView(mPreferenceLayout);
        return mContentLayout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLoadingView = mContentLayout.findViewById(R.id.loading);
        mEmptyText = mContentLayout.findViewById(R.id.empty);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // We'll manually add preferences later.
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        requireActivity().getActionBar().setDisplayHomeAsUpEnabled(true);

        mEmptyText.setText(getEmptyTextResource());

        updateState();
    }

    @StringRes
    protected abstract int getEmptyTextResource();

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        Utils.prepareSearchMenuItem(menu, requireContext());
        int helpUriResource = getHelpUriResource();
        if (helpUriResource != 0) {
            HelpUtils.prepareHelpMenuItem(requireActivity(), menu, helpUriResource,
                    getClass().getName());
        }
    }

    @StringRes
    protected int getHelpUriResource() {
        return 0;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                requireActivity().finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void updateState() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        boolean isLoading = preferenceScreen == null;
        UiUtils.setViewShown(mLoadingView, isLoading);
        boolean isEmpty = preferenceScreen != null && preferenceScreen.getPreferenceCount() == 0;
        UiUtils.setViewShown(mEmptyText, isEmpty);
    }
}
