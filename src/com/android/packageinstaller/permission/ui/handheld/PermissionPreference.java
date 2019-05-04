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

import static com.android.packageinstaller.permission.utils.Utils.DEFAULT_MAX_LABEL_SIZE_PX;
import static com.android.packageinstaller.permission.utils.Utils.getRequestMessage;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.Switch;

import androidx.annotation.IntDef;
import androidx.annotation.LayoutRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;

import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import java.lang.annotation.Retention;
import java.util.List;

/**
 * A preference for representing a permission group requested by an app.
 */
class PermissionPreference extends MultiTargetSwitchPreference {
    @Retention(SOURCE)
    @IntDef(value = {CHANGE_FOREGROUND, CHANGE_BACKGROUND}, flag = true)
    @interface ChangeTarget {}
    static final int CHANGE_FOREGROUND = 1;
    static final int CHANGE_BACKGROUND = 2;
    static final int CHANGE_BOTH = CHANGE_FOREGROUND | CHANGE_BACKGROUND;

    private final AppPermissionGroup mGroup;
    private final PreferenceFragmentCompat mFragment;
    private final PermissionPreferenceChangeListener mCallBacks;
    private final @LayoutRes int mOriginalWidgetLayoutRes;
    private final int mIconSize;

    /** Callbacks for the permission to the fragment showing a list of permissions */
    interface PermissionPreferenceChangeListener {
        /**
         * Checks if the user has to confirm a revocation of a permission granted by default.
         *
         * @return {@code true} iff the user has to confirm it
         */
        boolean shouldConfirmDefaultPermissionRevoke();

        /**
         * Notify the listener that the user confirmed that she/he wants to revoke permissions that
         * were granted by default.
         */
        void hasConfirmDefaultPermissionRevoke();

        /**
         * Notify the listener that this preference has changed.
         *
         * @param key The key uniquely identifying this preference
         */
        void onPreferenceChanged(String key);
    }

    /**
     * Callbacks from dialogs to the fragment. These callbacks are supposed to directly cycle back
     * to the permission tha created the dialog.
     */
    interface PermissionPreferenceOwnerFragment {
        /**
         * The {@link DefaultDenyDialog} can only interact with the fragment, not the preference
         * that created it. Hence this call goes to the fragment, which then finds the preference an
         * calls {@link #onDenyAnyWay(int)}.
         *
         * @param key Key uniquely identifying the preference that created the default deny dialog
         * @param changeTarget Whether background or foreground permissions should be changed
         *
         * @see #showDefaultDenyDialog(int)
         */
        void onDenyAnyWay(String key, @ChangeTarget int changeTarget);

        /**
         * The {@link BackgroundAccessChooser} can only interact with the fragment, not the
         * preference that created it. Hence this call goes to the fragment, which then finds the
         * preference an calls {@link #onBackgroundAccessChosen(int)}}.
         *
         * @param key Key uniquely identifying the preference that created the background access
         *            chooser
         * @param chosenItem The index of the item selected by the user.
         *
         * @see #showBackgroundChooserDialog()
         */
        void onBackgroundAccessChosen(String key, int chosenItem);
    }

