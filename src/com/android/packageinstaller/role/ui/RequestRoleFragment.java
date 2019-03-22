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

package com.android.packageinstaller.role.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;

import com.android.packageinstaller.permission.utils.PackageRemovalMonitor;
import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.packageinstaller.role.utils.PackageUtils;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO: STOPSHIP: Add don't ask again, support theming.
/**
 * {@code Fragment} for a role request.
 */
public class RequestRoleFragment extends DialogFragment {

    private static final String LOG_TAG = RequestRoleFragment.class.getSimpleName();

    private String mRoleName;
    private String mPackageName;

    private Role mRole;

    private Adapter mAdapter;

    private RequestRoleViewModel mViewModel;

    @Nullable
    private PackageRemovalMonitor mPackageRemovalMonitor;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the requested role
     * @param packageName the package name of the application requesting the role
     *
     * @return a new instance of this fragment
     */
    public static RequestRoleFragment newInstance(@NonNull String roleName,
            @NonNull String packageName) {
        RequestRoleFragment fragment = new RequestRoleFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mPackageName = arguments.getString(Intent.EXTRA_PACKAGE_NAME);
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);

        mRole = Roles.get(requireContext()).get(mRoleName);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), getTheme());
        Context context = builder.getContext();

        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfo(mPackageName, context);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            finish();
            return super.onCreateDialog(savedInstanceState);
        }
        Drawable icon = Utils.getBadgedIcon(context, applicationInfo);
        String applicationLabel = Utils.getAppLabel(applicationInfo, context);
        String roleLabel = getString(mRole.getLabelResource());
        String title = getString(R.string.request_role_title, applicationLabel, roleLabel);

        View titleLayout = LayoutInflater.from(context).inflate(R.layout.request_role_title, null);
        ImageView iconImage = titleLayout.findViewById(R.id.icon);
        iconImage.setImageDrawable(icon);
        TextView titleText = titleLayout.findViewById(R.id.title);
        titleText.setText(title);

        mAdapter = new Adapter(mRole);
        if (savedInstanceState != null) {
            mAdapter.onRestoreInstanceState(savedInstanceState);
        }

        RoleManager roleManager = context.getSystemService(RoleManager.class);
        List<String> currentPackageNames = roleManager.getRoleHolders(mRoleName);
        if (currentPackageNames.contains(mPackageName)) {
            Log.i(LOG_TAG, "Application is already a role holder, role: " + mRoleName
                    + ", package: " + mPackageName);
            setResultOkAndFinish();
            return super.onCreateDialog(savedInstanceState);
        }

        AlertDialog dialog = builder
                .setCustomTitle(titleLayout)
                .setSingleChoiceItems(mAdapter, AdapterView.INVALID_POSITION, (dialog2, which) ->
                        onItemClicked(which))
                // Set the positive button listener later to avoid the automatic dismiss behavior.
                .setPositiveButton(R.string.request_role_set_as_default, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        dialog.setOnShowListener(dialog2 -> dialog.getButton(Dialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> setRoleHolder()));
        return dialog;
    }

    @Override
    public AlertDialog getDialog() {
        return (AlertDialog) super.getDialog();
    }

    @Override
    public void onStart() {
        super.onStart();

        Context context = requireContext();
        if (PackageUtils.getApplicationInfo(mPackageName, context) == null) {
            Log.w(LOG_TAG, "Unknown application: " + mPackageName);
            finish();
            return;
        }

        mPackageRemovalMonitor = new PackageRemovalMonitor(context, mPackageName) {
            @Override
            protected void onPackageRemoved() {
                Log.w(LOG_TAG, "Application is uninstalled, role: " + mRoleName + ", package: "
                        + mPackageName);
                finish();
            }
        };
        mPackageRemovalMonitor.register();

        mAdapter.setListView(getDialog().getListView());

        // Postponed to onStart() so that the list view in dialog is created.
        mViewModel = ViewModelProviders.of(this, new RequestRoleViewModel.Factory(mRole,
                requireActivity().getApplication())).get(RequestRoleViewModel.class);
        mViewModel.getRoleLiveData().observe(this, mAdapter::replace);
        mViewModel.getManageRoleHolderStateLiveData().observe(this,
                this::onManageRoleHolderStateChanged);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        mAdapter.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mPackageRemovalMonitor != null) {
            mPackageRemovalMonitor.unregister();
            mPackageRemovalMonitor = null;
        }
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);

        Log.i(LOG_TAG, "Dialog cancelled, role: " + mRoleName + ", package: " + mPackageName);
        if (getActivity() != null) {
            finish();
        }
    }

    private void onItemClicked(int position) {
        mAdapter.onItemClicked(position);
        updateUiEnabled();
    }

    private void setRoleHolder() {
        String packageName = mAdapter.getCheckedPackageName();
        Context context = requireContext();
        UserHandle user = Process.myUserHandle();
        if (packageName == null) {
            mRole.onNoneHolderSelectedAsUser(user, context);
            mViewModel.getManageRoleHolderStateLiveData().clearRoleHoldersAsUser(mRoleName, 0, user,
                    context);
        } else {
            int flags = Objects.equals(packageName, mPackageName)
                    ? RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP : 0;
            mViewModel.getManageRoleHolderStateLiveData().setRoleHolderAsUser(mRoleName,
                    packageName, true, flags, user, context);
        }
    }

    private void onManageRoleHolderStateChanged(int state) {
        switch (state) {
            case ManageRoleHolderStateLiveData.STATE_IDLE:
            case ManageRoleHolderStateLiveData.STATE_WORKING:
                updateUiEnabled();
                break;
            case ManageRoleHolderStateLiveData.STATE_SUCCESS: {
                ManageRoleHolderStateLiveData liveData =
                        mViewModel.getManageRoleHolderStateLiveData();
                String packageName = liveData.getLastPackageName();
                if (packageName != null) {
                    mRole.onHolderSelectedAsUser(packageName, liveData.getLastUser(),
                            requireContext());
                }
                if (Objects.equals(packageName, mPackageName)) {
                    setResultOkAndFinish();
                } else {
                    finish();
                }
                break;
            }
            case ManageRoleHolderStateLiveData.STATE_FAILURE:
                finish();
                break;
        }
    }

    private void updateUiEnabled() {
        AlertDialog dialog = getDialog();
        boolean enabled = mViewModel.getManageRoleHolderStateLiveData().getValue()
                == ManageRoleHolderStateLiveData.STATE_IDLE;
        dialog.getListView().setEnabled(enabled);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled
                && !mAdapter.isHolderApplicationChecked());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(enabled);
    }

    private void setResultOkAndFinish() {
        requireActivity().setResult(Activity.RESULT_OK);
        finish();
    }

    private void finish() {
        requireActivity().finish();
    }

    private static class Adapter extends BaseAdapter {

        private static final String STATE_USER_CHECKED = Adapter.class.getName()
                + ".state.USER_CHECKED";
        private static final String STATE_USER_CHECKED_PACKAGE_NAME = Adapter.class.getName()
                + ".state.USER_CHECKED_PACKAGE_NAME";

        @NonNull
        private final Role mRole;

        // We'll use a null to represent the "None" item.
        @NonNull
        private final List<Pair<ApplicationInfo, Boolean>> mQualifyingApplications =
                new ArrayList<>();

        private boolean mHasHolderApplication;

        private ListView mListView;

        // If user has ever clicked an item to mark it as checked, we no longer automatically mark
        // the current holder as checked.
        private boolean mUserChecked;

        private boolean mPendingUserChecked;
        // We may use a null to represent the "None" item.
        @Nullable
        private String mPendingUserCheckedPackageName;

        Adapter(@NonNull Role role) {
            mRole = role;
        }

        public void onSaveInstanceState(@NonNull Bundle outState) {
            outState.putBoolean(STATE_USER_CHECKED, mUserChecked);
            if (mUserChecked) {
                outState.putString(STATE_USER_CHECKED_PACKAGE_NAME, getCheckedPackageName());
            }
        }

        public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
            mPendingUserChecked = savedInstanceState.getBoolean(STATE_USER_CHECKED);
            if (mPendingUserChecked) {
                mPendingUserCheckedPackageName = savedInstanceState.getString(
                        STATE_USER_CHECKED_PACKAGE_NAME);
            }
        }

        public void setListView(@NonNull ListView listView) {
            mListView = listView;
        }

        public void onItemClicked(int position) {
            mUserChecked = true;
        }

        public void replace(@NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
            mQualifyingApplications.clear();
            if (mRole.shouldShowNone()) {
                mQualifyingApplications.add(0, null);
            }
            mQualifyingApplications.addAll(qualifyingApplications);
            mHasHolderApplication = hasHolderApplication(qualifyingApplications);
            notifyDataSetChanged();

            if (mPendingUserChecked) {
                restoreItemChecked();
                mPendingUserChecked = false;
                mPendingUserCheckedPackageName = null;
            }

            if (!mUserChecked) {
                updateItemChecked();
            }
        }

        private static boolean hasHolderApplication(
                @NonNull List<Pair<ApplicationInfo, Boolean>> qualifyingApplications) {
            int qualifyingApplicationsSize = qualifyingApplications.size();
            for (int i = 0; i < qualifyingApplicationsSize; i++) {
                Pair<ApplicationInfo, Boolean> qualifyingApplication = qualifyingApplications.get(
                        i);
                boolean isHolderApplication = qualifyingApplication.second;

                if (isHolderApplication) {
                    return true;
                }
            }
            return false;
        }

        private void restoreItemChecked() {
            if (mPendingUserCheckedPackageName == null) {
                if (mRole.shouldShowNone()) {
                    mUserChecked = true;
                    mListView.setItemChecked(0, true);
                }
            } else {
                int count = getCount();
                for (int i = 0; i < count; i++) {
                    Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(i);
                    if (qualifyingApplication == null) {
                        continue;
                    }
                    String packageName = qualifyingApplication.first.packageName;

                    if (Objects.equals(packageName, mPendingUserCheckedPackageName)) {
                        mUserChecked = true;
                        mListView.setItemChecked(i, true);
                        break;
                    }
                }
            }
        }

        private void updateItemChecked() {
            if (!mHasHolderApplication) {
                if (mRole.shouldShowNone()) {
                    mListView.setItemChecked(0, true);
                } else {
                    mListView.clearChoices();
                }
            } else {
                int count = getCount();
                for (int i = 0; i < count; i++) {
                    Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(i);
                    if (qualifyingApplication == null) {
                        continue;
                    }
                    boolean isHolderApplication = qualifyingApplication.second;

                    if (isHolderApplication) {
                        mListView.setItemChecked(i, true);
                        break;
                    }
                }
            }
        }

        @Nullable
        public Pair<ApplicationInfo, Boolean> getCheckedItem() {
            int position = mListView.getCheckedItemPosition();
            return position != AdapterView.INVALID_POSITION ? getItem(position) : null;
        }

        @Nullable
        public String getCheckedPackageName() {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = getCheckedItem();
            return qualifyingApplication == null ? null : qualifyingApplication.first.packageName;
        }

        public boolean isHolderApplicationChecked() {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = getCheckedItem();
            return qualifyingApplication == null ? !mHasHolderApplication
                    : qualifyingApplication.second;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return mQualifyingApplications.size();
        }

        @Nullable
        @Override
        public Pair<ApplicationInfo, Boolean> getItem(int position) {
            return mQualifyingApplications.get(position);
        }

        @Override
        public long getItemId(int position) {
            Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(position);
            return qualifyingApplication == null ? 0
                    : qualifyingApplication.first.packageName.hashCode();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            Context context = parent.getContext();
            View view = convertView;
            ViewHolder holder;
            if (view != null) {
                holder = (ViewHolder) view.getTag();
            } else {
                view = LayoutInflater.from(context).inflate(R.layout.request_role_item, parent,
                        false);
                holder = new ViewHolder(view);
                view.setTag(holder);
            }

            Pair<ApplicationInfo, Boolean> qualifyingApplication = getItem(position);
            Drawable icon;
            String title;
            String subtitle;
            if (qualifyingApplication == null) {
                icon = AppCompatResources.getDrawable(context, R.drawable.ic_remove_circle);
                title = context.getString(R.string.default_app_none);
                subtitle = !mHasHolderApplication ? context.getString(
                        R.string.request_role_current_default) : null;
            } else {
                ApplicationInfo qualifyingApplicationInfo = qualifyingApplication.first;
                icon = Utils.getBadgedIcon(context, qualifyingApplicationInfo);
                title = Utils.getAppLabel(qualifyingApplicationInfo, context);
                boolean isHolderApplication = qualifyingApplication.second;
                subtitle = isHolderApplication ? context.getString(
                        R.string.request_role_current_default) : null;
            }

            holder.iconImage.setImageDrawable(icon);
            holder.titleText.setText(title);
            holder.subtitleText.setVisibility(!TextUtils.isEmpty(subtitle) ? View.VISIBLE
                    : View.GONE);
            holder.subtitleText.setText(subtitle);

            return view;
        }

        private static class ViewHolder {

            @NonNull
            public final ImageView iconImage;
            @NonNull
            public final TextView titleText;
            @NonNull
            public final TextView subtitleText;

            ViewHolder(@NonNull View view) {
                iconImage = Objects.requireNonNull(view.findViewById(R.id.icon));
                titleText = Objects.requireNonNull(view.findViewById(R.id.title));
                subtitleText = Objects.requireNonNull(view.findViewById(R.id.subtitle));
            }
        }
    }
}
