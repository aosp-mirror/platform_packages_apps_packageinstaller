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

package com.android.packageinstaller.permission.ui.auto;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.TypedArrayUtils;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.packageinstaller.auto.AutoSettingsFrameFragment;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.PackageRemovalMonitor;
import com.android.packageinstaller.permission.utils.SafetyNetLogger;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.RestrictedLockUtils;

import java.lang.annotation.Retention;
import java.util.List;

/** Settings related to a particular permission for the given app. */
public class AutoAppPermissionFragment extends AutoSettingsFrameFragment {

    private static final String LOG_TAG = "AppPermissionFragment";

    @Retention(SOURCE)
    @IntDef(value = {CHANGE_FOREGROUND, CHANGE_BACKGROUND}, flag = true)
    @interface ChangeTarget {
    }

    static final int CHANGE_FOREGROUND = 1;
    static final int CHANGE_BACKGROUND = 2;
    static final int CHANGE_BOTH = CHANGE_FOREGROUND | CHANGE_BACKGROUND;

    @NonNull
    private AppPermissionGroup mGroup;

    @NonNull
    private TwoStatePreference mAlwaysPermissionPreference;
    @NonNull
    private TwoStatePreference mForegroundOnlyPermissionPreference;
    @NonNull
    private TwoStatePreference mDenyPermissionPreference;
    @NonNull
    private AutoTwoTargetPreference mDetailsPreference;

    private boolean mHasConfirmedRevoke;

    /**
     * Listens for changes to the permission of the app the permission is currently getting
     * granted to. {@code null} when unregistered.
     */
    @Nullable
    private PackageManager.OnPermissionsChangedListener mPermissionChangeListener;

    /**
     * Listens for changes to the app the permission is currently getting granted to. {@code null}
     * when unregistered.
     */
    @Nullable
    private PackageRemovalMonitor mPackageRemovalMonitor;

    /**
     * Returns a new {@link AutoAppPermissionFragment}.
     *
     * @param packageName the package name for which the permission is being changed
     * @param permName the name of the permission being changed
     * @param groupName the name of the permission group being changed
     * @param userHandle the user for which the permission is being changed
     */
    @NonNull
    public static AutoAppPermissionFragment newInstance(@NonNull String packageName,
            @NonNull String permName, @Nullable String groupName, @NonNull UserHandle userHandle) {
        AutoAppPermissionFragment fragment = new AutoAppPermissionFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHasConfirmedRevoke = false;

        mGroup = getAppPermissionGroup();
        if (mGroup == null) {
            requireActivity().setResult(Activity.RESULT_CANCELED);
            requireActivity().finish();
            return;
        }

        setHeaderLabel(
                getContext().getString(R.string.app_permission_title, mGroup.getFullLabel()));
    }

