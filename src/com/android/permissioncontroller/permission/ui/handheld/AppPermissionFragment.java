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

package com.android.permissioncontroller.permission.ui.handheld;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.AppPermissionActivity;
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ChangeTarget;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.widget.ActionBarShadowController;

import java.util.List;

import kotlin.Pair;

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission.
 */
public class AppPermissionFragment extends SettingsWithLargeHeader {
    private static final String LOG_TAG = "AppPermissionFragment";

    private @NonNull AppPermissionViewModel mViewModel;

    private @NonNull RadioButton mAlwaysButton;
    private @NonNull RadioButton mForegroundOnlyButton;
    private @NonNull RadioButton mDenyButton;
    private @NonNull View mDivider;
    private @NonNull ViewGroup mWidgetFrame;
    private @NonNull TextView mPermissionDetails;
    private @NonNull NestedScrollView mNestedScrollView;
    private @NonNull String mPackageName;
    private @NonNull String mPermGroupName;
    private @NonNull UserHandle mUser;

    private @NonNull String mPackageLabel;
    private @NonNull String mPermGroupLabel;
    private Drawable mPackageIcon;

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

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mPackageLabel = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(), mPackageName,
                        mUser));
        mPermGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                mPermGroupName).toString();
        mPackageIcon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), mPackageName, mPermGroupName, mUser, sessionId);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionViewModel.class);

        getActivity().setTitle(
                getPreferenceManager().getContext().getString(R.string.app_permission_title,
                        mPermGroupLabel));

        mViewModel.getButtonStateLiveData().observe(this, this::setRadioButtonsState);
        mViewModel.getDetailResIdLiveData().observe(this, this::setDetail);
        mViewModel.getShowAdminSupportLiveData().observe(this, this::setAdminSupportDetail);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getContext();
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.app_permission, container, false);

        setHeader(mPackageIcon, mPackageLabel, null, null, false);
        updateHeader(root.requireViewById(R.id.large_header));

        ((TextView) root.requireViewById(R.id.permission_message)).setText(
                context.getString(R.string.app_permission_header, mPermGroupLabel));

        TextView usageView = root.requireViewById(R.id.usage_summary);
        if (mViewModel.shouldShowPermissionUsage()) {
            usageView.setText(mViewModel.getUsageSummary(getContext(), mPermGroupLabel,
                    mPackageLabel));
        } else if (mViewModel.shouldShowUsageView()) {
            usageView.setText(R.string.app_permission_footer_not_available);
        } else {
            usageView.setVisibility(View.GONE);
        }

        String caller = getArguments().getString(AppPermissionActivity.EXTRA_CALLER_NAME);

        TextView footer1Link = root.requireViewById(R.id.footer_link_1);
        footer1Link.setText(context.getString(R.string.app_permission_footer_app_permissions_link,
                mPackageLabel));
        mViewModel.setBottomLinkState(context, footer1Link, caller,
                Intent.ACTION_MANAGE_APP_PERMISSIONS);

        TextView footer2Link = root.requireViewById(R.id.footer_link_2);
        footer2Link.setText(context.getString(R.string.app_permission_footer_permission_apps_link));
        mViewModel.setBottomLinkState(context, footer2Link, caller,
                Intent.ACTION_MANAGE_PERMISSION_APPS);

        mAlwaysButton = root.requireViewById(R.id.allow_radio_button);
        mForegroundOnlyButton = root.requireViewById(R.id.foreground_only_radio_button);
        mDenyButton = root.requireViewById(R.id.deny_radio_button);
        mDivider = root.requireViewById(R.id.two_target_divider);
        mWidgetFrame = root.requireViewById(R.id.widget_frame);
        mPermissionDetails = root.requireViewById(R.id.permission_details);

        mNestedScrollView = root.requireViewById(R.id.nested_scroll_view);

        return root;
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

    private void setRadioButtonsState(List<AppPermissionViewModel.ButtonState> states) {
        if (states == null && mViewModel.getButtonStateLiveData().isInitialized()) {
            mViewModel.finishActivity(getActivity());
            return;
        } else if (states == null) {
            return;
        }

        mAlwaysButton.setOnClickListener((v) -> mViewModel.requestChange(true, this,
                ChangeTarget.CHANGE_BOTH));
        mForegroundOnlyButton.setOnClickListener((v) -> {
            mViewModel.requestChange(true, this, ChangeTarget.CHANGE_FOREGROUND);
            mViewModel.requestChange(false, this, ChangeTarget.CHANGE_BACKGROUND);
        });
        mDenyButton.setOnClickListener((v) -> mViewModel.requestChange(false, this,
                ChangeTarget.CHANGE_BOTH));

        setButtonState(mAlwaysButton, states.get(0), true);
        setButtonState(mForegroundOnlyButton, states.get(1), false);
        setButtonState(mDenyButton, states.get(2), false);

        if (mForegroundOnlyButton.getVisibility() == View.GONE) {
            mAlwaysButton.setText(getContext().getString(R.string.app_permission_button_allow));
        } else {
            mAlwaysButton.setText(
                    getContext().getString(R.string.app_permission_button_allow_always));
        }

    }

    private void setButtonState(RadioButton button, AppPermissionViewModel.ButtonState state,
            boolean requestGrant) {
        button.setChecked(state.isChecked());
        button.setEnabled(state.isEnabled());
        int visible = state.isShown() ? View.VISIBLE : View.GONE;
        button.setVisibility(visible);
        if (state.getCustomTarget() != null) {
            button.setOnClickListener((v) -> mViewModel.requestChange(requestGrant, this,
                    state.getCustomTarget()));
        }
    }

    private void setDetail(Pair<Integer, Integer> detailResIds) {
        if (detailResIds == null) {
            mWidgetFrame.setVisibility(View.GONE);
            mDivider.setVisibility(View.GONE);
            return;
        }
        mWidgetFrame.setVisibility(View.VISIBLE);
        if (detailResIds.getSecond() != null) {
            // If the permissions are individually controlled, also show a link to the page that
            // lets you control them.
            mDivider.setVisibility(View.VISIBLE);
            showRightIcon(R.drawable.ic_settings);
            mWidgetFrame.setOnClickListener(v -> showAllPermissions());
            mPermissionDetails.setText(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst(), detailResIds.getSecond()));
        } else {
            mPermissionDetails.setText(getPreferenceManager().getContext().getString(
                    detailResIds.getFirst()));
        }
        mPermissionDetails.setVisibility(View.VISIBLE);

    }

    /**
     * Show all individual permissions in this group in a new fragment.
     * TODO ntmyren: move to viewmodel when navigation component is implemented
     */
    private void showAllPermissions() {
        AllAppPermissionsFragment frag = AllAppPermissionsFragment.newInstance(mPackageName,
                mPermGroupName, mUser);
        getParentFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    private void setAdminSupportDetail(EnforcedAdmin admin) {
        if (admin != null) {
            showRightIcon(R.drawable.ic_info);
            mWidgetFrame.setOnClickListener(v ->
                    RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), admin)
            );
        } else {
            mWidgetFrame.removeAllViews();
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
     * Show a dialog that warns the user that she/he is about to revoke permissions that were
     * granted by default.
     *
     *
     * The order of operation to revoke a permission granted by default is:
     *
     *  1. `showDefaultDenyDialog`
     *  1. [DefaultDenyDialog.onCreateDialog]
     *  1. [AppPermissionViewModel.onDenyAnyWay]
     *
     *
     * @param changeTarget Whether background or foreground should be changed
     * @param messageId The Id of the string message to show
     */
    void showDefaultDenyDialog(ChangeTarget changeTarget, @StringRes int messageId) {
        Bundle args = new Bundle();

        args.putInt(DefaultDenyDialog.MSG, messageId);
        args.putSerializable(DefaultDenyDialog.CHANGE_TARGET, changeTarget);

        DefaultDenyDialog defaultDenyDialog = new DefaultDenyDialog();
        defaultDenyDialog.setCancelable(true);
        defaultDenyDialog.setArguments(args);
        defaultDenyDialog.show(getChildFragmentManager().beginTransaction(),
                DefaultDenyDialog.class.getName());
    }

    /**
     * A dialog warning the user that she/he is about to deny a permission that was granted by
     * default.
     *
     * @see #showDefaultDenyDialog(ChangeTarget, int)
     */
    public static class DefaultDenyDialog extends DialogFragment {
        static final String MSG = DefaultDenyDialog.class.getName() + ".arg.msg";
        static final String CHANGE_TARGET = DefaultDenyDialog.class.getName()
                + ".arg.changeTarget";
        private static final String KEY = DefaultDenyDialog.class.getName() + ".arg.key";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface dialog, int which) -> dialog.cancel())
                    .setPositiveButton(R.string.grant_dialog_button_deny_anyway,
                            (DialogInterface dialog, int which) ->
                                    fragment.mViewModel.onDenyAnyWay((ChangeTarget)
                                            getArguments().getSerializable(CHANGE_TARGET)));
            Dialog d = b.create();
            d.setCanceledOnTouchOutside(true);
            return d;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            AppPermissionFragment fragment = (AppPermissionFragment) getParentFragment();
            fragment.setRadioButtonsState(fragment.mViewModel.getButtonStateLiveData().getValue());
        }
    }
}
