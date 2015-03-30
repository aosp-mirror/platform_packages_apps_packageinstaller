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

package com.android.packageinstaller.permission;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.packageinstaller.R;

public final class GrantPermissionFragment extends DialogFragment {
    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON_RES_ID = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_ICON_PKG = "ARG_GROUP_ICON_PKG";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";

    public interface OnRequestGrantPermissionGroupResult {
        public void onRequestGrantPermissionGroupResult(String name, boolean granted);
    }

    public static GrantPermissionFragment newInstance(String groupName, int groupCount,
            int groupIndex, String iconPkg, int iconResId, CharSequence message) {
        GrantPermissionFragment instance = new GrantPermissionFragment();
        instance.setStyle(STYLE_NORMAL,
                android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);

        Bundle arguments = new Bundle();
        arguments.putString(ARG_GROUP_NAME, groupName);
        arguments.putInt(ARG_GROUP_COUNT, groupCount);
        arguments.putInt(ARG_GROUP_INDEX, groupIndex);
        arguments.putInt(ARG_GROUP_ICON_RES_ID, iconResId);
        arguments.putString(ARG_GROUP_ICON_PKG, iconPkg);
        arguments.putCharSequence(ARG_GROUP_MESSAGE, message);
        instance.setArguments(arguments);

        return instance;
    }

    private String mGroupName;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View content = inflater.inflate(R.layout.grant_permissions, container, false);

        mGroupName = getArguments().getString(ARG_GROUP_NAME);
        final CharSequence message = getArguments().getCharSequence(ARG_GROUP_MESSAGE);
        final String iconPkg = getArguments().getString(ARG_GROUP_ICON_PKG);
        final int iconResId = getArguments().getInt(ARG_GROUP_ICON_RES_ID);
        final int groupCount = getArguments().getInt(ARG_GROUP_COUNT);
        final int groupIndex = getArguments().getInt(ARG_GROUP_INDEX);

        final ImageView iconView = (ImageView) content.findViewById(R.id.permission_icon);
        final View allowButton = content.findViewById(R.id.permission_allow_button);
        final View denyButton = content.findViewById(R.id.permission_deny_button);
        final View doNotAskCheckbox = content.findViewById(R.id.do_not_ask_checkbox);
        final TextView currentGroupView = (TextView) content.findViewById(R.id.current_page_text);
        final TextView messageView = (TextView) content.findViewById(R.id.permission_message);

        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view == allowButton) {
                    ((OnRequestGrantPermissionGroupResult) getActivity())
                            .onRequestGrantPermissionGroupResult(mGroupName, true);
                } else if (view == denyButton) {
                    ((OnRequestGrantPermissionGroupResult) getActivity())
                            .onRequestGrantPermissionGroupResult(mGroupName, false);
                } else if (view == doNotAskCheckbox) {
                    //TODO: Implement me.
                }
            }
        };

        Drawable icon = AppPermissions.loadDrawable(getActivity().getPackageManager(), iconPkg,
                iconResId);
        iconView.setImageDrawable(icon);

        messageView.setText(message);

        allowButton.setOnClickListener(clickListener);
        denyButton.setOnClickListener(clickListener);
        doNotAskCheckbox.setOnClickListener(clickListener);

        if (groupCount > 1) {
            currentGroupView.setVisibility(View.VISIBLE);
            currentGroupView.setText(getString(R.string.current_permission_template,
                    groupIndex + 1, groupCount));
        } else {
            currentGroupView.setVisibility(View.INVISIBLE);
        }

        return content;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        ((OnRequestGrantPermissionGroupResult) getActivity())
                .onRequestGrantPermissionGroupResult(mGroupName, false);
    }
}