    private AppPermissionGroup getAppPermissionGroup() {
        Activity activity = getActivity();
        Context context = getPreferenceManager().getContext();

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (groupName == null) {
            groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        PackageItemInfo groupInfo = Utils.getGroupInfo(groupName, context);
        List<PermissionInfo> groupPermInfos = Utils.getGroupPermissionInfos(groupName, context);
        if (groupInfo == null || groupPermInfos == null) {
            Log.i(LOG_TAG, "Illegal group: " + groupName);
            return null;
        }
        UserHandle userHandle = getArguments().getParcelable(Intent.EXTRA_USER);
        PackageInfo packageInfo = AutoPermissionsUtils.getPackageInfo(activity, packageName,
                userHandle);
        if (packageInfo == null) {
            Log.i(LOG_TAG, "PackageInfo is null");
            return null;
        }
        AppPermissionGroup group = AppPermissionGroup.create(context, packageInfo, groupInfo,
                groupPermInfos, false);

        if (group == null || !Utils.shouldShowPermission(context, group)) {
            Log.i(LOG_TAG, "Illegal group: " + (group == null ? "null" : group.getName()));
            return null;
        }

        return group;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PreferenceScreen screen = getPreferenceScreen();
        screen.addPreference(
                AutoPermissionsUtils.createHeaderPreference(getContext(),
                        mGroup.getApp().applicationInfo));

        // Add permissions selector preferences.
        PreferenceGroup permissionSelector = new PreferenceCategory(getContext());
        permissionSelector.setTitle(
                getContext().getString(R.string.app_permission_header, mGroup.getFullLabel()));
        screen.addPreference(permissionSelector);

        mAlwaysPermissionPreference = new SelectedPermissionPreference(getContext());
        mAlwaysPermissionPreference.setTitle(R.string.app_permission_button_allow_always);
        permissionSelector.addPreference(mAlwaysPermissionPreference);

        mForegroundOnlyPermissionPreference = new SelectedPermissionPreference(getContext());
        mForegroundOnlyPermissionPreference.setTitle(
                R.string.app_permission_button_allow_foreground);
        permissionSelector.addPreference(mForegroundOnlyPermissionPreference);

        mDenyPermissionPreference = new SelectedPermissionPreference(getContext());
        mDenyPermissionPreference.setTitle(R.string.app_permission_button_deny);
        permissionSelector.addPreference(mDenyPermissionPreference);

        mDetailsPreference = new AutoTwoTargetPreference(getContext());
        screen.addPreference(mDetailsPreference);
    }

    @Override
    public void onStart() {
        super.onStart();
        Activity activity = requireActivity();

        mPermissionChangeListener = new PermissionChangeListener(
                mGroup.getApp().applicationInfo.uid);
        PackageManager pm = activity.getPackageManager();
        pm.addOnPermissionsChangeListener(mPermissionChangeListener);

        // Get notified when the package is removed.
        String packageName = mGroup.getApp().packageName;
        mPackageRemovalMonitor = new PackageRemovalMonitor(getContext(), packageName) {
            @Override
            public void onPackageRemoved() {
                Log.w(LOG_TAG, packageName + " was uninstalled");
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
            }
        };
        mPackageRemovalMonitor.register();

        // Check if the package was removed while this activity was not started.
        try {
            activity.createPackageContextAsUser(packageName, /* flags= */ 0,
                    mGroup.getUser()).getPackageManager().getPackageInfo(packageName,
                    /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(LOG_TAG, packageName + " was uninstalled while this activity was stopped", e);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }

        // Re-create the permission group in case permissions have changed and update the UI.
        mGroup = getAppPermissionGroup();
        updateUi();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mPackageRemovalMonitor != null) {
            mPackageRemovalMonitor.unregister();
            mPackageRemovalMonitor = null;
        }

        if (mPermissionChangeListener != null) {
            getActivity().getPackageManager().removeOnPermissionsChangeListener(
                    mPermissionChangeListener);
            mPermissionChangeListener = null;
        }
    }

    private void updateUi() {
        mDetailsPreference.setOnSecondTargetClickListener(null);
        mDetailsPreference.setVisible(false);

        if (mGroup.areRuntimePermissionsGranted()) {
            if (!mGroup.hasPermissionWithBackgroundMode()
                    || (mGroup.getBackgroundPermissions() != null
                    && mGroup.getBackgroundPermissions().areRuntimePermissionsGranted())) {
                setSelectedPermissionState(mAlwaysPermissionPreference);
            } else {
                setSelectedPermissionState(mForegroundOnlyPermissionPreference);
            }
        } else {
            setSelectedPermissionState(mDenyPermissionPreference);
        }

        mAlwaysPermissionPreference.setOnPreferenceClickListener(
                v -> requestChange(/* requestGrant= */true, CHANGE_BOTH));
        mForegroundOnlyPermissionPreference.setOnPreferenceClickListener(v -> {
            requestChange(/* requestGrant= */false, CHANGE_BACKGROUND);
            requestChange(/* requestGrant= */true, CHANGE_FOREGROUND);
            return true;
        });
        mDenyPermissionPreference.setOnPreferenceClickListener(
                v -> requestChange(/* requestGrant= */ false, CHANGE_BOTH));

        // Set the allow and foreground-only button states appropriately.
        if (mGroup.hasPermissionWithBackgroundMode()) {
            if (mGroup.getBackgroundPermissions() == null) {
                mAlwaysPermissionPreference.setVisible(false);
            } else {
                mForegroundOnlyPermissionPreference.setVisible(true);
                mAlwaysPermissionPreference.setTitle(R.string.app_permission_button_allow_always);
            }
        } else {
            mForegroundOnlyPermissionPreference.setVisible(false);
            mAlwaysPermissionPreference.setTitle(R.string.app_permission_button_allow);
        }

        // Handle the UI for various special cases.
        if (isSystemFixed() || isPolicyFullyFixed() || isForegroundDisabledByPolicy()) {
            // Disable changing permissions and potentially show administrator message.
            mAlwaysPermissionPreference.setEnabled(false);
            mForegroundOnlyPermissionPreference.setEnabled(false);
            mDenyPermissionPreference.setEnabled(false);

            RestrictedLockUtils.EnforcedAdmin admin = getAdmin();
            if (admin != null) {
                mDetailsPreference.setWidgetLayoutResource(R.layout.info_preference_widget);
                mDetailsPreference.setOnSecondTargetClickListener(
                        preference -> RestrictedLockUtils.sendShowAdminSupportDetailsIntent(
                                getContext(), admin));
            }

            updateDetailForFixedByPolicyPermissionGroup();
        } else if (Utils.areGroupPermissionsIndividuallyControlled(getContext(),
                mGroup.getName())) {
            // If the permissions are individually controlled, also show a link to the page that
            // lets you control them.
            mDetailsPreference.setWidgetLayoutResource(R.layout.settings_preference_widget);
            mDetailsPreference.setOnSecondTargetClickListener(
                    preference -> showAllPermissions(mGroup.getName()));

            updateDetailForIndividuallyControlledPermissionGroup();
        } else {
            if (mGroup.hasPermissionWithBackgroundMode()) {
                if (mGroup.getBackgroundPermissions() == null) {
                    // The group has background permissions but the app did not request any. I.e.
                    // The app can only switch between 'never" and "only in foreground".
                    mAlwaysPermissionPreference.setEnabled(false);

                    mDenyPermissionPreference.setOnPreferenceClickListener(v -> requestChange(false,
                            CHANGE_FOREGROUND));
                } else {
                    if (isBackgroundPolicyFixed()) {
                        // If background policy is fixed, we only allow switching the foreground.
                        // Note that this assumes that the background policy is fixed to deny,
                        // since if it is fixed to grant, so is the foreground.
                        mAlwaysPermissionPreference.setEnabled(false);
                        setSelectedPermissionState(mForegroundOnlyPermissionPreference);

                        mDenyPermissionPreference.setOnPreferenceClickListener(
                                v -> requestChange(false, CHANGE_FOREGROUND));

                        updateDetailForFixedByPolicyPermissionGroup();
                    } else if (isForegroundPolicyFixed()) {
                        // Foreground permissions are fixed to allow (the first case above handles
                        // fixing to deny), so we only allow toggling background permissions.
                        mDenyPermissionPreference.setEnabled(false);

                        mAlwaysPermissionPreference.setOnPreferenceClickListener(
                                v -> requestChange(true, CHANGE_BACKGROUND));
                        mForegroundOnlyPermissionPreference.setOnPreferenceClickListener(
                                v -> requestChange(false, CHANGE_BACKGROUND));

                        updateDetailForFixedByPolicyPermissionGroup();
                    } else {
                        // The default tri-state case is handled by default.
                    }
                }

            } else {
                // The default bi-state case is handled by default.
            }
        }
    }

    /**
     * Set the given permission state as the only checked permission state.
     */
    private void setSelectedPermissionState(@NonNull TwoStatePreference permissionState) {
        permissionState.setChecked(true);
        if (permissionState != mAlwaysPermissionPreference) {
            mAlwaysPermissionPreference.setChecked(false);
        }
        if (permissionState != mForegroundOnlyPermissionPreference) {
            mForegroundOnlyPermissionPreference.setChecked(false);
        }
        if (permissionState != mDenyPermissionPreference) {
            mDenyPermissionPreference.setChecked(false);
        }
    }

    /**
     * Are any permissions of this group fixed by the system, i.e. not changeable by the user.
     *
     * @return {@code true} iff any permission is fixed
     */
    private boolean isSystemFixed() {
        return mGroup.isSystemFixed();
    }

    /**
     * Is any foreground permissions of this group fixed by the policy, i.e. not changeable by the
     * user.
     *
     * @return {@code true} iff any foreground permission is fixed
     */
    private boolean isForegroundPolicyFixed() {
        return mGroup.isPolicyFixed();
    }

    /**
     * Is any background permissions of this group fixed by the policy, i.e. not changeable by the
     * user.
     *
     * @return {@code true} iff any background permission is fixed
     */
    private boolean isBackgroundPolicyFixed() {
        return mGroup.getBackgroundPermissions() != null
                && mGroup.getBackgroundPermissions().isPolicyFixed();
    }

    /**
     * Are there permissions fixed, so that the user cannot change the preference at all?
     *
     * @return {@code true} iff the permissions of this group are fixed
     */
    private boolean isPolicyFullyFixed() {
        return isForegroundPolicyFixed() && (mGroup.getBackgroundPermissions() == null
                || isBackgroundPolicyFixed());
    }

    /**
     * Is the foreground part of this group disabled. If the foreground is disabled, there is no
     * need to possible grant background access.
     *
     * @return {@code true} iff the permissions of this group are fixed
     */
    private boolean isForegroundDisabledByPolicy() {
        return isForegroundPolicyFixed() && !mGroup.areRuntimePermissionsGranted();
    }

    /**
     * Get the app that acts as admin for this profile.
     *
     * @return The admin or {@code null} if there is no admin.
     */
    @Nullable
    private RestrictedLockUtils.EnforcedAdmin getAdmin() {
        return RestrictedLockUtils.getProfileOrDeviceOwner(getContext(), mGroup.getUser());
    }

    /**
     * Update the detail in the case the permission group has individually controlled permissions.
     */
    private void updateDetailForIndividuallyControlledPermissionGroup() {
        int revokedCount = 0;
        List<Permission> permissions = mGroup.getPermissions();
        int permissionCount = permissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = permissions.get(i);
            if (!permission.isGrantedIncludingAppOp()) {
                revokedCount++;
            }
        }

        int resId;
        if (revokedCount == 0) {
            resId = R.string.permission_revoked_none;
        } else if (revokedCount == permissionCount) {
            resId = R.string.permission_revoked_all;
        } else {
            resId = R.string.permission_revoked_count;
        }

        mDetailsPreference.setSummary(getContext().getString(resId, revokedCount));
        mDetailsPreference.setVisible(true);
    }

