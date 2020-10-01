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

package com.android.permissioncontroller.permission.ui.handheld;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.MICROPHONE;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.permission.debug.UtilsKt.getUsageDurationString;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;
import static com.android.permissioncontroller.permission.ui.handheld.AppPermissionFragment.GRANT_CATEGORY;
import static com.android.permissioncontroller.permission.utils.KotlinUtilsKt.navigateSafe;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.Navigation;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.LocationProviderInterceptDialog;
import com.android.permissioncontroller.permission.utils.LocationUtils;

import java.util.List;

/**
 * A preference that links to the screen where a permission can be toggled.
 */
public class PermissionControlPreference extends Preference {
    private final @NonNull Context mContext;
    private @Nullable Drawable mWidgetIcon;
    private @Nullable String mGranted;
    private boolean mUseSmallerIcon;
    private boolean mEllipsizeEnd;
    private @Nullable List<Integer> mTitleIcons;
    private @Nullable List<Integer> mSummaryIcons;
    private @NonNull String mPackageName;
    private @NonNull String mPermGroupName;
    private @NonNull String mCaller;
    private @NonNull long mSessionId;
    private boolean mHasNavGraph;
    private @NonNull UserHandle mUser;

    public PermissionControlPreference(@NonNull Context context,
            @NonNull AppPermissionGroup group, @NonNull String caller) {
        this(context, group, caller, 0);
    }

    public PermissionControlPreference(@NonNull Context context,
            @NonNull AppPermissionGroup group, @NonNull String caller, long sessionId) {
        this(context, group.getApp().packageName, group.getName(), group.getUser(), caller,
                sessionId, null, false);
    }

    public PermissionControlPreference(@NonNull Context context,
            @NonNull String packageName, @NonNull String permGroupName, @NonNull UserHandle user,
            @NonNull String caller, long sessionId, String granted, boolean hasNavGraph) {
        super(context);
        mContext = context;
        mWidgetIcon = null;
        mUseSmallerIcon = false;
        mEllipsizeEnd = false;
        mTitleIcons = null;
        mSummaryIcons = null;
        mPackageName = packageName;
        mCaller = caller;
        mPermGroupName = permGroupName;
        mSessionId = sessionId;
        mUser = user;
        mGranted = granted;
        mHasNavGraph = hasNavGraph;
    }

    /**
     * Sets this preference's right icon.
     *
     * Note that this must be called before preference layout to take effect.
     *
     * @param widgetIcon the icon to use.
     */
    public void setRightIcon(@NonNull Drawable widgetIcon) {
        mWidgetIcon = widgetIcon;
        setWidgetLayoutResource(R.layout.image_view);
    }

    /**
     * Sets this preference's left icon to be smaller than normal.
     *
     * Note that this must be called before preference layout to take effect.
     */
    public void useSmallerIcon() {
        mUseSmallerIcon = true;
    }

    /**
     * Sets this preference's title to use an ellipsis at the end.
     *
     * Note that this must be called before preference layout to take effect.
     */
    public void setEllipsizeEnd() {
        mEllipsizeEnd = true;
    }

    /**
     * Sets this preference's summary based on the group it represents, if applicable.
     *
     * @param group the permission group this preference represents.
     */
    public void setGroupSummary(@NonNull AppPermissionGroup group) {
        if (group.hasPermissionWithBackgroundMode() && group.areRuntimePermissionsGranted()) {
            AppPermissionGroup backgroundGroup = group.getBackgroundPermissions();
            if (backgroundGroup == null || !backgroundGroup.areRuntimePermissionsGranted()) {
                setSummary(R.string.permission_subtitle_only_in_foreground);
                return;
            }
        }
        setSummary("");
    }

