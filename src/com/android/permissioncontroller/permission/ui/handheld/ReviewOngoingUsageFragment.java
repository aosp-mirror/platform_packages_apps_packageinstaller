/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_DISMISS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_LINE_ITEM;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.icu.text.ListFormatter;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.Html;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.model.ReviewOngoingUsageViewModel;
import com.android.permissioncontroller.permission.ui.model.ReviewOngoingUsageViewModel.PackageAttribution;
import com.android.permissioncontroller.permission.ui.model.ReviewOngoingUsageViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A dialog listing the currently uses of camera, microphone, and location.
 */
public class ReviewOngoingUsageFragment extends PreferenceFragmentCompat {
    private static final String LOG_TAG = ReviewOngoingUsageFragment.class.getSimpleName();

    // TODO: Replace with OPSTR... APIs
    public static final String PHONE_CALL = "android:phone_call_microphone";
    public static final String VIDEO_CALL = "android:phone_call_camera";

    private @Nullable AlertDialog mDialog;
    private ReviewOngoingUsageViewModel mViewModel;

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

        ReviewOngoingUsageViewModelFactory factory =
                new ReviewOngoingUsageViewModelFactory(
                        getArguments().getLong(Intent.EXTRA_DURATION_MILLIS), this, new Bundle());
        mViewModel = new ViewModelProvider(this, factory).get(ReviewOngoingUsageViewModel.class);

