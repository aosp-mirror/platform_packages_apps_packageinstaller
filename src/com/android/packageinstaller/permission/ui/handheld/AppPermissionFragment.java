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

package com.android.packageinstaller.permission.ui.handheld;

import static com.android.packageinstaller.Constants.EXTRA_SESSION_ID;
import static com.android.packageinstaller.Constants.INVALID_SESSION_ID;
import static com.android.packageinstaller.permission.ui.handheld.AppPermissionViewModel.REQUEST_CHANGE_TRUE;
import static com.android.packageinstaller.permission.ui.handheld.AppPermissionViewModel.SHOW_DEFAULT_DENY_DIALOGUE;
import static com.android.packageinstaller.permission.ui.handheld.AppPermissionViewModel.SHOW_LOCATION_DIALOGUE;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.model.PermissionUsages;
import com.android.packageinstaller.permission.ui.AppPermissionActivity;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.widget.ActionBarShadowController;

import java.lang.annotation.Retention;
import java.util.List;

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission.
 */
public class AppPermissionFragment extends SettingsWithLargeHeader {
    private static final String LOG_TAG = "AppPermissionFragment";

    @Retention(SOURCE)
    @IntDef(value = {CHANGE_FOREGROUND, CHANGE_BACKGROUND}, flag = true)
    @interface ChangeTarget {}
    static final int CHANGE_FOREGROUND = 1;
    static final int CHANGE_BACKGROUND = 2;
    static final int CHANGE_BOTH = CHANGE_FOREGROUND | CHANGE_BACKGROUND;

    private @NonNull AppPermissionGroup mGroup;
    private @NonNull AppPermissionViewModel mViewModel;

    private @NonNull RadioGroup mRadioGroup;
    private @NonNull RadioButton mAlwaysButton;
    private @NonNull RadioButton mForegroundOnlyButton;
    private @NonNull RadioButton mDenyButton;
    private @NonNull View mDivider;
    private @NonNull ViewGroup mWidgetFrame;
    private @NonNull TextView mPermissionDetails;
    private @NonNull NestedScrollView mNestedScrollView;

    private boolean mHasConfirmedRevoke;

    /**
     * @return A new fragment
     */
    public static @NonNull AppPermissionFragment newInstance(@NonNull String packageName,
            @NonNull String permName, @Nullable String groupName,
            @NonNull UserHandle userHandle, @Nullable String caller, long sessionId) {
        AppPermissionFragment fragment = new AppPermissionFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putString(AppPermissionActivity.EXTRA_CALLER_NAME, caller);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mHasConfirmedRevoke = false;

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        String groupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (groupName == null) {
            groupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        UserHandle userHandle = getArguments().getParcelable(Intent.EXTRA_USER);
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), packageName, groupName, userHandle, sessionId);
        mViewModel = new ViewModelProvider(this, factory)
                .get(AppPermissionViewModel.class);