    PermissionPreference(PreferenceFragmentCompat fragment, AppPermissionGroup group,
            PermissionPreferenceChangeListener callbacks, int iconSize) {
        super(fragment.getPreferenceManager().getContext());

        mFragment = fragment;
        mGroup = group;
        mCallBacks = callbacks;
        mOriginalWidgetLayoutRes = getWidgetLayoutResource();
        mIconSize = iconSize;

        setPersistent(false);
        updateUi();
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
    private EnforcedAdmin getAdmin() {
        return RestrictedLockUtils.getProfileOrDeviceOwner(getContext(), mGroup.getUser());
    }

    /**
     * Update the preference after the state might have changed.
     */
    void updateUi() {
        boolean arePermissionsIndividuallyControlled =
                Utils.areGroupPermissionsIndividuallyControlled(getContext(), mGroup.getName());
        EnforcedAdmin admin = getAdmin();

        // Reset ui state
        setEnabled(true);
        setWidgetLayoutResource(mOriginalWidgetLayoutRes);
        setOnPreferenceClickListener(null);
        setSwitchOnClickListener(null);
        setSummary(null);

        setChecked(mGroup.areRuntimePermissionsGranted());

        if (isSystemFixed() || isPolicyFullyFixed() || isForegroundDisabledByPolicy()) {
            if (admin != null) {
                setWidgetLayoutResource(R.layout.restricted_icon);

                setOnPreferenceClickListener((v) -> {
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin);
                    return true;
                });
            } else {
                setEnabled(false);
            }

            updateSummaryForFixedByPolicyPermissionGroup();
        } else if (arePermissionsIndividuallyControlled) {
            setOnPreferenceClickListener((pref) -> {
                showAllPermissions(mGroup.getName());
                return false;
            });

            setSwitchOnClickListener(v -> {
                Switch switchView = (Switch) v;
                requestChange(switchView.isChecked(), CHANGE_BOTH);

                // Update UI as the switch widget might be in wrong state
                updateUi();
            });

            updateSummaryForIndividuallyControlledPermissionGroup();
        } else {
            if (mGroup.hasPermissionWithBackgroundMode()) {
                if (mGroup.getBackgroundPermissions() == null) {
                    // The group has background permissions but the app did not request any. I.e.
                    // The app can only switch between 'never" and "only in foreground".
                    setOnPreferenceChangeListener((pref, newValue) ->
                            requestChange((Boolean) newValue, CHANGE_FOREGROUND));

                    updateSummaryForPermissionGroupWithBackgroundPermission();
                } else {
                    if (isBackgroundPolicyFixed()) {
                        setOnPreferenceChangeListener((pref, newValue) ->
                                requestChange((Boolean) newValue, CHANGE_FOREGROUND));

                        updateSummaryForFixedByPolicyPermissionGroup();
                    } else if (isForegroundPolicyFixed()) {
                        setOnPreferenceChangeListener((pref, newValue) ->
                                requestChange((Boolean) newValue, CHANGE_BACKGROUND));

                        updateSummaryForFixedByPolicyPermissionGroup();
                    } else {
                        updateSummaryForPermissionGroupWithBackgroundPermission();

                        setOnPreferenceClickListener((pref) -> {
                            showBackgroundChooserDialog();
                            return true;
                        });

                        setSwitchOnClickListener(v -> {
                            Switch switchView = (Switch) v;

                            if (switchView.isChecked()) {
                                showBackgroundChooserDialog();
                            } else {
                                requestChange(false, CHANGE_BOTH);
                            }

                            // Update UI as the switch widget might be in wrong state
                            updateUi();
                        });
                    }
                }
            } else {
                setOnPreferenceChangeListener((pref, newValue) ->
                        requestChange((Boolean) newValue, CHANGE_BOTH));
            }
        }
    }

    /**
     * Update the summary in the case the permission group has individually controlled permissions.
     */
    private void updateSummaryForIndividuallyControlledPermissionGroup() {
        int revokedCount = 0;
        List<Permission> permissions = mGroup.getPermissions();
        final int permissionCount = permissions.size();
        for (int i = 0; i < permissionCount; i++) {
            Permission permission = permissions.get(i);
            if (!permission.isGrantedIncludingAppOp()) {
                revokedCount++;
            }
        }

        final int resId;
        if (revokedCount == 0) {
            resId = R.string.permission_revoked_none;
        } else if (revokedCount == permissionCount) {
            resId = R.string.permission_revoked_all;
        } else {
            resId = R.string.permission_revoked_count;
        }

        String summary = getContext().getString(resId, revokedCount);
        setSummary(summary);
    }

    /**
     * Update the summary of a permission group that has background permission.
     *
     * <p>This does not apply to permission groups that are fixed by policy</p>
     */
    private void updateSummaryForPermissionGroupWithBackgroundPermission() {
        AppPermissionGroup backgroundGroup = mGroup.getBackgroundPermissions();

        if (mGroup.areRuntimePermissionsGranted()) {
            if (backgroundGroup == null) {
                setSummary(R.string.permission_access_only_foreground);
            } else {
                if (backgroundGroup.areRuntimePermissionsGranted()) {
                    setSummary(R.string.permission_access_always);
                } else {
                    setSummary(R.string.permission_access_only_foreground);
                }
            }
        } else {
            setSummary(R.string.permission_access_never);
        }
    }