        mViewModel.getUsages().observe(this, usages -> {
            if (mViewModel.getUsages().isStale()) {
                // Prevent stale data from being shown, if Dialog is shown twice in quick succession
                return;
            }
            if (usages == null) {
                getActivity().finish();
                return;
            }

            if (mDialog == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setView(updateDialogView(usages))
                        .setPositiveButton(R.string.ongoing_usage_dialog_ok, (dialog, which) ->
                                PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                                        PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_DISMISS))
                        .setOnDismissListener((dialog) -> getActivity().finish());
                mDialog = builder.create();
                mDialog.show();
            } else {
                updateDialogView(usages);
            }
        });
    }

    /**
     * Get a list of permission labels.
     *
     * @param groups map<perm group name, perm group label>
     *
     * @return A localized string with the list of permissions
     */
    private CharSequence getListOfPermissionLabels(ArrayMap<String, CharSequence> groups) {
        int numGroups = groups.size();

        if (numGroups == 1) {
            return groups.valueAt(0);
        } else if (numGroups == 2 && groups.containsKey(MICROPHONE) && groups.containsKey(CAMERA)) {
            // Special case camera + mic permission to be localization friendly
            return getContext().getString(R.string.permgroup_list_microphone_and_camera);
        } else {
            ArrayList<CharSequence> sortedGroups = new ArrayList<>(groups.values());
            Collator collator = Collator.getInstance(
                    getResources().getConfiguration().getLocales().get(0));
            sortedGroups.sort(collator);

            StringBuilder listBuilder = new StringBuilder();

            for (int i = 0; i < numGroups; i++) {
                listBuilder.append(sortedGroups.get(i));
                if (i < numGroups - 2) {
                    listBuilder.append(getString(R.string.ongoing_usage_dialog_separator));
                } else if (i < numGroups - 1) {
                    listBuilder.append(getString(R.string.ongoing_usage_dialog_last_separator));
                }
            }

            return listBuilder;
        }
    }

    private @NonNull View updateDialogView(@NonNull ReviewOngoingUsageViewModel.Usages allUsages) {
        Activity context = getActivity();

        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.ongoing_usage_dialog_content, null);
        ViewGroup appsList = contentView.requireViewById(R.id.items_container);
        Map<PackageAttribution, Set<String>> appUsages = allUsages.getAppUsages();
        Collection<String> callUsage = allUsages.getCallUsages();
        Map<PackageAttribution, List<CharSequence>> attrLabels = allUsages.getShownAttributions();

        // Compute all of the permission group labels that were used.
        ArrayMap<String, CharSequence> usedGroups = new ArrayMap<>();
        for (Set<String> accessedPermGroupNames : appUsages.values()) {
            for (String accessedPermGroupName : accessedPermGroupNames) {
                usedGroups.put(accessedPermGroupName, KotlinUtils.INSTANCE.getPermGroupLabel(
                        context, accessedPermGroupName).toString());
            }
        }

        TextView otherUseHeader = contentView.requireViewById(R.id.other_use_header);
        TextView otherUseContent = contentView.requireViewById(R.id.other_use_content);

        boolean hasCallUsage = !callUsage.isEmpty();
        boolean hasAppUsages = !appUsages.isEmpty();

        if (!hasCallUsage) {
            otherUseHeader.setVisibility(View.GONE);
            otherUseContent.setVisibility(View.GONE);
        }

        if (!hasAppUsages) {
            otherUseHeader.setVisibility(View.GONE);
            appsList.setVisibility(View.GONE);
        }

        if (!hasCallUsage) {
            otherUseContent.setVisibility(View.GONE);
        }

        if (hasCallUsage) {
            if (callUsage.contains(VIDEO_CALL) && callUsage.contains(PHONE_CALL)) {
                otherUseContent.setText(
                        Html.fromHtml(getString(R.string.phone_call_uses_microphone_and_camera),
                                0));
            } else if (callUsage.contains(VIDEO_CALL)) {
                otherUseContent.setText(
                        Html.fromHtml(getString(R.string.phone_call_uses_camera), 0));
            } else if (callUsage.contains(PHONE_CALL)) {
                otherUseContent.setText(
                        Html.fromHtml(getString(R.string.phone_call_uses_microphone), 0));
            }

            if (callUsage.contains(VIDEO_CALL)) {
                usedGroups.put(CAMERA, KotlinUtils.INSTANCE.getPermGroupLabel(context, CAMERA));
            }

            if (callUsage.contains(PHONE_CALL)) {
                usedGroups.put(MICROPHONE, KotlinUtils.INSTANCE.getPermGroupLabel(context,
                        MICROPHONE));
            }
        }

        // Add the layout for each app.
        for (Map.Entry<PackageAttribution, Set<String>> usage : appUsages.entrySet()) {
            String packageName = usage.getKey().getPackageName();
            UserHandle user = usage.getKey().getUser();

            Set<String> groups = usage.getValue();

            View itemView = inflater.inflate(R.layout.ongoing_usage_dialog_item, appsList, false);

            ((TextView) itemView.requireViewById(R.id.app_name))
                    .setText(KotlinUtils.INSTANCE.getPackageLabel(context.getApplication(),
                            packageName, user));
            ((ImageView) itemView.requireViewById(R.id.app_icon))
                    .setImageDrawable(KotlinUtils.INSTANCE.getBadgedPackageIcon(
                            context.getApplication(), packageName, user));

            ArrayMap<String, CharSequence> usedGroupsThisApp = new ArrayMap<>();

            ViewGroup iconFrame = itemView.requireViewById(R.id.icons);
            CharSequence specialGroupMessage = null;
            for (String group : groups) {
                ViewGroup groupView = (ViewGroup) inflater.inflate(R.layout.image_view, null);
                ((ImageView) groupView.requireViewById(R.id.icon)).setImageDrawable(
                        Utils.applyTint(context, KotlinUtils.INSTANCE.getPermGroupIcon(context,
                                group), android.R.attr.colorControlNormal));
                iconFrame.addView(groupView);

                CharSequence groupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(context, group);
                if (group.equals(MICROPHONE) && attrLabels.containsKey(usage.getKey())) {
                    specialGroupMessage = ListFormatter.getInstance().format(
                            attrLabels.get(usage.getKey()));
                    continue;
                }

                usedGroupsThisApp.put(group, groupLabel);
            }
            iconFrame.setVisibility(View.VISIBLE);

            TextView permissionsList = itemView.requireViewById(R.id.permissionsList);
            permissionsList.setText(specialGroupMessage != null
                    ? specialGroupMessage : getListOfPermissionLabels(usedGroupsThisApp));

            itemView.setOnClickListener((v) -> {
                PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                        PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_LINE_ITEM);
                Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
                intent.putExtra(Intent.EXTRA_USER, user);
                context.startActivity(intent);
                mDialog.dismiss();
            });

            appsList.addView(itemView);
        }

        ((TextView) contentView.requireViewById(R.id.title)).setText(getTitle(usedGroups));

        return contentView;
    }

    private CharSequence getTitle(ArrayMap<String, CharSequence> usedGroups) {
        if (usedGroups.size() == 1 && usedGroups.keyAt(0).equals(MICROPHONE)) {
            return getString(R.string.ongoing_usage_dialog_title_mic);
        } else if (usedGroups.size() == 1 && usedGroups.keyAt(0).equals(CAMERA)) {
            return getString(R.string.ongoing_usage_dialog_title_camera);
        } else if (usedGroups.size() == 2 && usedGroups.containsKey(MICROPHONE)
                && usedGroups.containsKey(CAMERA)) {
            return getString(R.string.ongoing_usage_dialog_title_mic_camera);
        } else {
            return getString(R.string.ongoing_usage_dialog_title,
                    getListOfPermissionLabels(usedGroups));
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // empty
    }
}