        AppPermissionGroup group = mViewModel.getLiveData().getValue();
        if (group == null) {
            Log.i(LOG_TAG, "Illegal group: " + groupName);
            getActivity().setResult(Activity.RESULT_CANCELED);
            getActivity().finish();
            return;
        }
        mGroup = group;
        getActivity().setTitle(
                getPreferenceManager().getContext().getString(R.string.app_permission_title,
                        mGroup.getFullLabel()));
        mViewModel.logAppPermissionFragmentViewed();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getPreferenceManager().getContext();
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.app_permission, container, false);

        if (mGroup == null) {
            return root;
        }

        String appLabel = Utils.getFullAppLabel(mGroup.getApp().applicationInfo, context);
        setHeader(getAppIcon(), appLabel, null, null, false);
        updateHeader(root.requireViewById(R.id.large_header));

        ((TextView) root.requireViewById(R.id.permission_message)).setText(
                context.getString(R.string.app_permission_header, mGroup.getFullLabel()));

        if (!Utils.isPermissionsHubEnabled()) {
            root.requireViewById(R.id.usage_summary).setVisibility(View.GONE);
        } else if (Utils.isModernPermissionGroup(mGroup.getName())) {
            if (!Utils.shouldShowPermissionUsage(mGroup.getName())) {
                ((TextView) root.requireViewById(R.id.usage_summary)).setText(
                        context.getString(R.string.app_permission_footer_not_available));
            } else {
                ((TextView) root.requireViewById(R.id.usage_summary)).setText(
                        getUsageSummary(context, appLabel));
            }
        } else {
            root.requireViewById(R.id.usage_summary).setVisibility(View.GONE);
        }

        long sessionId = getArguments().getLong(EXTRA_SESSION_ID);

        TextView footer1Link = root.requireViewById(R.id.footer_link_1);
        footer1Link.setText(context.getString(R.string.app_permission_footer_app_permissions_link,
                appLabel));
        footer1Link.setOnClickListener((v) -> {
            UserHandle user = UserHandle.getUserHandleForUid(mGroup.getApp().applicationInfo.uid);
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mGroup.getApp().packageName);
            intent.putExtra(EXTRA_SESSION_ID, sessionId);
            intent.putExtra(Intent.EXTRA_USER, user);
            context.startActivity(intent);
        });

        TextView footer2Link = root.requireViewById(R.id.footer_link_2);
        footer2Link.setText(context.getString(R.string.app_permission_footer_permission_apps_link));
        footer2Link.setOnClickListener((v) -> {
            Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS);
            intent.putExtra(Intent.EXTRA_PERMISSION_NAME, mGroup.getName());
            intent.putExtra(EXTRA_SESSION_ID, sessionId);
            context.startActivity(intent);
        });

        String caller = getArguments().getString(AppPermissionActivity.EXTRA_CALLER_NAME);
        if (AppPermissionsFragment.class.getName().equals(caller)) {
            footer1Link.setVisibility(View.GONE);
        } else if (PermissionAppsFragment.class.getName().equals(caller)) {
            footer2Link.setVisibility(View.GONE);
        }

        mRadioGroup = root.requireViewById(R.id.radiogroup);
        mAlwaysButton = root.requireViewById(R.id.allow_radio_button);
        mForegroundOnlyButton = root.requireViewById(R.id.foreground_only_radio_button);
        mDenyButton = root.requireViewById(R.id.deny_radio_button);
        mDivider = root.requireViewById(R.id.two_target_divider);
        mWidgetFrame = root.requireViewById(R.id.widget_frame);
        mPermissionDetails = root.requireViewById(R.id.permission_details);

        mNestedScrollView = root.requireViewById(R.id.nested_scroll_view);

        mViewModel.getLiveData().observe(this, appGroup -> {
            if (appGroup == null) {
                Log.i(LOG_TAG, "AppPermissionGroup package or group invalidated for "
                        + mGroup.getName());
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
            } else {
                mGroup = appGroup;
                updateButtons();
            }
        });

        return root;
    }

    private @NonNull String getUsageSummary(@NonNull Context context, @NonNull String appLabel) {
        String timeDiffStr = Utils.getRelativeLastUsageString(context,
                PermissionUsages.loadLastGroupUsage(context, mGroup));
        int strResId;
        if (timeDiffStr == null) {
            switch (mGroup.getName()) {
                case Manifest.permission_group.ACTIVITY_RECOGNITION:
                    strResId = R.string.app_permission_footer_no_usages_activity_recognition;
                    break;
                case Manifest.permission_group.CALENDAR:
                    strResId = R.string.app_permission_footer_no_usages_calendar;
                    break;
                case Manifest.permission_group.CALL_LOG:
                    strResId = R.string.app_permission_footer_no_usages_call_log;
                    break;
                case Manifest.permission_group.CAMERA:
                    strResId = R.string.app_permission_footer_no_usages_camera;
                    break;
                case Manifest.permission_group.CONTACTS:
                    strResId = R.string.app_permission_footer_no_usages_contacts;
                    break;
                case Manifest.permission_group.LOCATION:
                    strResId = R.string.app_permission_footer_no_usages_location;
                    break;
                case Manifest.permission_group.MICROPHONE:
                    strResId = R.string.app_permission_footer_no_usages_microphone;
                    break;
                case Manifest.permission_group.PHONE:
                    strResId = R.string.app_permission_footer_no_usages_phone;
                    break;
                case Manifest.permission_group.SENSORS:
                    strResId = R.string.app_permission_footer_no_usages_sensors;
                    break;
                case Manifest.permission_group.SMS:
                    strResId = R.string.app_permission_footer_no_usages_sms;
                    break;
                case Manifest.permission_group.STORAGE:
                    strResId = R.string.app_permission_footer_no_usages_storage;
                    break;
                default:
                    return context.getString(R.string.app_permission_footer_no_usages_generic,
                            appLabel, mGroup.getLabel().toString().toLowerCase());
            }
            return context.getString(strResId, appLabel);
        } else {
            switch (mGroup.getName()) {
                case Manifest.permission_group.ACTIVITY_RECOGNITION:
                    strResId = R.string.app_permission_footer_usage_summary_activity_recognition;
                    break;
                case Manifest.permission_group.CALENDAR:
                    strResId = R.string.app_permission_footer_usage_summary_calendar;
                    break;
                case Manifest.permission_group.CALL_LOG:
                    strResId = R.string.app_permission_footer_usage_summary_call_log;
                    break;
                case Manifest.permission_group.CAMERA:
                    strResId = R.string.app_permission_footer_usage_summary_camera;
                    break;
                case Manifest.permission_group.CONTACTS:
                    strResId = R.string.app_permission_footer_usage_summary_contacts;
                    break;
                case Manifest.permission_group.LOCATION:
                    strResId = R.string.app_permission_footer_usage_summary_location;
                    break;
                case Manifest.permission_group.MICROPHONE:
                    strResId = R.string.app_permission_footer_usage_summary_microphone;
                    break;
                case Manifest.permission_group.PHONE:
                    strResId = R.string.app_permission_footer_usage_summary_phone;
                    break;
                case Manifest.permission_group.SENSORS:
                    strResId = R.string.app_permission_footer_usage_summary_sensors;
                    break;
                case Manifest.permission_group.SMS:
                    strResId = R.string.app_permission_footer_usage_summary_sms;
                    break;
                case Manifest.permission_group.STORAGE:
                    strResId = R.string.app_permission_footer_usage_summary_storage;
                    break;
                default:
                    return context.getString(R.string.app_permission_footer_usage_summary_generic,
                            appLabel, mGroup.getLabel().toString().toLowerCase(), timeDiffStr);
            }
            return context.getString(strResId, appLabel, timeDiffStr);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setElevation(0);
        }

        ActionBarShadowController.attachToView(getActivity(), getLifecycle(), mNestedScrollView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateButtons() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        // Reset everything to the "default" state: tri-state buttons are shown with exactly one
        // selected and no special messages.
        mDivider.setVisibility(View.GONE);
        mWidgetFrame.setVisibility(View.GONE);
        mPermissionDetails.setVisibility(View.GONE);

        if (mGroup.areRuntimePermissionsGranted()) {
            if (!mGroup.hasPermissionWithBackgroundMode()
                    || (mGroup.getBackgroundPermissions() != null
                    && mGroup.getBackgroundPermissions().areRuntimePermissionsGranted())) {
                setCheckedButton(mAlwaysButton);
            } else {
                setCheckedButton(mForegroundOnlyButton);
            }
        } else {
            setCheckedButton(mDenyButton);
        }

        mAlwaysButton.setOnClickListener((v) -> requestChange(true, CHANGE_BOTH));
        mForegroundOnlyButton.setOnClickListener((v) -> {
            requestChange(false, CHANGE_BACKGROUND);
            requestChange(true, CHANGE_FOREGROUND);
        });
        mDenyButton.setOnClickListener((v) -> requestChange(false, CHANGE_BOTH));

        // Set the allow and foreground-only button states appropriately.
        if (mGroup.hasPermissionWithBackgroundMode()) {
            if (mGroup.getBackgroundPermissions() == null) {
                mAlwaysButton.setVisibility(View.GONE);
            } else {
                mForegroundOnlyButton.setVisibility(View.VISIBLE);
                mAlwaysButton.setText(
                        context.getString(R.string.app_permission_button_allow_always));
            }
        } else {
            mForegroundOnlyButton.setVisibility(View.GONE);
            mAlwaysButton.setText(context.getString(R.string.app_permission_button_allow));
        }

        // Handle the UI for various special cases.
        if (isSystemFixed() || isPolicyFullyFixed() || isForegroundDisabledByPolicy()) {
            // Disable changing permissions and potentially show administrator message.
            mAlwaysButton.setEnabled(false);
            mForegroundOnlyButton.setEnabled(false);
            mDenyButton.setEnabled(false);

            EnforcedAdmin admin = getAdmin();
            if (admin != null) {
                showRightIcon(R.drawable.ic_info);
                mWidgetFrame.setOnClickListener(v ->
                        RestrictedLockUtils.sendShowAdminSupportDetailsIntent(context, admin)
                );
            }

            updateDetailForFixedByPolicyPermissionGroup();
        } else if (Utils.areGroupPermissionsIndividuallyControlled(context, mGroup.getName())) {
            // If the permissions are individually controlled, also show a link to the page that
            // lets you control them.
            mDivider.setVisibility(View.VISIBLE);
            showRightIcon(R.drawable.ic_settings);
            mWidgetFrame.setOnClickListener(v -> showAllPermissions(mGroup.getName()));

            updateDetailForIndividuallyControlledPermissionGroup();
        } else {
            if (mGroup.hasPermissionWithBackgroundMode()) {
                if (mGroup.getBackgroundPermissions() == null) {
                    // The group has background permissions but the app did not request any. I.e.
                    // The app can only switch between 'never" and "only in foreground".
                    mAlwaysButton.setEnabled(false);

                    mDenyButton.setOnClickListener((v) -> requestChange(false, CHANGE_FOREGROUND));
                } else {
                    if (isBackgroundPolicyFixed()) {
                        // If background policy is fixed, we only allow switching the foreground.
                        // Note that this assumes that the background policy is fixed to deny,
                        // since if it is fixed to grant, so is the foreground.
                        mAlwaysButton.setEnabled(false);
                        setCheckedButton(mForegroundOnlyButton);

                        mDenyButton.setOnClickListener(
                                (v) -> requestChange(false, CHANGE_FOREGROUND));

                        updateDetailForFixedByPolicyPermissionGroup();
                    } else if (isForegroundPolicyFixed()) {
                        // Foreground permissions are fixed to allow (the first case above handles
                        // fixing to deny), so we only allow toggling background permissions.
                        mDenyButton.setEnabled(false);

                        mAlwaysButton.setOnClickListener(
                                (v) -> requestChange(true, CHANGE_BACKGROUND));
                        mForegroundOnlyButton.setOnClickListener(
                                (v) -> requestChange(false, CHANGE_BACKGROUND));

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
     * Set the given button as the only checked button in the radio group.
     *
     * @param button the button that should be checked.
     */
    private void setCheckedButton(@NonNull RadioButton button) {
        mRadioGroup.clearCheck();
        button.setChecked(true);
        if (button != mAlwaysButton) {
            mAlwaysButton.setChecked(false);
        }
        if (button != mForegroundOnlyButton) {
            mForegroundOnlyButton.setChecked(false);
        }
        if (button != mDenyButton) {
            mDenyButton.setChecked(false);
        }
    }

    /**
     * Show the given icon on the right of the first radio button.
     *
     * @param iconId the resourceId of the drawable to use.
     */
    private void showRightIcon(int iconId) {
        mWidgetFrame.removeAllViews();
        ImageView imageView = new ImageView(getPreferenceManager().getContext());
        imageView.setImageResource(iconId);
        mWidgetFrame.addView(imageView);
        mWidgetFrame.setVisibility(View.VISIBLE);
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
    private @Nullable EnforcedAdmin getAdmin() {
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

        mPermissionDetails.setText(getContext().getString(resId, revokedCount));
        mPermissionDetails.setVisibility(View.VISIBLE);
    }

    /**
     * Update the detail of a permission group that is at least partially fixed by policy.
     */
    private void updateDetailForFixedByPolicyPermissionGroup() {
        EnforcedAdmin admin = getAdmin();
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
     * Show the given string as informative text below the radio buttons.
     *
     * @param strId the resourceId of the string to display.
     */
    private void setDetail(int strId) {
        mPermissionDetails.setText(getPreferenceManager().getContext().getString(strId));
        mPermissionDetails.setVisibility(View.VISIBLE);
    }

    /**
     * Show all individual permissions in this group in a new fragment.
     */
    private void showAllPermissions(@NonNull String filterGroup) {
        Fragment frag = AllAppPermissionsFragment.newInstance(mGroup.getApp().packageName,
                filterGroup, UserHandle.getUserHandleForUid(mGroup.getApp().applicationInfo.uid));
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    /**
     * Get the icon of this app.
     *
     * @return the app's icon.
     */
    private @NonNull Drawable getAppIcon() {
        ApplicationInfo appInfo = mGroup.getApp().applicationInfo;
        return Utils.getBadgedIcon(getActivity(), appInfo);
    }

    /**
     * Requests that the ViewModel change a permission, and displays a dialogue, if needed. See
     * {@link AppPermissionViewModel#requestChange}
     *
     * @param requestGrant If this group should be granted
     * @param changeTarget Which permission group (foreground/background/both) should be changed
     *
     * @return If the request was processed
     */
    private boolean requestChange(boolean requestGrant, @ChangeTarget int changeTarget) {
        int action = mViewModel.requestChange(requestGrant, getContext(), changeTarget);
        switch (action) {
            case SHOW_LOCATION_DIALOGUE:
                LocationUtils.showLocationDialog(getContext(),
                        Utils.getAppLabel(mGroup.getApp().applicationInfo, getContext()));
                break;
            case SHOW_DEFAULT_DENY_DIALOGUE:
                showDefaultDenyDialog(changeTarget);
                break;
            case REQUEST_CHANGE_TRUE:
                return true;
        }
        return false;
    }

    /**
     * Show a dialog that warns the user that she/he is about to revoke permissions that were
     * granted by default.
     *
     * <p>The order of operation to revoke a permission granted by default is:
     * <ol>
     *     <li>{@code showDefaultDenyDialog}</li>
     *     <li>{@link DefaultDenyDialog#onCreateDialog}</li>
     *     <li>{@link AppPermissionViewModel#onDenyAnyWay}</li>
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
     * A dialog warning the user that she/he is about to deny a permission that was granted by
     * default.
     *
     * @see #showDefaultDenyDialog(int)
     */
    public static class DefaultDenyDialog extends DialogFragment {
        private static final String MSG = DefaultDenyDialog.class.getName() + ".arg.msg";
        private static final String CHANGE_TARGET = DefaultDenyDialog.class.getName()
                + ".arg.changeTarget";
        private static final String KEY = DefaultDenyDialog.class.getName() + ".arg.key";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AppPermissionFragment fragment = (AppPermissionFragment) getTargetFragment();
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface dialog, int which) -> fragment.updateButtons())
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (DialogInterface dialog, int which) ->
                                    fragment.mViewModel
                                            .onDenyAnyWay(getArguments().getInt(CHANGE_TARGET)));

            return b.create();
        }
    }
}
