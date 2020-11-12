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

package com.android.permissioncontroller.permission.ui.television;

import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.BidiFormatter;
import android.util.ArraySet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissions;
import com.android.permissioncontroller.permission.model.livedatatypes.AutoRevokeState;
import com.android.permissioncontroller.permission.ui.ReviewPermissionsActivity;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.LocationUtils;
import com.android.permissioncontroller.permission.utils.SafetyNetLogger;
import com.android.permissioncontroller.permission.utils.Utils;

public final class AppPermissionsFragment extends SettingsWithHeader
        implements OnPreferenceClickListener {

    private static final String LOG_TAG = "ManagePermsFragment";

    static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";
    private static final String AUTO_REVOKE_SWITCH_KEY = "_AUTO_REVOKE_SWITCH_KEY";

    private static final int MENU_ALL_PERMS = 0;

    private ArraySet<AppPermissionGroup> mToggledGroups;
    private AppPermissionGroupsViewModel mViewModel;
    private AppPermissions mAppPermissions;
    private PreferenceScreen mExtraScreen;

    private boolean mHasConfirmedRevoke;

    public static AppPermissionsFragment newInstance(String packageName, UserHandle user) {
        return setPackage(new AppPermissionsFragment(), packageName, user);
    }

    private static <T extends PermissionsFrameFragment> T setPackage(
            T fragment, String packageName, UserHandle user) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, user);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true /* loading */, false /* animate */);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        final String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        final UserHandle user = getArguments().getParcelable(Intent.EXTRA_USER);

        Activity activity = getActivity();
        PackageInfo packageInfo = getPackageInfo(activity, packageName);
        if (packageName == null) {
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            getActivity().finish();
            return;
        }

        mAppPermissions = new AppPermissions(activity, packageInfo, true,
                () -> getActivity().finish());

        if (mAppPermissions.isReviewRequired()) {
            Intent intent = new Intent(getActivity(), ReviewPermissionsActivity.class);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
            intent.putExtra(Intent.EXTRA_USER, user);
            startActivity(intent);
            getActivity().finish();
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        final UserHandle user = getArguments().getParcelable(Intent.EXTRA_USER);

        AppPermissionGroupsViewModelFactory factory =
                new AppPermissionGroupsViewModelFactory(packageName, user, 0);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionGroupsViewModel.class);
        mViewModel.getAutoRevokeLiveData().observe(this, this::setAutoRevokeToggleState);

        mAppPermissions.refresh();
        loadPreferences();
        setPreferencesCheckedState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                getActivity().finish();
                return true;
            }

            case MENU_ALL_PERMS: {
                PermissionsFrameFragment frag =
                        AllAppPermissionsFragment.newInstance(
                                getArguments().getString(Intent.EXTRA_PACKAGE_NAME));
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, frag)
                        .addToBackStack("AllPerms")
                        .commit();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mAppPermissions != null) {
            bindUi(this,
                getArguments().getString(Intent.EXTRA_PACKAGE_NAME),
                getArguments().getParcelable(Intent.EXTRA_USER),
                R.string.app_permissions_decor_title);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(Menu.NONE, MENU_ALL_PERMS, Menu.NONE, R.string.all_permissions);
    }

    static void bindUi(SettingsWithHeader fragment, String packageName,
            UserHandle user, int decorTitleStringResId) {
        final Activity activity = fragment.getActivity();
        final Application application = activity.getApplication();

        CharSequence label = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(application, packageName, user));
        Drawable icon= KotlinUtils.INSTANCE.getBadgedPackageIcon(application, packageName, user);

        Intent infoIntent = null;
        if (!activity.getIntent().getBooleanExtra(EXTRA_HIDE_INFO_BUTTON, false)) {
            infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null));
        }

        fragment.setHeader(icon, label, infoIntent, fragment.getString(
                R.string.additional_permissions_decor_title));
    }

    private void loadPreferences() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();
        screen.addPreference(createHeaderLineTwoPreference(context));

        if (mExtraScreen != null) {
            mExtraScreen.removeAll();
            mExtraScreen = null;
        }

        final Preference extraPerms = new Preference(context);
        extraPerms.setIcon(R.drawable.ic_toc);
        extraPerms.setTitle(R.string.additional_permissions);

        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (!Utils.shouldShowPermission(getContext(), group)) {
                continue;
            }

            boolean isPlatform = group.getDeclaringPackage().equals(Utils.OS_PKG);

            Preference preference = new Preference(context);
            preference.setOnPreferenceClickListener(this);
            preference.setKey(group.getName());
            Drawable icon = Utils.loadDrawable(context.getPackageManager(),
                    group.getIconPkg(), group.getIconResId());
            preference.setIcon(Utils.applyTint(getContext(), icon,
                    android.R.attr.colorControlNormal));
            preference.setTitle(group.getLabel());
            if (group.isSystemFixed()) {
                preference.setSummary(getString(R.string.permission_summary_enabled_system_fixed));
            } else if (group.isPolicyFixed()) {
                preference.setSummary(getString(R.string.permission_summary_enforced_by_policy));
            }
            preference.setPersistent(false);
            preference.setEnabled(!group.isSystemFixed() && !group.isPolicyFixed());

            if (isPlatform) {
                screen.addPreference(preference);
            } else {
                if (mExtraScreen == null) {
                    mExtraScreen = getPreferenceManager().createPreferenceScreen(context);
                    mExtraScreen.addPreference(createHeaderLineTwoPreference(context));
                }
                mExtraScreen.addPreference(preference);
            }
        }

        final String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        final UserHandle user = getArguments().getParcelable(Intent.EXTRA_USER);

        if (mExtraScreen != null) {
            extraPerms.setOnPreferenceClickListener(preference -> {
                AdditionalPermissionsFragment frag = new AdditionalPermissionsFragment();
                setPackage(frag, packageName, user);
                frag.setTargetFragment(AppPermissionsFragment.this, 0);
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, frag)
                        .addToBackStack(null)
                        .commit();
                return true;
            });
            int count = mExtraScreen.getPreferenceCount() - 1;
            extraPerms.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, count, count));
            screen.addPreference(extraPerms);
        }

        addAutoRevokePreferences(getPreferenceScreen());

        setLoading(false /* loading */, true /* animate */);
    }

    /**
     * Creates a heading below decor_title and above the rest of the preferences. This heading
     * displays the app name and banner icon. It's used in both system and additional permissions
     * fragments for each app. The styling used is the same as a leanback preference with a
     * customized background color
     * @param context The context the preferences created on
     * @return The preference header to be inserted as the first preference in the list.
     */
    private Preference createHeaderLineTwoPreference(Context context) {
        Preference headerLineTwo = new Preference(context) {
            @Override
            public void onBindViewHolder(PreferenceViewHolder holder) {
                super.onBindViewHolder(holder);
                holder.itemView.setBackgroundColor(
                        getResources().getColor(R.color.lb_header_banner_color));
            }
        };
        headerLineTwo.setKey(HEADER_PREFERENCE_KEY);
        headerLineTwo.setSelectable(false);
        headerLineTwo.setTitle(mLabel);
        headerLineTwo.setIcon(mIcon);
        return headerLineTwo;
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        String groupName = preference.getKey();
        final AppPermissionGroup group = mAppPermissions.getPermissionGroup(groupName);

        if (group == null) {
            return false;
        }

        addToggledGroup(group);

        if (LocationUtils.isLocationGroupAndProvider(getContext(), group.getName(),
                group.getApp().packageName)) {
            LocationUtils.showLocationDialog(getContext(), mAppPermissions.getAppLabel());
            return false;
        }

        AppPermissionFragment frag = new AppPermissionFragment();

        frag.setArguments(AppPermissionFragment.createArgs(
                    /* packageName= */ group.getApp().packageName,
                    /* permName= */ null,
                    /* groupName= */ group.getName(),
                    /* userHandle= */ group.getUser(),
                    /* caller= */ null,
                    /* sessionId= */ INVALID_SESSION_ID,
                    /* grantCategory= */ null));
        frag.setTargetFragment(AppPermissionsFragment.this, 0);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack(null)
                .commit();

        return true;
    }

    @Override
    public void onPause() {
        mViewModel.getAutoRevokeLiveData().removeObservers(this);
        super.onPause();
        logToggledGroups();
    }

    private void addToggledGroup(AppPermissionGroup group) {
        if (mToggledGroups == null) {
            mToggledGroups = new ArraySet<>();
        }
        mToggledGroups.add(group);
    }

    private void logToggledGroups() {
        if (mToggledGroups != null) {
            SafetyNetLogger.logPermissionsToggled(mToggledGroups);
            mToggledGroups = null;
        }
    }

    private void setPreferencesCheckedState() {
        setPreferencesCheckedState(getPreferenceScreen());
        if (mExtraScreen != null) {
            setPreferencesCheckedState(mExtraScreen);
        }
        setAutoRevokeToggleState(mViewModel.getAutoRevokeLiveData().getValue());
    }

    private void setPreferencesCheckedState(PreferenceScreen screen) {
        int preferenceCount = screen.getPreferenceCount();
        for (int i = 0; i < preferenceCount; i++) {
            Preference preference = screen.getPreference(i);
            if (preference.getKey() == null) {
                continue;
            }
            AppPermissionGroup group = mAppPermissions.getPermissionGroup(preference.getKey());
            if (group == null) {
                continue;
            }
            AppPermissionGroup backgroundGroup = group.getBackgroundPermissions();

            if (group.areRuntimePermissionsGranted()) {
                if (backgroundGroup == null) {
                    preference.setSummary(R.string.app_permission_button_allow);
                } else {
                    if (backgroundGroup.areRuntimePermissionsGranted()) {
                        preference.setSummary(R.string.permission_access_always);
                    } else {
                        preference.setSummary(R.string.permission_access_only_foreground);
                    }
                }
            } else {
                if (group.isOneTime()) {
                    preference.setSummary(R.string.app_permission_button_ask);
                } else {
                    preference.setSummary(R.string.permission_access_never);
                }
            }
        }
    }


    private void addAutoRevokePreferences(PreferenceScreen screen) {
        SwitchPreference autoRevokeSwitch =
                new SwitchPreference(screen.getPreferenceManager().getContext());
        autoRevokeSwitch.setLayoutResource(R.layout.preference_permissions_revoke);
        autoRevokeSwitch.setOnPreferenceClickListener((preference) -> {
            mViewModel.setAutoRevoke(autoRevokeSwitch.isChecked());
            android.util.Log.w(LOG_TAG, "setAutoRevoke " + autoRevokeSwitch.isChecked());
            return true;
        });
        autoRevokeSwitch.setTitle(R.string.auto_revoke_label);
        autoRevokeSwitch.setSummary(R.string.auto_revoke_summary);
        autoRevokeSwitch.setKey(AUTO_REVOKE_SWITCH_KEY);
        screen.addPreference(autoRevokeSwitch);
    }

    private void setAutoRevokeToggleState(AutoRevokeState state) {
        SwitchPreference autoRevokeSwitch = getPreferenceScreen().findPreference(
                AUTO_REVOKE_SWITCH_KEY);
        if (state == null || autoRevokeSwitch == null) {
            return;
        }
        if (!state.isEnabledGlobal()) {
            autoRevokeSwitch.setVisible(false);
            return;
        }
        autoRevokeSwitch.setVisible(true);
        autoRevokeSwitch.setEnabled(state.getShouldAllowUserToggle());
        autoRevokeSwitch.setChecked(state.isEnabledForApp());
    }

    private static PackageInfo getPackageInfo(Activity activity, String packageName) {
        try {
            return activity.getPackageManager().getPackageInfo(
                    packageName, PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.i(LOG_TAG, "No package:" + activity.getCallingPackage(), e);
            return null;
        }
    }

    public static class AdditionalPermissionsFragment extends SettingsWithHeader {
        AppPermissionsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mOuterFragment = (AppPermissionsFragment) getTargetFragment();
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferenceScreen(mOuterFragment.mExtraScreen);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            bindUi(this,
                getArguments().getString(Intent.EXTRA_PACKAGE_NAME),
                getArguments().getParcelable(Intent.EXTRA_USER),
                R.string.additional_permissions_decor_title);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
            case android.R.id.home:
                getFragmentManager().popBackStack();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
