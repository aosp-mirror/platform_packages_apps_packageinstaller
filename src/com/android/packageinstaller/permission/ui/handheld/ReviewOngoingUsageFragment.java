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

package com.android.packageinstaller.permission.ui.handheld;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;

import static com.android.packageinstaller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_DISMISS;
import static com.android.packageinstaller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_LINE_ITEM;
import static com.android.packageinstaller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_PRIVACY_SETTINGS;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.model.AppPermissionUsage;
import com.android.packageinstaller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.packageinstaller.permission.model.PermissionApps;
import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A dialog listing the currently uses of camera, microphone, and location.
 */
public class ReviewOngoingUsageFragment extends PreferenceFragmentCompat {

    private @NonNull PermissionUsages mPermissionUsages;
    private @Nullable AlertDialog mDialog;
    private long mStartTime;

    /**
     * @return A new {@link ReviewOngoingUsageFragment}
     */
    public static ReviewOngoingUsageFragment newInstance(long numMillis) {
        ReviewOngoingUsageFragment fragment = new ReviewOngoingUsageFragment();
        Bundle arguments = new Bundle();
        arguments.putLong(Intent.EXTRA_DURATION_MILLIS, numMillis);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Utils.isPermissionsHubEnabled()) {
            getActivity().finish();
            return;
        }

        long numMillis = getArguments().getLong(Intent.EXTRA_DURATION_MILLIS);

        mPermissionUsages = new PermissionUsages(getActivity());
        mStartTime = Math.max(System.currentTimeMillis() - numMillis, Instant.EPOCH.toEpochMilli());
        mPermissionUsages.load(null, new String[]{CAMERA, LOCATION, MICROPHONE}, mStartTime,
                Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST, getActivity().getLoaderManager(),
                false, false, this::onPermissionUsagesLoaded, false);
    }

    private void onPermissionUsagesLoaded() {
        if (getActivity() == null) {
            return;
        }

        List<AppPermissionUsage> appPermissionUsages = mPermissionUsages.getUsages();

        List<Pair<AppPermissionUsage, List<GroupUsage>>> usages = new ArrayList<>();
        ArrayList<PermissionApp> permApps = new ArrayList<>();
        int numApps = appPermissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appUsage = appPermissionUsages.get(appNum);

            List<GroupUsage> usedGroups = new ArrayList<>();
            List<GroupUsage> appGroups = appUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                GroupUsage groupUsage = appGroups.get(groupNum);
                String groupName = groupUsage.getGroup().getName();

                if (groupUsage.getLastAccessTime() < mStartTime && !groupUsage.isRunning()) {
                    continue;
                }
                if (!Utils.isGroupOrBgGroupUserSensitive(groupUsage.getGroup())) {
                    continue;
                }

                usedGroups.add(appGroups.get(groupNum));
            }

            if (!usedGroups.isEmpty()) {
                usages.add(Pair.create(appUsage, usedGroups));
                permApps.add(appUsage.getApp());
            }
        }

        if (usages.isEmpty()) {
            getActivity().finish();
            return;
        }

        new PermissionApps.AppDataLoader(getActivity(), () -> showDialog(usages))
                .execute(permApps.toArray(new PermissionApps.PermissionApp[permApps.size()]));
    }

    private void showDialog(@NonNull List<Pair<AppPermissionUsage, List<GroupUsage>>> usages) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setView(createDialogView(usages))
                .setPositiveButton(R.string.ongoing_usage_dialog_ok, (dialog, which) ->
                        PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                                PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_DISMISS, null))
                .setOnDismissListener((dialog) -> getActivity().finish());
        setNeutralButton(builder);
        mDialog = builder.create();
        mDialog.show();
    }

    protected void setNeutralButton(AlertDialog.Builder builder) {
        builder.setNeutralButton(R.string.ongoing_usage_dialog_open_settings, (dialog, which) -> {
            PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                    PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_PRIVACY_SETTINGS, null);
            startActivity(new Intent(Settings.ACTION_PRIVACY_SETTINGS).putExtra(
                    Intent.EXTRA_DURATION_MILLIS, TimeUnit.MINUTES.toMillis(1)));
        });
    }

    private @NonNull View createDialogView(
            @NonNull List<Pair<AppPermissionUsage, List<GroupUsage>>> usages) {
        Context context = getActivity();
        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.ongoing_usage_dialog_content, null);
        ViewGroup appsList = contentView.requireViewById(R.id.items_container);

        // Compute all of the permission group labels that were used.
        ArraySet<String> usedGroups = new ArraySet<>();
        int numUsages = usages.size();
        for (int usageNum = 0; usageNum < numUsages; usageNum++) {
            List<GroupUsage> groups = usages.get(usageNum).second;
            int numGroups = groups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                usedGroups.add(groups.get(groupNum).getGroup().getLabel().toString().toLowerCase());
            }
        }

        // Add the layout for each app.
        for (int usageNum = 0; usageNum < numUsages; usageNum++) {
            Pair<AppPermissionUsage, List<GroupUsage>> usage = usages.get(usageNum);
            PermissionApp app = usage.first.getApp();
            List<GroupUsage> groups = usage.second;

            View itemView = inflater.inflate(R.layout.ongoing_usage_dialog_item, appsList, false);

            ((TextView) itemView.requireViewById(R.id.app_name)).setText(app.getLabel());
            ((ImageView) itemView.requireViewById(R.id.app_icon)).setImageDrawable(app.getIcon());

            // Add the icons for the groups this app used as long as multiple groups were used by
            // some app.
            if (usedGroups.size() > 1) {
                ViewGroup iconFrame = itemView.requireViewById(R.id.icons);
                int numGroups = usages.get(usageNum).second.size();
                for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                    ViewGroup group = (ViewGroup) inflater.inflate(R.layout.image_view, null);
                    ((ImageView) group.requireViewById(R.id.icon)).setImageDrawable(
                            Utils.applyTint(context, groups.get(groupNum).getGroup().getIconResId(),
                                    android.R.attr.colorControlNormal));
                    iconFrame.addView(group);
                }
                iconFrame.setVisibility(View.VISIBLE);
            }

            itemView.setOnClickListener((v) -> {
                String packageName = app.getPackageName();
                PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                        PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_LINE_ITEM, packageName);
                UserHandle user = UserHandle.getUserHandleForUid(app.getUid());
                Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
                intent.putExtra(Intent.EXTRA_USER, user);
                context.startActivity(intent);
                mDialog.dismiss();
            });

            appsList.addView(itemView);
        }

        // Set the title of the dialog based on all of the permissions used.
        StringBuilder titleBuilder = new StringBuilder();
        int numGroups = usedGroups.size();
        List<String> sortedGroups = new ArrayList<>(usedGroups);
        Collator collator = Collator.getInstance(
                getResources().getConfiguration().getLocales().get(0));
        sortedGroups.sort(collator);
        for (int i = 0; i < numGroups; i++) {
            titleBuilder.append(sortedGroups.get(i));
            if (i < numGroups - 2) {
                titleBuilder.append(getString(R.string.ongoing_usage_dialog_separator));
            } else if (i < numGroups - 1) {
                titleBuilder.append(getString(R.string.ongoing_usage_dialog_last_separator));
            }
        }

        ((TextView) contentView.requireViewById(R.id.title)).setText(
                getString(R.string.ongoing_usage_dialog_title, titleBuilder.toString()));

        return contentView;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // empty
    }
}
