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

package com.android.packageinstaller.permission.ui;

import android.app.Activity;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toolbar;
import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.permission.ui.ConfirmActionDialogFragment.OnActionConfirmedListener;

import java.util.List;

public final class ReviewPermissionsActivity extends Activity
        implements OnActionConfirmedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageInfo packageInfo = getTargetPackageInfo();
        if (packageInfo == null) {
            finish();
            return;
        }

        setContentView(R.layout.review_permissions);
        if (getFragmentManager().findFragmentById(R.id.preferences_frame) == null) {
            getFragmentManager().beginTransaction().add(R.id.preferences_frame,
                    ReviewPermissionsFragment.newInstance(packageInfo)).commit();
        }
    }

    @Override
    public void onActionConfirmed(String action) {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.preferences_frame);
        if (fragment instanceof OnActionConfirmedListener) {
            ((OnActionConfirmedListener) fragment).onActionConfirmed(action);
        }
    }

    private PackageInfo getTargetPackageInfo() {
        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            return getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static final class ReviewPermissionsFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener,
            ConfirmActionDialogFragment.OnActionConfirmedListener {
        public static final String EXTRA_PACKAGE_INFO =
                "com.android.packageinstaller.permission.ui.extra.PACKAGE_INFO";

        private AppPermissions mAppPermissions;

        private PreferenceCategory mNewPermissionsCategory;

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

            mAppPermissions = new AppPermissions(activity, packageInfo, null, false,
                    new Runnable() {
                        @Override
                        public void run() {
                            getActivity().finish();
                        }
                    });

            if (mAppPermissions.getPermissionGroups().isEmpty()) {
                activity.finish();
                return;
            }

            boolean reviewRequired = false;
            for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
                if (group.isReviewRequired()) {
                    reviewRequired = true;
                    break;
                }
            }

            if (!reviewRequired) {
                activity.finish();
            }
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
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
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (mHasConfirmedRevoke) {
                return true;
            }
            if (preference instanceof SwitchPreference) {
                SwitchPreference switchPreference = (SwitchPreference) preference;
                if (switchPreference.isChecked()) {
                    showWarnRevokeDialog(switchPreference.getKey());
                } else {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onActionConfirmed(String action) {
            Preference preference = getPreferenceManager().findPreference(action);
            if (preference instanceof SwitchPreference) {
                SwitchPreference switchPreference = (SwitchPreference) preference;
                switchPreference.setChecked(false);
                mHasConfirmedRevoke = true;
            }
        }

        private void showWarnRevokeDialog(final String groupName) {
            DialogFragment fragment = ConfirmActionDialogFragment.newInstance(
                    getString(R.string.old_sdk_deny_warning), groupName);
            fragment.show(getFragmentManager(), fragment.getClass().getName());
        }

        private void confirmPermissionsReview() {
            PreferenceGroup preferenceGroup = mNewPermissionsCategory != null
                ? mNewPermissionsCategory : getPreferenceScreen();

            final int preferenceCount = preferenceGroup.getPreferenceCount();
            for (int i = 0; i < preferenceCount; i++) {
                Preference preference = preferenceGroup.getPreference(i);
                if (preference instanceof TwoStatePreference) {
                    TwoStatePreference twoStatePreference = (TwoStatePreference) preference;
                    String groupName = preference.getKey();
                    AppPermissionGroup group = mAppPermissions.getPermissionGroup(groupName);
                    if (twoStatePreference.isChecked()) {
                        group.grantRuntimePermissions(false);
                    } else {
                        group.revokeRuntimePermissions(false);
                    }
                    group.resetReviewRequired();
                }
            }
        }

        private void bindUi() {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            // Set message
            String appLabel = mAppPermissions.getAppLabel().toString();
            final int labelTemplateResId = isPackageUpdated()
                    ?  R.string.permission_review_message_template_update
                    :  R.string.permission_review_message_template_install;
            String message = getString(labelTemplateResId, appLabel);

            // Set the permission message as the title so it can be announced.
            ((TextView)activity.findViewById(R.id.message)).setText(message);

            Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_close);
            toolbar.setNavigationOnClickListener(view -> {
                executeCallback(false);
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
            });
            activity.setActionBar(toolbar);

            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            menu.clear();
            menu.add(R.string.review_button_continue);
            menu.getItem(0).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            Activity activity = getActivity();
            if (activity == null) {
                return false;
            }

            confirmPermissionsReview();
            executeCallback(true);
            activity.finish();

            return true;
        }

        private void loadPreferences() {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                screen = getPreferenceManager().createPreferenceScreen(getActivity());
                setPreferenceScreen(screen);
            } else {
                screen.removeAll();
            }

            PreferenceGroup currentPermissionsCategory = null;
            PreferenceGroup oldNewPermissionsCategory = mNewPermissionsCategory;
            mNewPermissionsCategory = null;

            final boolean isPackageUpdated = isPackageUpdated();

            for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
                if (!Utils.shouldShowPermission(group, mAppPermissions.getPackageInfo().packageName)
                        || !Utils.OS_PKG.equals(group.getDeclaringPackage())) {
                    continue;
                }

                // TODO: Sort permissions - platform first then third-party ones

                final SwitchPreference preference;
                Preference cachedPreference = oldNewPermissionsCategory != null
                        ? oldNewPermissionsCategory.findPreference(group.getName()) : null;
                if (cachedPreference instanceof SwitchPreference) {
                    preference = (SwitchPreference) cachedPreference;
                } else {
                    preference = new SwitchPreference(getActivity());

                    preference.setKey(group.getName());
                    Drawable icon = Utils.loadDrawable(activity.getPackageManager(),
                            group.getIconPkg(), group.getIconResId());
                    preference.setIcon(Utils.applyTint(getContext(), icon,
                            android.R.attr.colorControlNormal));
                    preference.setTitle(group.getLabel());
                    preference.setSummary(group.getDescription());
                    preference.setPersistent(false);

                    preference.setOnPreferenceChangeListener(this);
                }

                preference.setChecked(group.areRuntimePermissionsGranted());

                // Mutable state
                if (group.isPolicyFixed()) {
                    preference.setEnabled(false);
                    preference.setSummary(getString(
                            R.string.permission_summary_enforced_by_policy));
                } else {
                    preference.setEnabled(true);
                }

                if (group.isReviewRequired()) {
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
                    if (currentPermissionsCategory == null) {
                        currentPermissionsCategory = new PreferenceCategory(activity);
                        currentPermissionsCategory.setTitle(R.string.current_permissions_category);
                        currentPermissionsCategory.setOrder(2);
                        screen.addPreference(currentPermissionsCategory);
                    }
                    currentPermissionsCategory.addPreference(preference);
                }
            }
        }

        private boolean isPackageUpdated() {
            List<AppPermissionGroup> groups = mAppPermissions.getPermissionGroups();
            final int groupCount = groups.size();
            for (int i = 0; i < groupCount; i++) {
                AppPermissionGroup group = groups.get(i);
                if (!group.isReviewRequired()) {
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
    }
}