    /**
     * Update the detail of a permission group that is at least partially fixed by policy.
     */
    private void updateDetailForFixedByPolicyPermissionGroup() {
        RestrictedLockUtils.EnforcedAdmin admin = getAdmin();
        AppPermissionGroup backgroundGroup = mGroup.getBackgroundPermissions();

        boolean hasAdmin = admin != null;

        if (isSystemFixed()) {
            // Permission is fully controlled by the system and cannot be switched

            setDetail(R.string.permission_summary_enabled_system_fixed);
        } else if (isForegroundDisabledByPolicy()) {
            // Permission is fully controlled by policy and cannot be switched

            if (hasAdmin) {
                setDetail(R.string.disabled_by_admin);
            } else {
                // Disabled state will be displayed by switch, so no need to add text for that
                setDetail(R.string.permission_summary_enforced_by_policy);
            }
        } else if (isPolicyFullyFixed()) {
            // Permission is fully controlled by policy and cannot be switched

            if (backgroundGroup == null) {
                if (hasAdmin) {
                    setDetail(R.string.enabled_by_admin);
                } else {
                    // Enabled state will be displayed by switch, so no need to add text for
                    // that
                    setDetail(R.string.permission_summary_enforced_by_policy);
                }
            } else {
                if (backgroundGroup.areRuntimePermissionsGranted()) {
                    if (hasAdmin) {
                        setDetail(R.string.enabled_by_admin);
                    } else {
                        // Enabled state will be displayed by switch, so no need to add text for
                        // that
                        setDetail(R.string.permission_summary_enforced_by_policy);
                    }
                } else {
                    if (hasAdmin) {
                        setDetail(
                                R.string.permission_summary_enabled_by_admin_foreground_only);
                    } else {
                        setDetail(
                                R.string.permission_summary_enabled_by_policy_foreground_only);
                    }
                }
            }
        } else {
            // Part of the permission group can still be switched

            if (isBackgroundPolicyFixed()) {
                if (backgroundGroup.areRuntimePermissionsGranted()) {
                    if (hasAdmin) {
                        setDetail(R.string.permission_summary_enabled_by_admin_background_only);
                    } else {
                        setDetail(R.string.permission_summary_enabled_by_policy_background_only);
                    }
                } else {
                    if (hasAdmin) {
                        setDetail(R.string.permission_summary_disabled_by_admin_background_only);
                    } else {
                        setDetail(R.string.permission_summary_disabled_by_policy_background_only);
                    }
                }
            } else if (isForegroundPolicyFixed()) {
                if (hasAdmin) {
                    setDetail(R.string.permission_summary_enabled_by_admin_foreground_only);
                } else {
                    setDetail(R.string.permission_summary_enabled_by_policy_foreground_only);
                }
            }
        }
    }