    /**
     * Sets this preference's summary based on its permission usage.
     *
     * @param groupUsage the usage information
     * @param accessTimeStr the string representing the last access time
     */
    public void setUsageSummary(@NonNull GroupUsage groupUsage, @NonNull String accessTimeStr) {
        long backgroundAccessCount = groupUsage.getBackgroundAccessCount();
        long duration = 0;
        String groupName = groupUsage.getGroup().getName();
        if (groupName.equals(CAMERA) || groupName.equals(MICROPHONE)) {
            duration = groupUsage.getAccessDuration();
        }
        if (backgroundAccessCount == 0) {
            long numForegroundAccesses = groupUsage.getForegroundAccessCount();
            if (duration == 0) {
                setSummary(mContext.getResources().getQuantityString(
                        R.plurals.permission_usage_summary, (int) numForegroundAccesses,
                        accessTimeStr, numForegroundAccesses));
            } else {
                setSummary(mContext.getResources().getQuantityString(
                        R.plurals.permission_usage_summary_duration, (int) numForegroundAccesses,
                        accessTimeStr, numForegroundAccesses,
                        getUsageDurationString(mContext, groupUsage)));
            }
        } else {
            long numAccesses = groupUsage.getAccessCount();
            if (duration == 0) {
                setSummary(mContext.getResources().getQuantityString(
                        R.plurals.permission_usage_summary_background, (int) numAccesses,
                        accessTimeStr, numAccesses, backgroundAccessCount));
            } else {
                setSummary(mContext.getResources().getQuantityString(
                        R.plurals.permission_usage_summary_background_duration, (int) numAccesses,
                        accessTimeStr, numAccesses, backgroundAccessCount,
                        getUsageDurationString(mContext, groupUsage)));
            }
        }
    }

    /**
     * Sets this preference to show the given icons to the left of its title.
     *
     * @param titleIcons the icons to show.
     */
    public void setTitleIcons(@NonNull List<Integer> titleIcons) {
        mTitleIcons = titleIcons;
        setLayoutResource(R.layout.preference_usage);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (mUseSmallerIcon) {
            ImageView icon = ((ImageView) holder.findViewById(android.R.id.icon));
            icon.setMaxWidth(
                    mContext.getResources().getDimensionPixelSize(R.dimen.secondary_app_icon_size));
            icon.setMaxHeight(
                    mContext.getResources().getDimensionPixelSize(R.dimen.secondary_app_icon_size));
        }

        super.onBindViewHolder(holder);

        if (mWidgetIcon != null) {
            View widgetFrame = holder.findViewById(android.R.id.widget_frame);
            ((ImageView) widgetFrame.findViewById(R.id.icon)).setImageDrawable(mWidgetIcon);
        }

        if (mEllipsizeEnd) {
            TextView title = (TextView) holder.findViewById(android.R.id.title);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
        }

        setIcons(holder, mSummaryIcons, R.id.summary_widget_frame);
        setIcons(holder, mTitleIcons, R.id.title_widget_frame);

        setOnPreferenceClickListener(pref -> {
            if (LocationUtils.isLocationGroupAndProvider(
                    mContext, mPermGroupName, mPackageName)) {
                Intent intent = new Intent(mContext, LocationProviderInterceptDialog.class);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName);
                mContext.startActivityAsUser(intent, mUser);
            } else if (LocationUtils.isLocationGroupAndControllerExtraPackage(
                    mContext, mPermGroupName, mPackageName)) {
                // Redirect to location controller extra package settings.
                LocationUtils.startLocationControllerExtraPackageSettings(mContext, mUser);
            } else if (mHasNavGraph) {
                Bundle args = new Bundle();
                args.putString(Intent.EXTRA_PACKAGE_NAME, mPackageName);
                args.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, mPermGroupName);
                args.putParcelable(Intent.EXTRA_USER, mUser);
                args.putString(EXTRA_CALLER_NAME, mCaller);
                args.putLong(EXTRA_SESSION_ID, mSessionId);
                args.putString(GRANT_CATEGORY, mGranted);
                navigateSafe(Navigation.findNavController(holder.itemView), R.id.perm_groups_to_app,
                        args);
            } else {
                // TODO ntmyren, yianyliu: Remove once Auto has been adapted to new permission model
                // see b/150229448
                Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName);
                intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mPermGroupName);
                intent.putExtra(Intent.EXTRA_USER, mUser);
                intent.putExtra(EXTRA_CALLER_NAME, mCaller);
                intent.putExtra(EXTRA_SESSION_ID, mSessionId);
                mContext.startActivity(intent);
            }
            return true;
        });
    }

    private void setIcons(PreferenceViewHolder holder, @Nullable List<Integer> icons, int frameId) {
        ViewGroup frame = (ViewGroup) holder.findViewById(frameId);
        if (icons != null && !icons.isEmpty()) {
            frame.setVisibility(View.VISIBLE);
            frame.removeAllViews();
            LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
            int numIcons = icons.size();
            for (int i = 0; i < numIcons; i++) {
                ViewGroup group = (ViewGroup) inflater.inflate(R.layout.title_summary_image_view,
                        null);
                ImageView imageView = group.requireViewById(R.id.icon);
                imageView.setImageResource(icons.get(i));
                frame.addView(group);
            }
        } else if (frame != null) {
            frame.setVisibility(View.GONE);
        }
    }
}
