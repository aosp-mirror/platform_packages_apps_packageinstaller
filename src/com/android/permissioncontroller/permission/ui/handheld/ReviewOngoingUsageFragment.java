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
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_DISMISS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_LINE_ITEM;
import static com.android.permissioncontroller.permission.debug.UtilsKt.shouldShowPermissionsDashboard;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.Html;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.data.OpAccess;
import com.android.permissioncontroller.permission.data.OpUsageLiveData;
import com.android.permissioncontroller.permission.debug.PermissionUsages;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps.PermissionApp;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A dialog listing the currently uses of camera, microphone, and location.
 */
public class ReviewOngoingUsageFragment extends PreferenceFragmentCompat {

    // TODO: Replace with OPSTR... APIs
    static final String PHONE_CALL = "android:phone_call_microphone";
    static final String VIDEO_CALL = "android:phone_call_camera";

    private AudioManager mAudioManager;
    private @NonNull PermissionUsages mPermissionUsages;
    private boolean mPermissionUsagesLoaded;
    private @Nullable AlertDialog mDialog;
    private OpUsageLiveData mOpUsageLiveData;
    private @Nullable Map<String, List<OpAccess>> mOpUsage;
    private ArraySet<String> mSystemUsage = new ArraySet<>(0);
    private long mStartTime;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onPermissionUsagesLoaded();
        }
    };

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

        long numMillis = getArguments().getLong(Intent.EXTRA_DURATION_MILLIS);

        mAudioManager = getContext().getSystemService(AudioManager.class);
        getContext().registerReceiver(mReceiver,
                new IntentFilter(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED));
        mPermissionUsages = new PermissionUsages(getActivity());
        mStartTime = Math.max(System.currentTimeMillis() - numMillis, Instant.EPOCH.toEpochMilli());
        String[] permissions = new String[]{CAMERA, MICROPHONE};
        if (shouldShowPermissionsDashboard()) {
            permissions = new String[] {CAMERA, LOCATION, MICROPHONE};
        }
        ArrayList<String> appOps = new ArrayList<>(List.of(PHONE_CALL, VIDEO_CALL));
        mOpUsageLiveData = OpUsageLiveData.Companion.get(appOps, numMillis);
        mOpUsageLiveData.observeStale(this, new Observer<Map<String, List<OpAccess>>>() {
            @Override
            public void onChanged(Map<String, List<OpAccess>> opUsage) {
                if (mOpUsageLiveData.isStale()) {
                    return;
                }
                mOpUsage = opUsage;
                mOpUsageLiveData.removeObserver(this);

                if (mPermissionUsagesLoaded) {
                    onPermissionUsagesLoaded();
                }
            }
        });
        mPermissionUsages.load(null, permissions, mStartTime, Long.MAX_VALUE,
                PermissionUsages.USAGE_FLAG_LAST, getActivity().getLoaderManager(), false, false,
                this::onPermissionUsagesLoaded, false);
    }

    private void onPermissionUsagesLoaded() {
        mPermissionUsagesLoaded = true;
        if (getActivity() == null || mOpUsage == null) {
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

                if (!groupUsage.isRunning()) {
                    if (groupUsage.getLastAccessDuration() == -1) {
                        if (groupUsage.getLastAccessTime() < mStartTime) {
                            continue;
                        }
                    } else {
                        // TODO: Warning: Only works for groups with a single permission as it is
                        // not guaranteed the last access time and duration refer to same permission
                        // in AppPermissionUsage#lastAccessAggregate
                        if (groupUsage.getLastAccessTime() + groupUsage.getLastAccessDuration()
                                < mStartTime) {
                            continue;
                        }
                    }
                }

                if (Utils.isGroupOrBgGroupUserSensitive(groupUsage.getGroup())) {
                    usedGroups.add(appGroups.get(groupNum));
                } else if (getContext().getSystemService(LocationManager.class).isProviderPackage(
                        appUsage.getPackageName())
                        && (groupName.equals(CAMERA) || groupName.equals(MICROPHONE))) {
                    mSystemUsage.add(groupName);
                }
            }

            if (!usedGroups.isEmpty()) {
                usages.add(Pair.create(appUsage, usedGroups));
                permApps.add(appUsage.getApp());
            }
        }

        if (usages.isEmpty() && mOpUsage.isEmpty() && mSystemUsage.isEmpty()) {
            getActivity().finish();
            return;
        }

        new PermissionApps.AppDataLoader(getActivity(), () -> showDialog(usages))
                .execute(permApps.toArray(new PermissionApps.PermissionApp[permApps.size()]));
    }

    private void showDialog(@NonNull List<Pair<AppPermissionUsage, List<GroupUsage>>> usages) {
        if (mDialog == null || !mDialog.isShowing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setView(createDialogView(usages))
                    .setPositiveButton(R.string.ongoing_usage_dialog_ok, (dialog, which) ->
                            PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                                    PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_DISMISS))
                    .setOnDismissListener((dialog) -> getActivity().finish());
            mDialog = builder.create();
            mDialog.show();
        } else {
            mDialog.setView(createDialogView(usages));
        }
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
            // TODO: Use internationalization safe concatenation

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

    private @NonNull View createDialogView(
            @NonNull List<Pair<AppPermissionUsage, List<GroupUsage>>> usages) {
        Context context = getActivity();
        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.ongoing_usage_dialog_content, null);
        ViewGroup appsList = contentView.requireViewById(R.id.items_container);

        // Compute all of the permission group labels that were used.
        ArrayMap<String, CharSequence> usedGroups = new ArrayMap<>();
        int numUsages = usages.size();
        for (int usageNum = 0; usageNum < numUsages; usageNum++) {
            List<GroupUsage> groups = usages.get(usageNum).second;
            int numGroups = groups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionGroup group = groups.get(groupNum).getGroup();
                if (group.getName().equals(MICROPHONE) && mAudioManager.isMicrophoneMute()) {
                    continue;
                }
                usedGroups.put(group.getName(), group.getLabel());
            }
        }

        TextView otherUseHeader = contentView.requireViewById(R.id.other_use_header);
        TextView otherUseContent = contentView.requireViewById(R.id.other_use_content);
        TextView systemUseContent = contentView.requireViewById(R.id.system_use_content);
        View otherUseSpacer = contentView.requireViewById(R.id.other_use_inside_spacer);

        if (mOpUsage.isEmpty() && mSystemUsage.isEmpty()) {
            otherUseHeader.setVisibility(View.GONE);
            otherUseContent.setVisibility(View.GONE);
        }

        if (numUsages == 0) {
            otherUseHeader.setVisibility(View.GONE);
            appsList.setVisibility(View.GONE);
        }

        if (mOpUsage.isEmpty() || mSystemUsage.isEmpty()) {
            otherUseSpacer.setVisibility(View.GONE);
        }

        if (mOpUsage.isEmpty()) {
            otherUseContent.setVisibility(View.GONE);
        }

        if (mSystemUsage.isEmpty()) {
            systemUseContent.setVisibility(View.GONE);
        }

        if (!mOpUsage.isEmpty()) {
            boolean hasVideo = mOpUsage.containsKey(VIDEO_CALL);
            boolean hasPhone = mOpUsage.containsKey(PHONE_CALL)
                    && !mAudioManager.isMicrophoneMute();
            if (hasVideo && hasPhone) {
                otherUseContent.setText(
                        Html.fromHtml(getString(R.string.phone_call_uses_microphone_and_camera),
                                0));
            } else if (hasVideo && mAudioManager.isMicrophoneMute()) {
                otherUseContent.setText(
                        Html.fromHtml(getString(R.string.phone_call_uses_camera), 0));
            } else if (hasPhone) {
                otherUseContent.setText(
                        Html.fromHtml(getString(R.string.phone_call_uses_microphone), 0));
            }

            if (hasVideo) {
                usedGroups.put(CAMERA, KotlinUtils.INSTANCE.getPermGroupLabel(context, CAMERA));
                if (!mAudioManager.isMicrophoneMute()) {
                    usedGroups.put(MICROPHONE,
                            KotlinUtils.INSTANCE.getPermGroupLabel(context, MICROPHONE));
                }
            }

            if (hasPhone) {
                usedGroups.put(MICROPHONE,
                        KotlinUtils.INSTANCE.getPermGroupLabel(context, MICROPHONE));
            }
        }

        if (!mSystemUsage.isEmpty()) {
            if (mSystemUsage.contains(MICROPHONE) && mSystemUsage.contains(CAMERA)
                    && !mAudioManager.isMicrophoneMute()) {
                systemUseContent.setText(getString(R.string.system_uses_microphone_and_camera));
            } else if (mSystemUsage.contains(CAMERA)) {
                systemUseContent.setText(getString(R.string.system_uses_camera));
            } else if (mSystemUsage.contains(MICROPHONE) && !mAudioManager.isMicrophoneMute()) {
                systemUseContent.setText(getString(R.string.system_uses_microphone));
            }

            for (String usage : mSystemUsage) {
                usedGroups.put(usage, KotlinUtils.INSTANCE.getPermGroupLabel(context, usage));
            }
        }

        // Add the layout for each app.
        for (int usageNum = 0; usageNum < numUsages; usageNum++) {
            Pair<AppPermissionUsage, List<GroupUsage>> usage = usages.get(usageNum);
            PermissionApp app = usage.first.getApp();
            List<GroupUsage> groups = usage.second;

            // Check if this uses only mic permission. If the mic is muted, do not show
            if (groups.size() == 1) {
                if (groups.get(0).getGroup().getName().equals(MICROPHONE)
                        && mAudioManager.isMicrophoneMute()) {
                    continue;
                }
            }

            View itemView = inflater.inflate(R.layout.ongoing_usage_dialog_item, appsList, false);

            ((TextView) itemView.requireViewById(R.id.app_name)).setText(app.getLabel());
            ((ImageView) itemView.requireViewById(R.id.app_icon)).setImageDrawable(app.getIcon());

            ArrayMap<String, CharSequence> usedGroupsThisApp = new ArrayMap<>();

            ViewGroup iconFrame = itemView.requireViewById(R.id.icons);
            int numGroups = usages.get(usageNum).second.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionGroup group = groups.get(groupNum).getGroup();
                if (mAudioManager.isMicrophoneMute() && group.getName().equals(MICROPHONE)) {
                    continue;
                }

                ViewGroup groupView = (ViewGroup) inflater.inflate(R.layout.image_view, null);
                ((ImageView) groupView.requireViewById(R.id.icon)).setImageDrawable(
                        Utils.applyTint(context, group.getIconResId(),
                                android.R.attr.colorControlNormal));
                iconFrame.addView(groupView);

                usedGroupsThisApp.put(group.getName(), group.getLabel());
            }
            iconFrame.setVisibility(View.VISIBLE);

            TextView permissionsList = itemView.requireViewById(R.id.permissionsList);
            permissionsList.setText(getListOfPermissionLabels(usedGroupsThisApp));
            permissionsList.setVisibility(View.VISIBLE);

            itemView.setOnClickListener((v) -> {
                String packageName = app.getPackageName();
                PermissionControllerStatsLog.write(PRIVACY_INDICATORS_INTERACTED,
                        PRIVACY_INDICATORS_INTERACTED__TYPE__DIALOG_LINE_ITEM);
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

        ((TextView) contentView.requireViewById(R.id.title)).setText(
                getString(R.string.ongoing_usage_dialog_title,
                        getListOfPermissionLabels(usedGroups)));

        return contentView;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // empty
    }

    @Override
    public void onDestroy() {
        getContext().unregisterReceiver(mReceiver);
        super.onDestroy();

    }
}
