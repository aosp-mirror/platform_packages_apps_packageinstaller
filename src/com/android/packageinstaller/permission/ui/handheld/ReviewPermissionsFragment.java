/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;

import static com.android.packageinstaller.PermissionControllerStatsLog.REVIEW_PERMISSIONS_FRAGMENT_RESULT_REPORTED;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.ui.ManagePermissionsActivity;
import com.android.packageinstaller.permission.utils.ArrayUtils;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * If an app does not support runtime permissions the user is prompted via this fragment to select
 * which permissions to grant to the app before first use and if an update changed the permissions.
 */
public final class ReviewPermissionsFragment extends PreferenceFragmentCompat
        implements View.OnClickListener, PermissionPreference.PermissionPreferenceChangeListener,
        PermissionPreference.PermissionPreferenceOwnerFragment {

    private static final String EXTRA_PACKAGE_INFO =
            "com.android.packageinstaller.permission.ui.extra.PACKAGE_INFO";
    private static final String LOG_TAG = ReviewPermissionsFragment.class.getSimpleName();

    private AppPermissions mAppPermissions;

    private Button mContinueButton;
    private Button mCancelButton;
    private Button mMoreInfoButton;

    private PreferenceCategory mNewPermissionsCategory;
    private PreferenceCategory mCurrentPermissionsCategory;

    private boolean mHasConfirmedRevoke;

    public static ReviewPermissionsFragment newInstance(PackageInfo packageInfo) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(ReviewPermissionsFragment.EXTRA_PACKAGE_INFO, packageInfo);
        ReviewPermissionsFragment instance = new ReviewPermissionsFragment();
        instance.setArguments(arguments);
        instance.setRetainInstance(true);
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        PackageInfo packageInfo = getArguments().getParcelable(EXTRA_PACKAGE_INFO);
        if (packageInfo == null) {
            activity.finish();
            return;
        }

        mAppPermissions = new AppPermissions(activity, packageInfo, false, true,
                () -> getActivity().finish());

        boolean reviewRequired = false;
        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (group.isReviewRequired() || (group.getBackgroundPermissions() != null
                    && group.getBackgroundPermissions().isReviewRequired())) {
                reviewRequired = true;
                break;
            }
        }

        if (!reviewRequired) {
            // If the system called for a review but no groups are found, this means that all groups
            // are restricted. Hence there is nothing to review and instantly continue.
            confirmPermissionsReview();
            executeCallback(true);
            activity.finish();
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // empty
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppPermissions.refresh();
        loadPreferences();
    }

    @Override
    public void onClick(View view) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (view == mContinueButton) {
            confirmPermissionsReview();
            executeCallback(true);
        } else if (view == mCancelButton) {
            executeCallback(false);
            activity.setResult(Activity.RESULT_CANCELED);
        } else if (view == mMoreInfoButton) {
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME,
                    mAppPermissions.getPackageInfo().packageName);
            intent.putExtra(Intent.EXTRA_USER, UserHandle.getUserHandleForUid(
                    mAppPermissions.getPackageInfo().applicationInfo.uid));
            intent.putExtra(ManagePermissionsActivity.EXTRA_ALL_PERMISSIONS, true);
            getActivity().startActivity(intent);
        }
        activity.finish();
    }

    private void grantReviewedPermission(AppPermissionGroup group) {
        String[] permissionsToGrant = null;
        final int permissionCount = group.getPermissions().size();
        for (int j = 0; j < permissionCount; j++) {
            final Permission permission = group.getPermissions().get(j);
            if (permission.isReviewRequired()) {
                permissionsToGrant = ArrayUtils.appendString(
                        permissionsToGrant, permission.getName());
            }
        }
        if (permissionsToGrant != null) {
            group.grantRuntimePermissions(false, permissionsToGrant);
        }
    }

    private void confirmPermissionsReview() {
        final List<PreferenceGroup> preferenceGroups = new ArrayList<>();
        if (mNewPermissionsCategory != null) {
            preferenceGroups.add(mNewPermissionsCategory);
            preferenceGroups.add(mCurrentPermissionsCategory);
        } else {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            if (preferenceScreen != null) {
                preferenceGroups.add(preferenceScreen);
            }
        }

        final int preferenceGroupCount = preferenceGroups.size();
        long changeIdForLogging = new Random().nextLong();

        for (int groupNum = 0; groupNum < preferenceGroupCount; groupNum++) {
            final PreferenceGroup preferenceGroup = preferenceGroups.get(groupNum);

            final int preferenceCount = preferenceGroup.getPreferenceCount();
            for (int prefNum = 0; prefNum < preferenceCount; prefNum++) {
                Preference preference = preferenceGroup.getPreference(prefNum);
                if (preference instanceof PermissionReviewPreference) {
                    PermissionReviewPreference permPreference =
                            (PermissionReviewPreference) preference;
                    AppPermissionGroup group = permPreference.getGroup();

                    // If the preference wasn't toggled we show it as "granted"
                    if (group.isReviewRequired() && !permPreference.wasChanged()) {
                        grantReviewedPermission(group);
                    }
                    logReviewPermissionsFragmentResult(changeIdForLogging, group);

                    AppPermissionGroup backgroundGroup = group.getBackgroundPermissions();
                    if (backgroundGroup != null) {
                        // If the preference wasn't toggled we show it as "fully granted"
                        if (backgroundGroup.isReviewRequired() && !permPreference.wasChanged()) {
                            grantReviewedPermission(backgroundGroup);
                        }
                        logReviewPermissionsFragmentResult(changeIdForLogging, backgroundGroup);
                    }
                }
            }
        }
        mAppPermissions.persistChanges(true);

        // Some permission might be restricted and hence there is no AppPermissionGroup for it.
        // Manually unset all review-required flags, regardless of restriction.
        PackageManager pm = getContext().getPackageManager();
        PackageInfo pkg = mAppPermissions.getPackageInfo();
        UserHandle user = UserHandle.getUserHandleForUid(pkg.applicationInfo.uid);

        for (String perm : pkg.requestedPermissions) {
            try {
                pm.updatePermissionFlags(perm, pkg.packageName, FLAG_PERMISSION_REVIEW_REQUIRED,
                        0, user);
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, "Cannot unmark " + perm + " requested by " + pkg.packageName
                        + " as review required", e);
            }
        }
    }

    private void logReviewPermissionsFragmentResult(long changeId, AppPermissionGroup group) {
        ArrayList<Permission> permissions = group.getPermissions();

        int numPermissions = permissions.size();
        for (int i = 0; i < numPermissions; i++) {
            Permission permission = permissions.get(i);

            PermissionControllerStatsLog.write(REVIEW_PERMISSIONS_FRAGMENT_RESULT_REPORTED,
                    changeId, group.getApp().applicationInfo.uid, group.getApp().packageName,
                    permission.getName(), permission.isGrantedIncludingAppOp());
            Log.v(LOG_TAG, "Permission grant via permission review changeId=" + changeId + " uid="
                    + group.getApp().applicationInfo.uid + " packageName="
                    + group.getApp().packageName + " permission="
                    + permission.getName() + " granted=" + permission.isGrantedIncludingAppOp());
        }
    }

    private void bindUi() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Set icon
        Drawable icon = mAppPermissions.getPackageInfo().applicationInfo.loadIcon(
                activity.getPackageManager());
        ImageView iconView = activity.requireViewById(R.id.app_icon);
        iconView.setImageDrawable(icon);

        // Set message
        final int labelTemplateResId = isPackageUpdated()
                ? R.string.permission_review_title_template_update
                : R.string.permission_review_title_template_install;
        Spanned message = Html.fromHtml(getString(labelTemplateResId,
                mAppPermissions.getAppLabel()), 0);

        // Set the permission message as the title so it can be announced.
        activity.setTitle(message.toString());

        // Color the app name.
        TextView permissionsMessageView = activity.requireViewById(
                R.id.permissions_message);
        permissionsMessageView.setText(message);

        mContinueButton = getActivity().requireViewById(R.id.continue_button);
        mContinueButton.setOnClickListener(this);

        mCancelButton = getActivity().requireViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(this);

        if (activity.getPackageManager().arePermissionsIndividuallyControlled()) {
            mMoreInfoButton = getActivity().requireViewById(
                    R.id.permission_more_info_button);
            mMoreInfoButton.setOnClickListener(this);
            mMoreInfoButton.setVisibility(View.VISIBLE);
        }
    }

    private PermissionReviewPreference getPreference(String key) {
        if (mNewPermissionsCategory != null) {
            PermissionReviewPreference pref =
                    (PermissionReviewPreference) mNewPermissionsCategory.findPreference(key);

            if (pref == null && mCurrentPermissionsCategory != null) {
                return (PermissionReviewPreference) mCurrentPermissionsCategory.findPreference(key);
            } else {
                return pref;
            }
        } else {
            return (PermissionReviewPreference) getPreferenceScreen().findPreference(key);
        }
    }

    private void loadPreferences() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(getContext());
            setPreferenceScreen(screen);
        } else {
            screen.removeAll();
        }

        mCurrentPermissionsCategory = null;
        mNewPermissionsCategory = null;

        final boolean isPackageUpdated = isPackageUpdated();

        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (!Utils.shouldShowPermission(getContext(), group)
                    || !Utils.OS_PKG.equals(group.getDeclaringPackage())) {
                continue;
            }

            PermissionReviewPreference preference = getPreference(group.getName());
            if (preference == null) {
                preference = new PermissionReviewPreference(this, group, this);

                preference.setKey(group.getName());
                Drawable icon = Utils.loadDrawable(activity.getPackageManager(),
                        group.getIconPkg(), group.getIconResId());
                preference.setIcon(Utils.applyTint(getContext(), icon,
                        android.R.attr.colorControlNormal));
                preference.setTitle(group.getLabel());
            } else {
                preference.updateUi();
            }

            if (group.isReviewRequired() || (group.getBackgroundPermissions() != null
                    && group.getBackgroundPermissions().isReviewRequired())) {
                if (!isPackageUpdated) {
                    screen.addPreference(preference);
                } else {
                    if (mNewPermissionsCategory == null) {
                        mNewPermissionsCategory = new PreferenceCategory(activity);
                        mNewPermissionsCategory.setTitle(R.string.new_permissions_category);
                        mNewPermissionsCategory.setOrder(1);
                        screen.addPreference(mNewPermissionsCategory);
                    }
                    mNewPermissionsCategory.addPreference(preference);
                }
            } else {
                if (mCurrentPermissionsCategory == null) {
                    mCurrentPermissionsCategory = new PreferenceCategory(activity);
                    mCurrentPermissionsCategory.setTitle(R.string.current_permissions_category);
                    mCurrentPermissionsCategory.setOrder(2);
                    screen.addPreference(mCurrentPermissionsCategory);
                }
                mCurrentPermissionsCategory.addPreference(preference);
            }
        }
    }

    private boolean isPackageUpdated() {
        List<AppPermissionGroup> groups = mAppPermissions.getPermissionGroups();
        final int groupCount = groups.size();
        for (int i = 0; i < groupCount; i++) {
            AppPermissionGroup group = groups.get(i);
            if (!(group.isReviewRequired() || (group.getBackgroundPermissions() != null
                    && group.getBackgroundPermissions().isReviewRequired()))) {
                return true;
            }
        }
        return false;
    }

    private void executeCallback(boolean success) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (success) {
            IntentSender intent = activity.getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
            if (intent != null) {
                try {
                    int flagMask = 0;
                    int flagValues = 0;
                    if (activity.getIntent().getBooleanExtra(
                            Intent.EXTRA_RESULT_NEEDED, false)) {
                        flagMask = Intent.FLAG_ACTIVITY_FORWARD_RESULT;
                        flagValues = Intent.FLAG_ACTIVITY_FORWARD_RESULT;
                    }
                    activity.startIntentSenderForResult(intent, -1, null,
                            flagMask, flagValues, 0);
                } catch (IntentSender.SendIntentException e) {
                        /* ignore */
                }
                return;
            }
        }
        RemoteCallback callback = activity.getIntent().getParcelableExtra(
                Intent.EXTRA_REMOTE_CALLBACK);
        if (callback != null) {
            Bundle result = new Bundle();
            result.putBoolean(Intent.EXTRA_RETURN_RESULT, success);
            callback.sendResult(result);
        }
    }

    @Override
    public boolean shouldConfirmDefaultPermissionRevoke() {
        return !mHasConfirmedRevoke;
    }

    @Override
    public void hasConfirmDefaultPermissionRevoke() {
        mHasConfirmedRevoke = true;
    }

    @Override
    public void onPreferenceChanged(String key) {
        getPreference(key).setChanged();
    }

    @Override
    public void onDenyAnyWay(String key, int changeTarget) {
        getPreference(key).onDenyAnyWay(changeTarget);
    }

    @Override
    public void onBackgroundAccessChosen(String key, int chosenItem) {
        getPreference(key).onBackgroundAccessChosen(chosenItem);
    }

    /**
     * Extend the {@link PermissionPreference}:
     * <ul>
     *     <li>Show the description of the permission group</li>
     *     <li>Show the permission group as granted if the user has not toggled it yet. This means
     *     that if the user does not touch the preference, we will later grant the permission
     *     in {@link #confirmPermissionsReview()}.</li>
     * </ul>
     */
    private static class PermissionReviewPreference extends PermissionPreference {
        private final AppPermissionGroup mGroup;
        private boolean mWasChanged;

        PermissionReviewPreference(PreferenceFragmentCompat fragment, AppPermissionGroup group,
                PermissionPreferenceChangeListener callbacks) {
            super(fragment, group, callbacks, 0);

            mGroup = group;
            updateUi();
        }

        AppPermissionGroup getGroup() {
            return mGroup;
        }

        /**
         * Mark the permission as changed by the user
         */
        void setChanged() {
            mWasChanged = true;
            updateUi();
        }

        /**
         * @return {@code true} iff the permission was changed by the user
         */
        boolean wasChanged() {
            return mWasChanged;
        }

        @Override
        void updateUi() {
            // updateUi might be called in super-constructor before group is initialized
            if (mGroup == null) {
                return;
            }

            super.updateUi();

            if (isEnabled()) {
                if (mGroup.isReviewRequired() && !mWasChanged) {
                    setSummary(mGroup.getDescription());
                    setCheckedOverride(true);
                } else if (TextUtils.isEmpty(getSummary())) {
                    // Sometimes the summary is already used, e.g. when this for a
                    // foreground/background group. In this case show leave the original summary.
                    setSummary(mGroup.getDescription());
                }
            }
        }
    }
}