    /**
     * Update the summary of a permission group that is at least partially fixed by policy.
     */
    private void updateSummaryForFixedByPolicyPermissionGroup() {
        EnforcedAdmin admin = getAdmin();
        AppPermissionGroup backgroundGroup = mGroup.getBackgroundPermissions();

        boolean hasAdmin = admin != null;

        if (isSystemFixed()) {
            // Permission is fully controlled by the system and cannot be switched

            setSummary(R.string.permission_summary_enabled_system_fixed);
        } else if (isForegroundDisabledByPolicy()) {
            // Permission is fully controlled by policy and cannot be switched

            if (hasAdmin) {
                setSummary(R.string.disabled_by_admin);
            } else {
                // Disabled state will be displayed by switch, so no need to add text for that
                setSummary(R.string.permission_summary_enforced_by_policy);
            }
        } else if (isPolicyFullyFixed()) {
            // Permission is fully controlled by policy and cannot be switched

            if (backgroundGroup == null) {
                if (hasAdmin) {
                    setSummary(R.string.enabled_by_admin);
                } else {
                    // Enabled state will be displayed by switch, so no need to add text for
                    // that
                    setSummary(R.string.permission_summary_enforced_by_policy);
                }
            } else {
                if (backgroundGroup.areRuntimePermissionsGranted()) {
                    if (hasAdmin) {
                        setSummary(R.string.enabled_by_admin);
                    } else {
                        // Enabled state will be displayed by switch, so no need to add text for
                        // that
                        setSummary(R.string.permission_summary_enforced_by_policy);
                    }
                } else {
                    if (hasAdmin) {
                        setSummary(
                                R.string.permission_summary_enabled_by_admin_foreground_only);
                    } else {
                        setSummary(
                                R.string.permission_summary_enabled_by_policy_foreground_only);
                    }
                }
            }
        } else {
            // Part of the permission group can still be switched

            if (isBackgroundPolicyFixed()) {
                if (backgroundGroup.areRuntimePermissionsGranted()) {
                    if (hasAdmin) {
                        setSummary(R.string.permission_summary_enabled_by_admin_background_only);
                    } else {
                        setSummary(R.string.permission_summary_enabled_by_policy_background_only);
                    }
                } else {
                    if (hasAdmin) {
                        setSummary(R.string.permission_summary_disabled_by_admin_background_only);
                    } else {
                        setSummary(R.string.permission_summary_disabled_by_policy_background_only);
                    }
                }
            } else if (isForegroundPolicyFixed()) {
                if (hasAdmin) {
                    setSummary(R.string.permission_summary_enabled_by_admin_foreground_only);
                } else {
                    setSummary(R.string.permission_summary_enabled_by_policy_foreground_only);
                }
            }
        }
    }