    /**
     * Show the given string as informative text below permission picker preferences.
     *
     * @param strId the resourceId of the string to display.
     */
    private void setDetail(int strId) {
        mDetailsPreference.setSummary(strId);
        mDetailsPreference.setVisible(true);
    }

    /**
     * Show all individual permissions in this group in a new fragment.
     */
    private void showAllPermissions(@NonNull String filterGroup) {
        Fragment frag = AutoAllAppPermissionsFragment.newInstance(mGroup.getApp().packageName,
                filterGroup, UserHandle.getUserHandleForUid(mGroup.getApp().applicationInfo.uid));
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    /**
     * Request to grant/revoke permissions group.
     *
     * <p>Does <u>not</u> handle:
     * <ul>
     * <li>Individually granted permissions</li>
     * <li>Permission groups with background permissions</li>
     * </ul>
     * <p><u>Does</u> handle:
     * <ul>
     * <li>Default grant permissions</li>
     * </ul>
     *
     * @param requestGrant If this group should be granted
     * @param changeTarget Which permission group (foreground/background/both) should be changed
     * @return If the request was processed.
     */
    private boolean requestChange(boolean requestGrant, @ChangeTarget int changeTarget) {
        if (LocationUtils.isLocationGroupAndProvider(getContext(), mGroup.getName(),
                mGroup.getApp().packageName)) {
            LocationUtils.showLocationDialog(getContext(),
                    Utils.getAppLabel(mGroup.getApp().applicationInfo, getContext()));

            // The request was denied, so update the buttons.
            updateUi();
            return false;
        }

        if (requestGrant) {
            if ((changeTarget & CHANGE_FOREGROUND) != 0) {
                if (!mGroup.areRuntimePermissionsGranted()) {
                    SafetyNetLogger.logPermissionToggled(mGroup);
                }

                mGroup.grantRuntimePermissions(false);
            }
            if ((changeTarget & CHANGE_BACKGROUND) != 0) {
                if (mGroup.getBackgroundPermissions() != null) {
                    if (!mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                        SafetyNetLogger.logPermissionToggled(mGroup.getBackgroundPermissions());
                    }

                    mGroup.getBackgroundPermissions().grantRuntimePermissions(false);
                }
            }
        } else {
            boolean showDefaultDenyDialog = false;

            if ((changeTarget & CHANGE_FOREGROUND) != 0
                    && mGroup.areRuntimePermissionsGranted()) {
                showDefaultDenyDialog = mGroup.hasGrantedByDefaultPermission()
                        || !mGroup.doesSupportRuntimePermissions()
                        || mGroup.hasInstallToRuntimeSplit();
            }
            if ((changeTarget & CHANGE_BACKGROUND) != 0) {
                if (mGroup.getBackgroundPermissions() != null
                        && mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                    AppPermissionGroup bgPerm = mGroup.getBackgroundPermissions();
                    showDefaultDenyDialog |= bgPerm.hasGrantedByDefaultPermission()
                            || !bgPerm.doesSupportRuntimePermissions()
                            || bgPerm.hasInstallToRuntimeSplit();
                }
            }

            if (showDefaultDenyDialog && !mHasConfirmedRevoke) {
                showDefaultDenyDialog(changeTarget);
                updateUi();
                return false;
            } else {
                if ((changeTarget & CHANGE_FOREGROUND) != 0
                        && mGroup.areRuntimePermissionsGranted()) {
                    if (mGroup.areRuntimePermissionsGranted()) {
                        SafetyNetLogger.logPermissionToggled(mGroup);
                    }

                    mGroup.revokeRuntimePermissions(false);
                }
                if ((changeTarget & CHANGE_BACKGROUND) != 0) {
                    if (mGroup.getBackgroundPermissions() != null
                            && mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                        if (mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                            SafetyNetLogger.logPermissionToggled(mGroup.getBackgroundPermissions());
                        }

                        mGroup.getBackgroundPermissions().revokeRuntimePermissions(false);
                    }
                }
            }
        }

        updateUi();

        return true;
    }

    /**
     * Show a dialog that warns the user that she/he is about to revoke permissions that were
     * granted by default.
     *
     * <p>The order of operation to revoke a permission granted by default is:
     * <ol>
     * <li>{@code showDefaultDenyDialog}</li>
     * <li>{@link DefaultDenyDialog#onCreateDialog}</li>
     * <li>{@link AutoAppPermissionFragment#onDenyAnyWay}</li>
     * </ol>
     *
     * @param changeTarget Whether background or foreground should be changed
     */
    private void showDefaultDenyDialog(@ChangeTarget int changeTarget) {
        Bundle args = new Bundle();

        boolean showGrantedByDefaultWarning = false;
        if ((changeTarget & CHANGE_FOREGROUND) != 0) {
            showGrantedByDefaultWarning = mGroup.hasGrantedByDefaultPermission();
        }
        if ((changeTarget & CHANGE_BACKGROUND) != 0) {
            if (mGroup.getBackgroundPermissions() != null) {
                showGrantedByDefaultWarning |=
                        mGroup.getBackgroundPermissions().hasGrantedByDefaultPermission();
            }
        }

        args.putInt(DefaultDenyDialog.MSG, showGrantedByDefaultWarning ? R.string.system_warning
                : R.string.old_sdk_deny_warning);
        args.putInt(DefaultDenyDialog.CHANGE_TARGET, changeTarget);

        DefaultDenyDialog defaultDenyDialog = new DefaultDenyDialog();
        defaultDenyDialog.setArguments(args);
        defaultDenyDialog.setTargetFragment(this, 0);
        defaultDenyDialog.show(getFragmentManager().beginTransaction(),
                DefaultDenyDialog.class.getName());
    }

    /**
     * Once we user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @param changeTarget whether to change foreground, background, or both.
     * @see #showDefaultDenyDialog(int)
     */
    void onDenyAnyWay(@ChangeTarget int changeTarget) {
        boolean hasDefaultPermissions = false;
        if ((changeTarget & CHANGE_FOREGROUND) != 0) {
            if (mGroup.areRuntimePermissionsGranted()) {
                SafetyNetLogger.logPermissionToggled(mGroup);
            }

            mGroup.revokeRuntimePermissions(false);
            hasDefaultPermissions = mGroup.hasGrantedByDefaultPermission();
        }
        if ((changeTarget & CHANGE_BACKGROUND) != 0) {
            if (mGroup.getBackgroundPermissions() != null) {
                if (mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                    SafetyNetLogger.logPermissionToggled(mGroup.getBackgroundPermissions());
                }

                mGroup.getBackgroundPermissions().revokeRuntimePermissions(false);
                hasDefaultPermissions |=
                        mGroup.getBackgroundPermissions().hasGrantedByDefaultPermission();
            }
        }

        if (hasDefaultPermissions || !mGroup.doesSupportRuntimePermissions()) {
            mHasConfirmedRevoke = true;
        }
        updateUi();
    }

    /** Preference used to represent apps that can be picked as a default app. */
    private static class SelectedPermissionPreference extends TwoStatePreference {

        SelectedPermissionPreference(Context context) {
            super(context, null, TypedArrayUtils.getAttr(context, R.attr.preferenceStyle,
                    android.R.attr.preferenceStyle));
            setPersistent(false);
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            setSummary(checked ? getContext().getString(R.string.car_permission_selected) : null);
        }
    }

    /**
     * A dialog warning the user that they are about to deny a permission that was granted by
     * default.
     *
     * @see #showDefaultDenyDialog(int)
     */
    public static class DefaultDenyDialog extends DialogFragment {
        private static final String MSG = DefaultDenyDialog.class.getName() + ".arg.msg";
        private static final String CHANGE_TARGET = DefaultDenyDialog.class.getName()
                + ".arg.changeTarget";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AutoAppPermissionFragment fragment = (AutoAppPermissionFragment) getTargetFragment();
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (dialog, which) -> fragment.updateUi())
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (dialog, which) ->
                                    fragment.onDenyAnyWay(getArguments().getInt(CHANGE_TARGET)));

            return b.create();
        }
    }

    /**
     * A listener for permission changes.
     */
    private class PermissionChangeListener implements PackageManager.OnPermissionsChangedListener {
        private final int mUid;

        PermissionChangeListener(int uid) {
            mUid = uid;
        }

        @Override
        public void onPermissionsChanged(int uid) {
            if (uid == mUid) {
                Log.w(LOG_TAG, "Permissions changed.");
                mGroup = getAppPermissionGroup();
                updateUi();
            }
        }
    }
}
