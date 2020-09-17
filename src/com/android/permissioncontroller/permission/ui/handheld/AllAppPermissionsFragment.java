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

package com.android.permissioncontroller.permission.ui.handheld;

import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.data.PackagePermissionsLiveData;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.Permission;
import com.android.permissioncontroller.permission.ui.model.AllAppPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.AllAppPermissionsViewModelFactory;
import com.android.permissioncontroller.permission.utils.ArrayUtils;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.text.Collator;
import java.util.List;
import java.util.Map;

/**
 * Show and manage individual permissions for an app.
 *
 * <p>Shows the list of individual runtime and non-runtime permissions the app has requested.
 */
public final class AllAppPermissionsFragment extends SettingsWithLargeHeader {

    private static final String LOG_TAG = "AllAppPermissionsFragment";

    private static final String KEY_OTHER = "other_perms";

    private AllAppPermissionsViewModel mViewModel;
    private Collator mCollator;
    private String mPackageName;
    private String mFilterGroup;
    private UserHandle mUser;

    public static AllAppPermissionsFragment newInstance(@NonNull String packageName,
            @Nullable String filterGroup, @NonNull UserHandle userHandle) {
        AllAppPermissionsFragment instance = new AllAppPermissionsFragment();
        instance.setArguments(createArgs(packageName, filterGroup, userHandle));
        return instance;
    }

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName The name of the package
     * @param filterGroup An optional group to filter out permissions not in the group
     * @param userHandle The user of this package
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName, @Nullable String filterGroup,
            @NonNull UserHandle userHandle) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, filterGroup);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        return arguments;
    }

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName The name of the package
     * @param userHandle The user of this package
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName, @NonNull UserHandle userHandle) {
        return createArgs(packageName, null, userHandle);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mFilterGroup = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        if (mPackageName == null || mUser == null) {
            Log.e(LOG_TAG, "Missing required argument EXTRA_PACKAGE_NAME or "
                    + "EXTRA_USER");
            pressBack(this);
        }

        AllAppPermissionsViewModelFactory factory = new AllAppPermissionsViewModelFactory(
                mPackageName, mUser, mFilterGroup);

        mViewModel = new ViewModelProvider(this, factory).get(AllAppPermissionsViewModel.class);
        mViewModel.getAllPackagePermissionsLiveData().observe(this, this::updateUi);

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));
    }

    @Override
    public void onStart() {
        super.onStart();

        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        // If we target a group make this look like app permissions.
        if (getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME) == null) {
            getActivity().setTitle(R.string.all_permissions);
        } else {
            getActivity().setTitle(R.string.app_permissions);
        }

        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                pressBack(this);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUi(Map<String, List<String>> groupMap) {
        if (groupMap == null && mViewModel.getAllPackagePermissionsLiveData().isInitialized()) {
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            Log.w(LOG_TAG, "invalid package " + mPackageName);
            pressBack(this);
            return;
        }

        if (getPreferenceScreen() == null) {
            addPreferencesFromResource(R.xml.all_permissions);
        }

        PreferenceGroup otherGroup = findPreference(KEY_OTHER);
        otherGroup.removeAll();
        Preference header = findPreference(HEADER_KEY);

        getPreferenceScreen().removeAll();
        getPreferenceScreen().addPreference(otherGroup);
        getPreferenceScreen().addPreference(header);

        Drawable icon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        CharSequence label = KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(),
                mPackageName, mUser);
        Intent infoIntent = null;
        if (!getActivity().getIntent().getBooleanExtra(
                AppPermissionGroupsFragment.EXTRA_HIDE_INFO_BUTTON, false)) {
            infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", mPackageName, null));
        }
        setHeader(icon, label, infoIntent, mUser, false);
        if (groupMap != null) {
            for (String groupName : groupMap.keySet()) {
                List<String> permissions = groupMap.get(groupName);
                if (permissions == null || permissions.isEmpty()) {
                    continue;
                }

                PreferenceGroup pref = findOrCreatePrefGroup(groupName);
                for (String permName : permissions) {
                    pref.addPreference(getPreference(permName, groupName));
                }
            }
        }
        if (otherGroup.getPreferenceCount() == 0) {
            otherGroup.setVisible(false);
        } else {
            otherGroup.setVisible(true);
        }
        KotlinUtils.INSTANCE.sortPreferenceGroup(getPreferenceScreen(), this::comparePreferences,
                true
        );

        setLoading(false, true);
    }

    private int comparePreferences(Preference lhs, Preference rhs) {
        String lKey = lhs.getKey();
        String rKey = rhs.getKey();
        if (lKey.equals(KEY_OTHER)) {
            return 1;
        } else if (rKey.equals(KEY_OTHER)) {
            return -1;
        }
        if (Utils.isModernPermissionGroup(lKey)
                != Utils.isModernPermissionGroup(rKey)) {
            return Utils.isModernPermissionGroup(lKey) ? -1 : 1;
        }
        return mCollator.compare(lhs.getTitle().toString(), rhs.getTitle().toString());
    }

    private PreferenceGroup findOrCreatePrefGroup(String groupName) {
        if (groupName.equals(PackagePermissionsLiveData.NON_RUNTIME_NORMAL_PERMS)) {
            return findPreference(KEY_OTHER);
        }
        PreferenceGroup pref = findPreference(groupName);
        if (pref == null) {
            pref = new PreferenceCategory(getPreferenceManager().getContext());
            pref.setKey(groupName);
            pref.setTitle(KotlinUtils.INSTANCE.getPermGroupLabel(getContext(), groupName));
            getPreferenceScreen().addPreference(pref);
        } else {
            pref.removeAll();
        }
        return pref;
    }

    private Preference getPreference(String permName, String groupName) {
        final Preference pref;
        Context context = getPreferenceManager().getContext();

        // We allow individual permission control for some permissions if review enabled
        final boolean mutable = Utils.isPermissionIndividuallyControlled(getContext(),
                permName);
        if (mutable) {
            AppPermissionGroup appPermGroup = AppPermissionGroup.create(
                    getActivity().getApplication(), mPackageName, groupName, mUser, false);
            pref = new MyMultiTargetSwitchPreference(context, permName, appPermGroup);
        } else {
            pref = new Preference(context);
        }
        pref.setIcon(KotlinUtils.INSTANCE.getPermInfoIcon(context, permName));
        pref.setTitle(KotlinUtils.INSTANCE.getPermInfoLabel(context, permName));
        pref.setSingleLineTitle(false);
        final CharSequence desc = KotlinUtils.INSTANCE.getPermInfoDescription(context,
                permName);

        pref.setOnPreferenceClickListener((Preference preference) -> {
            new AlertDialog.Builder(getContext())
                    .setMessage(desc)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return mutable;
        });

        return pref;
    }

    private static final class MyMultiTargetSwitchPreference extends MultiTargetSwitchPreference {
        MyMultiTargetSwitchPreference(Context context, String permission,
                AppPermissionGroup appPermissionGroup) {
            super(context);

            setChecked(appPermissionGroup.areRuntimePermissionsGranted(
                    new String[]{permission}));

            setSwitchOnClickListener(v -> {
                Switch switchView = (Switch) v;
                if (switchView.isChecked()) {
                    appPermissionGroup.grantRuntimePermissions(true, false,
                            new String[]{permission});
                    // We are granting a permission from a group but since this is an
                    // individual permission control other permissions in the group may
                    // be revoked, hence we need to mark them user fixed to prevent the
                    // app from requesting a non-granted permission and it being granted
                    // because another permission in the group is granted. This applies
                    // only to apps that support runtime permissions.
                    if (appPermissionGroup.doesSupportRuntimePermissions()) {
                        int grantedCount = 0;
                        String[] revokedPermissionsToFix = null;
                        final int permissionCount = appPermissionGroup.getPermissions().size();
                        for (int i = 0; i < permissionCount; i++) {
                            Permission current = appPermissionGroup.getPermissions().get(i);
                            if (!current.isGrantedIncludingAppOp()) {
                                if (!current.isUserFixed()) {
                                    revokedPermissionsToFix = ArrayUtils.appendString(
                                            revokedPermissionsToFix, current.getName());
                                }
                            } else {
                                grantedCount++;
                            }
                        }
                        if (revokedPermissionsToFix != null) {
                            // If some permissions were not granted then they should be fixed.
                            appPermissionGroup.revokeRuntimePermissions(true,
                                    revokedPermissionsToFix);
                        } else if (appPermissionGroup.getPermissions().size() == grantedCount) {
                            // If all permissions are granted then they should not be fixed.
                            appPermissionGroup.grantRuntimePermissions(true, false);
                        }
                    }
                } else {
                    appPermissionGroup.revokeRuntimePermissions(true,
                            new String[]{permission});
                    // If we just revoked the last permission we need to clear
                    // the user fixed state as now the app should be able to
                    // request them at runtime if supported.
                    if (appPermissionGroup.doesSupportRuntimePermissions()
                            && !appPermissionGroup.areRuntimePermissionsGranted()) {
                        appPermissionGroup.revokeRuntimePermissions(false);
                    }
                }
            });
        }
    }
}