    /**
     * Show all individual permissions in this group in a new fragment.
     */
    private void showAllPermissions(String filterGroup) {
        Fragment frag = AllAppPermissionsFragment.newInstance(mGroup.getApp().packageName,
                filterGroup, UserHandle.getUserHandleForUid(mGroup.getApp().applicationInfo.uid));
        mFragment.getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    /**
     * Get the label of the app the permission group belongs to. (App permission groups are all
     * permissions of a group an app has requested.)
     *
     * @return The label of the app
     */
    private String getAppLabel() {
        return BidiFormatter.getInstance().unicodeWrap(
                mGroup.getApp().applicationInfo.loadSafeLabel(getContext().getPackageManager(),
                        DEFAULT_MAX_LABEL_SIZE_PX,
                        TextUtils.SAFE_STRING_FLAG_TRIM
                                | TextUtils.SAFE_STRING_FLAG_FIRST_LINE)
                        .toString());
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (mIconSize > 0) {
            ImageView icon = ((ImageView) holder.findViewById(android.R.id.icon));

            icon.setMaxWidth(mIconSize);
            icon.setMaxHeight(mIconSize);
        }

        super.onBindViewHolder(holder);
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
            LocationUtils.showLocationDialog(getContext(), getAppLabel());
            return false;
        }
        if (requestGrant) {
            mCallBacks.onPreferenceChanged(getKey());

            if ((changeTarget & CHANGE_FOREGROUND) != 0) {
                mGroup.grantRuntimePermissions(false);
            }
            if ((changeTarget & CHANGE_BACKGROUND) != 0) {
                if (mGroup.getBackgroundPermissions() != null) {
                    mGroup.getBackgroundPermissions().grantRuntimePermissions(false);
                }
            }
        } else {
            boolean requestToRevokeGrantedByDefault = false;
            if ((changeTarget & CHANGE_FOREGROUND) != 0) {
                requestToRevokeGrantedByDefault = mGroup.hasGrantedByDefaultPermission();
            }
            if ((changeTarget & CHANGE_BACKGROUND) != 0) {
                if (mGroup.getBackgroundPermissions() != null) {
                    requestToRevokeGrantedByDefault |=
                            mGroup.getBackgroundPermissions().hasGrantedByDefaultPermission();
                }
            }

            if ((requestToRevokeGrantedByDefault || !mGroup.doesSupportRuntimePermissions())
                    && mCallBacks.shouldConfirmDefaultPermissionRevoke()) {
                showDefaultDenyDialog(changeTarget);
                return false;
            } else {
                mCallBacks.onPreferenceChanged(getKey());

                if ((changeTarget & CHANGE_FOREGROUND) != 0) {
                    mGroup.revokeRuntimePermissions(false);
                }
                if ((changeTarget & CHANGE_BACKGROUND) != 0) {
                    if (mGroup.getBackgroundPermissions() != null) {
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
     *     <li>{@code showDefaultDenyDialog}</li>
     *     <li>{@link DefaultDenyDialog#onCreateDialog}</li>
     *     <li>{@link PermissionPreferenceOwnerFragment#onDenyAnyWay}</li>
     *     <li>{@link PermissionPreference#onDenyAnyWay}</li>
     * </ol>
     *
     * @param changeTarget Whether background or foreground should be changed
     */
    private void showDefaultDenyDialog(@ChangeTarget int changeTarget) {
        if (!mFragment.isResumed()) {
            return;
        }

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
        args.putString(DefaultDenyDialog.KEY, getKey());
        args.putInt(DefaultDenyDialog.CHANGE_TARGET, changeTarget);

        DefaultDenyDialog deaultDenyDialog = new DefaultDenyDialog();
        deaultDenyDialog.setArguments(args);
        deaultDenyDialog.show(mFragment.getChildFragmentManager().beginTransaction(),
                "denyDefault");
    }

    /**
     * Show a dialog that asks the user if foreground/background/none access should be enabled.
     *
     * <p>The order of operation to grant foreground/background/none access is:
     * <ol>
     *     <li>{@code showBackgroundChooserDialog}</li>
     *     <li>{@link BackgroundAccessChooser#onCreateDialog}</li>
     *     <li>{@link PermissionPreferenceOwnerFragment#onBackgroundAccessChosen}</li>
     *     <li>{@link PermissionPreference#onBackgroundAccessChosen}</li>
     * </ol>
     */
    private void showBackgroundChooserDialog() {
        if (!mFragment.isResumed()) {
            return;
        }

        if (LocationUtils.isLocationGroupAndProvider(getContext(), mGroup.getName(),
                mGroup.getApp().packageName)) {
            LocationUtils.showLocationDialog(getContext(), getAppLabel());
            return;
        }

        Bundle args = new Bundle();
        args.putCharSequence(BackgroundAccessChooser.TITLE,
                getRequestMessage(getAppLabel(), mGroup, getContext(), mGroup.getRequest()));
        args.putString(BackgroundAccessChooser.KEY, getKey());


        if (mGroup.areRuntimePermissionsGranted()) {
            if (mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                args.putInt(BackgroundAccessChooser.SELECTION,
                        BackgroundAccessChooser.ALWAYS_OPTION);
            } else {
                args.putInt(BackgroundAccessChooser.SELECTION,
                        BackgroundAccessChooser.FOREGROUND_ONLY_OPTION);
            }
        } else {
            args.putInt(BackgroundAccessChooser.SELECTION, BackgroundAccessChooser.NEVER_OPTION);
        }

        BackgroundAccessChooser chooserDialog = new BackgroundAccessChooser();
        chooserDialog.setArguments(args);
        chooserDialog.show(mFragment.getChildFragmentManager().beginTransaction(),
                "backgroundChooser");
    }

    /**
     * Once we user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @see #showDefaultDenyDialog(int)
     */
    void onDenyAnyWay(@ChangeTarget int changeTarget) {
        mCallBacks.onPreferenceChanged(getKey());

        boolean hasDefaultPermissions = false;
        if ((changeTarget & CHANGE_FOREGROUND) != 0) {
            mGroup.revokeRuntimePermissions(false);
            hasDefaultPermissions = mGroup.hasGrantedByDefaultPermission();
        }
        if ((changeTarget & CHANGE_BACKGROUND) != 0) {
            if (mGroup.getBackgroundPermissions() != null) {
                mGroup.getBackgroundPermissions().revokeRuntimePermissions(false);
                hasDefaultPermissions |=
                        mGroup.getBackgroundPermissions().hasGrantedByDefaultPermission();
            }
        }

        if (hasDefaultPermissions || !mGroup.doesSupportRuntimePermissions()) {
            mCallBacks.hasConfirmDefaultPermissionRevoke();
        }
        updateUi();
    }

    /**
     * Process the return from a {@link BackgroundAccessChooser} dialog.
     *
     * <p>These dialog are started when the user want to grant a permission group that has
     * background permissions.
     *
     * @param choosenItem The item that the user chose
     */
    void onBackgroundAccessChosen(int choosenItem) {
        AppPermissionGroup backgroundGroup = mGroup.getBackgroundPermissions();

        switch (choosenItem) {
            case BackgroundAccessChooser.ALWAYS_OPTION:
                requestChange(true, CHANGE_BOTH);
                break;
            case BackgroundAccessChooser.FOREGROUND_ONLY_OPTION:
                if (backgroundGroup.areRuntimePermissionsGranted()) {
                    requestChange(false, CHANGE_BACKGROUND);
                }
                requestChange(true, CHANGE_FOREGROUND);
                break;
            case BackgroundAccessChooser.NEVER_OPTION:
                if (mGroup.areRuntimePermissionsGranted()
                        || mGroup.getBackgroundPermissions().areRuntimePermissionsGranted()) {
                    requestChange(false, CHANGE_BOTH);
                }
                break;
        }
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
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (DialogInterface dialog, int which) -> (
                                    (PermissionPreferenceOwnerFragment) getParentFragment())
                                    .onDenyAnyWay(getArguments().getString(KEY),
                                            getArguments().getInt(CHANGE_TARGET)));

            return b.create();
        }
    }

    /**
     * If a permission group has background permission this chooser is used to let the user
     * choose how the permission group should be granted.
     *
     * @see #showBackgroundChooserDialog()
     */
    public static class BackgroundAccessChooser extends DialogFragment {
        private static final String TITLE = BackgroundAccessChooser.class.getName() + ".arg.title";
        private static final String KEY = BackgroundAccessChooser.class.getName() + ".arg.key";
        private static final String SELECTION = BackgroundAccessChooser.class.getName()
                + ".arg.selection";

        // Needs to match the entries in R.array.background_access_chooser_dialog_choices
        static final int ALWAYS_OPTION = 0;
        static final int FOREGROUND_ONLY_OPTION = 1;
        static final int NEVER_OPTION = 2;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(getArguments().getCharSequence(TITLE))
                    .setSingleChoiceItems(R.array.background_access_chooser_dialog_choices,
                            getArguments().getInt(SELECTION),
                            (dialog, which) -> {
                                dismissAllowingStateLoss();
                                ((PermissionPreferenceOwnerFragment) getParentFragment())
                                        .onBackgroundAccessChosen(getArguments().getString(KEY),
                                                which);
                            }
                    );

            return b.create();
        }
    }
}
