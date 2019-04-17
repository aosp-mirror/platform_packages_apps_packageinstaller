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

package com.android.packageinstaller.role.ui.auto;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;

/** Base fragment to be used by car variants of the default app settings screens. */
public class DefaultAppFrameFragment extends PreferenceFragmentCompat {

    private TextView mLabelView;
    private CharSequence mLabel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        View backButton = rootView.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> getActivity().onBackPressed());

        mLabelView = rootView.findViewById(R.id.label);
        updateHeaderLabel();

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

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // We'll manually add preferences later.
    }

    private void updateHeaderLabel() {
        if (mLabelView != null) {
            mLabelView.setText(mLabel);
        }
    }
}
