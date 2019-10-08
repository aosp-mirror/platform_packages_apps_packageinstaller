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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.auto.AutoSettingsFrameFragment;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.ui.AppPermissionActivity;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.text.Collator;
import java.util.ArrayList;

/** Screen to show the permissions for a specific application. */
public class AutoAppPermissionsFragment extends AutoSettingsFrameFragment {

    private static final String KEY_ALLOWED_PERMISSIONS_GROUP = "allowed_permissions_group";
    private static final String KEY_DENIED_PERMISSIONS_GROUP = "denied_permissions_group";

    private AppPermissions mAppPermissions;
    private PreferenceScreen mExtraScreen;

    private Collator mCollator;

    /**
     * @return A new fragment
     */
    public static AutoAppPermissionsFragment newInstance(@NonNull String packageName,
            @NonNull UserHandle userHandle) {
        return setPackageNameAndUserHandle(new AutoAppPermissionsFragment(), packageName,
                userHandle);
    }

    private static <T extends Fragment> T setPackageNameAndUserHandle(@NonNull T fragment,
            @NonNull String packageName, @NonNull UserHandle userHandle) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true);

        String packageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        UserHandle userHandle = getArguments().getParcelable(Intent.EXTRA_USER);
        Activity activity = requireActivity();
        PackageInfo packageInfo = AutoPermissionsUtils.getPackageInfo(activity, packageName,
                userHandle);
        if (packageInfo == null) {
            Toast.makeText(getContext(), R.string.app_not_found_dlg_title,
                    Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        setHeaderLabel(getContext().getString(R.string.app_permissions));
        setAction(getContext().getString(R.string.all_permissions), v -> showAllPermissions());

        mAppPermissions = new AppPermissions(activity, packageInfo, /* sortGroups= */ true,
                () -> getActivity().finish());

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    @Override
    public void onStart() {
        super.onStart();
        mAppPermissions.refresh();
        bindUi(mAppPermissions.getPackageInfo());
        updatePreferences();
    }

    @Override
    public void onStop() {
        super.onStop();
        getPreferenceScreen().removeAll();
    }

    private void showAllPermissions() {
        Fragment frag = AutoAllAppPermissionsFragment.newInstance(
                getArguments().getString(Intent.EXTRA_PACKAGE_NAME),
                getArguments().getParcelable(Intent.EXTRA_USER));
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    protected void bindUi(PackageInfo packageInfo) {
        getPreferenceScreen().addPreference(
                AutoPermissionsUtils.createHeaderPreference(getContext(),
                        packageInfo.applicationInfo));

        PreferenceGroup allowed = new PreferenceCategory(getContext());
        allowed.setKey(KEY_ALLOWED_PERMISSIONS_GROUP);
        allowed.setTitle(R.string.allowed_header);
        getPreferenceScreen().addPreference(allowed);

        PreferenceGroup denied = new PreferenceCategory(getContext());
        denied.setKey(KEY_DENIED_PERMISSIONS_GROUP);
        denied.setTitle(R.string.denied_header);
        getPreferenceScreen().addPreference(denied);
    }

    private void updatePreferences() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        PreferenceCategory allowed = findPreference(KEY_ALLOWED_PERMISSIONS_GROUP);
        PreferenceCategory denied = findPreference(KEY_DENIED_PERMISSIONS_GROUP);

        allowed.removeAll();
        denied.removeAll();

        if (mExtraScreen != null) {
            mExtraScreen.removeAll();
            mExtraScreen.addPreference(AutoPermissionsUtils.createHeaderPreference(getContext(),
                    mAppPermissions.getPackageInfo().applicationInfo));
        }

        Preference extraPerms = new Preference(context);
        extraPerms.setIcon(R.drawable.ic_toc);
        extraPerms.setTitle(R.string.additional_permissions);
        boolean extraPermsAreAllowed = false;

        ArrayList<AppPermissionGroup> groups = new ArrayList<>(
                mAppPermissions.getPermissionGroups());
        groups.sort((x, y) -> mCollator.compare(x.getLabel(), y.getLabel()));
        allowed.setOrderingAsAdded(true);
        denied.setOrderingAsAdded(true);

        for (int i = 0; i < groups.size(); i++) {
            AppPermissionGroup group = groups.get(i);
            if (!Utils.shouldShowPermission(getContext(), group)) {
                continue;
            }

            boolean isPlatform = group.getDeclaringPackage().equals(Utils.OS_PKG);

            Preference preference = createPermissionPreference(getContext(), group);
            if (isPlatform) {
                PreferenceCategory category =
                        group.areRuntimePermissionsGranted() ? allowed : denied;
                category.addPreference(preference);
            } else {
                if (mExtraScreen == null) {
                    mExtraScreen = getPreferenceManager().createPreferenceScreen(context);
                    mExtraScreen.addPreference(
                            AutoPermissionsUtils.createHeaderPreference(getContext(),
                                    mAppPermissions.getPackageInfo().applicationInfo));
                }
                mExtraScreen.addPreference(preference);
                if (group.areRuntimePermissionsGranted()) {
                    extraPermsAreAllowed = true;
                }
            }
        }

        if (mExtraScreen != null) {
            extraPerms.setOnPreferenceClickListener(preference -> {
                AutoAppPermissionsFragment.AdditionalPermissionsFragment
                        frag = new AutoAppPermissionsFragment.AdditionalPermissionsFragment();
                setPackageNameAndUserHandle(frag,
                        getArguments().getString(Intent.EXTRA_PACKAGE_NAME),
                        getArguments().getParcelable(Intent.EXTRA_USER));
                frag.setTargetFragment(AutoAppPermissionsFragment.this, 0);
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, frag)
                        .addToBackStack(null)
                        .commit();
                return true;
            });
            // Delete 1 to account for app header preference.
            int count = mExtraScreen.getPreferenceCount() - 1;
            extraPerms.setSummary(getResources().getQuantityString(
                    R.plurals.additional_permissions_more, count,
                    count));
            PreferenceCategory category = extraPermsAreAllowed ? allowed : denied;
            category.addPreference(extraPerms);
        }

        if (allowed.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_permissions_allowed));
            empty.setSelectable(false);
            allowed.addPreference(empty);
        }
        if (denied.getPreferenceCount() == 0) {
            Preference empty = new Preference(context);
            empty.setTitle(getString(R.string.no_permissions_denied));
            empty.setSelectable(false);
            denied.addPreference(empty);
        }

        setLoading(false);
    }

    private Preference createPermissionPreference(Context context, AppPermissionGroup group) {
        Preference preference = new Preference(context);
        Drawable icon = Utils.loadDrawable(context.getPackageManager(),
                group.getIconPkg(), group.getIconResId());
        preference.setKey(group.getName());
        preference.setTitle(group.getFullLabel());
        preference.setIcon(Utils.applyTint(context, icon, android.R.attr.colorControlNormal));
        preference.setOnPreferenceClickListener(pref -> {
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, group.getApp().packageName);
            intent.putExtra(Intent.EXTRA_PERMISSION_NAME, group.getPermissions().get(0).getName());
            intent.putExtra(Intent.EXTRA_USER, group.getUser());
            intent.putExtra(AppPermissionActivity.EXTRA_CALLER_NAME,
                    AutoAppPermissionsFragment.class.getName());
            context.startActivity(intent);
            return true;
        });
        return preference;
    }

    private String getGroupSummary(AppPermissionGroup group) {
        if (group.hasPermissionWithBackgroundMode() && group.areRuntimePermissionsGranted()) {
            AppPermissionGroup backgroundGroup = group.getBackgroundPermissions();
            if (backgroundGroup == null || !backgroundGroup.areRuntimePermissionsGranted()) {
                return getContext().getString(R.string.permission_subtitle_only_in_foreground);
            }
        }
        return null;
    }

    /**
     * Class that shows additional permissions.
     */
    public static class AdditionalPermissionsFragment extends AutoSettingsFrameFragment {
        AutoAppPermissionsFragment mOuterFragment;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            // Set this before calling super.onCreate as it is needed in onCreatePreferences
            // (which is called from super.onCreate).
            mOuterFragment = (AutoAppPermissionsFragment) getTargetFragment();
            super.onCreate(savedInstanceState);
            setHeaderLabel(mOuterFragment.getHeaderLabel());
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            setPreferenceScreen(mOuterFragment.mExtraScreen);
        }
    }
}

