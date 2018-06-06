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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.pm.PackageItemInfo;
import android.os.Bundle;
import android.text.BidiFormatter;
import android.widget.Switch;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.utils.LocationUtils;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.settingslib.RestrictedLockUtils;

import java.util.List;

/**
 * A preference for representing a permission group requested by an app.
 */
class PermissionPreference extends RestrictedSwitchPreference {
    private final AppPermissionGroup mGroup;
    private final Fragment mFragment;
    private final PermissionPreferenceChangeListener mCallBacks;

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
         * calls {@link #onDenyAnyWay()}.
         *
         * @param key Key uniquely identifying the preference that created the default deny dialog
         *
         * @see #showDefaultDenyDialog()
         */
        void onDenyAnyWay(String key);
    }

    /**
     * Create a new preference for a single permission group.
     *
     * @param fragment Fragment containing the preference
     * @param group The permission group that is managed by this preference
     * @param callbacks Callbacks used by this preference to query / update per fragment state
     */
    PermissionPreference(Fragment fragment, AppPermissionGroup group,
            PermissionPreferenceChangeListener callbacks) {
        super(fragment.getContext());

        mFragment = fragment;
        mGroup = group;
        mCallBacks = callbacks;

        setPersistent(false);
        updateUi();
    }

    /**
     * Update the preference after the state might have changed.
     */
    void updateUi() {
        boolean arePermissionsIndividuallyControlled =
                Utils.areGroupPermissionsIndividuallyControlled(getContext(), mGroup.getName());
        RestrictedLockUtils.EnforcedAdmin admin =
                RestrictedLockUtils.getProfileOrDeviceOwner(getContext(), mGroup.getUserId());
        boolean isPolicyFixed = mGroup.isPolicyFixed();
        boolean isAdminFixed = admin != null;
        boolean isSystemFixed = isPolicyFixed && !isAdminFixed;

        // Reset ui state
        setDisabledByAdmin(null);
        setEnabled(true);
        setOnPreferenceClickListener(null);
        setSwitchOnClickListener(null);
        setSummary(null);

        setChecked(mGroup.areRuntimePermissionsGranted());

        if (isAdminFixed) {
            setDisabledByAdmin(admin);
            setSummary(R.string.disabled_by_admin_summary_text);
            setEnabled(false);
        } else if (isSystemFixed) {
            // Both foreground and background filed
            setSummary(R.string.permission_summary_enforced_by_policy);
            setEnabled(false);
        } else if (arePermissionsIndividuallyControlled) {
            setOnPreferenceClickListener((pref) -> {
                showAllPermissions();
                return false;
            });

            setSwitchOnClickListener(v -> {
                Switch switchView = (Switch) v;
                requestChange(switchView.isChecked());
            });

            updateSummaryForIndividuallyControlledPermissionGroup();
        } else {
            setOnPreferenceChangeListener((pref, newValue) ->
                    requestChange((Boolean) newValue));
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
            if (mGroup.doesSupportRuntimePermissions()
                    ? !permission.isGranted() : (!permission.isAppOpAllowed()
                    || permission.isReviewRequired())) {
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
     * Show all individual permissions in this group in a new fragment.
     */
    private void showAllPermissions() {
        Fragment frag = AllAppPermissionsFragment.newInstance(mGroup.getApp().packageName,
                mGroup.getName());
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
                        PackageItemInfo.DEFAULT_MAX_LABEL_SIZE_PX,
                        PackageItemInfo.SAFE_LABEL_FLAG_TRIM
                                | PackageItemInfo.SAFE_LABEL_FLAG_FIRST_LINE)
                        .toString());
    }

    /**
     * Request to grant/revoke permissions group.
     *
     * <p>Does <u>not</u> handle:
     * <ul>
     * <li>Individually granted permissions</li>
     * </ul>
     * <p><u>Does</u> handle:
     * <ul>
     * <li>Default grant permissions</li>
     * </ul>
     *
     * @param requestGrant If this group should be granted
     * @return If the request was processed.
     */
    private boolean requestChange(boolean requestGrant) {
        if (LocationUtils.isLocationGroupAndProvider(mGroup.getName(),
                mGroup.getApp().packageName)) {
            LocationUtils.showLocationDialog(getContext(), getAppLabel());
            return false;
        }
        if (requestGrant) {
            mCallBacks.onPreferenceChanged(getKey());

            mGroup.grantRuntimePermissions(false);
        } else {
            boolean requestToRevokeGrantedByDefault = mGroup.hasGrantedByDefaultPermission();

            if ((requestToRevokeGrantedByDefault || !mGroup.doesSupportRuntimePermissions())
                    && mCallBacks.shouldConfirmDefaultPermissionRevoke()) {
                showDefaultDenyDialog();
                return false;
            } else {
                mCallBacks.onPreferenceChanged(getKey());

                mGroup.revokeRuntimePermissions(false);
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
     */
    private void showDefaultDenyDialog() {
        Bundle args = new Bundle();

        boolean showGrantedByDefaultWarning = mGroup.hasGrantedByDefaultPermission();

        args.putInt(DefaultDenyDialog.MSG, showGrantedByDefaultWarning ? R.string.system_warning
                : R.string.old_sdk_deny_warning);
        args.putString(DefaultDenyDialog.KEY, getKey());

        DefaultDenyDialog deaultDenyDialog = new DefaultDenyDialog();
        deaultDenyDialog.setArguments(args);
        deaultDenyDialog.show(mFragment.getChildFragmentManager().beginTransaction(),
                "denyDefault");
    }

    /**
     * Once we user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @see #showDefaultDenyDialog()
     */
    void onDenyAnyWay() {
        mCallBacks.onPreferenceChanged(getKey());

        boolean hasDefaultPermissions = mGroup.hasGrantedByDefaultPermission();
        mGroup.revokeRuntimePermissions(false);

        if (hasDefaultPermissions) {
            mCallBacks.hasConfirmDefaultPermissionRevoke();
        }
        updateUi();
    }

    /**
     * A dialog warning the user that she/he is about to deny a permission that was granted by
     * default.
     *
     * @see #showDefaultDenyDialog()
     */
    public static class DefaultDenyDialog extends DialogFragment {
        private static final String MSG = DefaultDenyDialog.class.getName() + ".arg.msg";
        private static final String KEY = DefaultDenyDialog.class.getName() + ".arg.key";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (DialogInterface dialog, int which) -> (
                                    (PermissionPreferenceOwnerFragment) getParentFragment())
                                    .onDenyAnyWay(getArguments().getString(KEY)));

            return b.create();
        }
    }
}
