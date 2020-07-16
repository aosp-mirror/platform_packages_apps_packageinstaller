/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.Manifest.permission_group.STORAGE;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT;
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.BidiFormatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissions;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonState;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ChangeRequest;
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.R;

import java.util.Map;
import java.util.Objects;

/**
 * Show and manage a single permission group for an app.
 *
 * <p>Allows the user to control whether the app is granted the permission.
 */
public class AppPermissionFragment extends SettingsWithHeader
        implements AppPermissionViewModel.ConfirmDialogShowingFragment {
    private static final String LOG_TAG = "AppPermissionFragment";
    private static final long POST_DELAY_MS = 20;

    static final String GRANT_CATEGORY = "grant_category";

    private @NonNull AppPermissionViewModel mViewModel;
    private @NonNull RadioButtonPreference mAllowButton;
    private @NonNull RadioButtonPreference mAllowAlwaysButton;
    private @NonNull RadioButtonPreference mAllowForegroundButton;
    private @NonNull RadioButtonPreference mAskOneTimeButton;
    private @NonNull RadioButtonPreference mAskButton;
    private @NonNull RadioButtonPreference mDenyButton;
    private @NonNull RadioButtonPreference mDenyForegroundButton;
    private @NonNull String mPackageName;
    private @NonNull String mPermGroupName;
    private @NonNull UserHandle mUser;
    private boolean mIsStorageGroup;
    private boolean mIsInitialLoad;
    private long mSessionId;

    private @NonNull String mPackageLabel;
    private @NonNull String mPermGroupLabel;
    private Drawable mPackageIcon;
    private Utils.ForegroundCapableType mForegroundCapableType;

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName   The name of the package
     * @param permName      The name of the permission whose group this fragment is for (optional)
     * @param groupName     The name of the permission group (required if permName not specified)
     * @param userHandle    The user of the app permission group
     * @param caller        The name of the fragment we called from
     * @param sessionId     The current session ID
     * @param grantCategory The grant status of this app permission group. Used to initially set
     *                      the button state
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName,
            @Nullable String permName, @Nullable String groupName,
            @NonNull UserHandle userHandle, @Nullable String caller, long sessionId, @Nullable
            String grantCategory) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        if (groupName == null) {
            arguments.putString(Intent.EXTRA_PERMISSION_NAME, permName);
        } else {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putString(EXTRA_CALLER_NAME, caller);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putString(GRANT_CATEGORY, grantCategory);
        return arguments;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }
        if (mPackageName == null || mPermGroupName == null) {
            if (mPackageName == null) {
                Log.e(LOG_TAG, "Package name is null: " + Intent.EXTRA_PACKAGE_NAME);
            }
            if (mPermGroupName == null) {
                Log.e(LOG_TAG, "Permission group is null: " + Intent.EXTRA_PERMISSION_GROUP_NAME);
            }
            final Activity activity = getActivity();
            Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }
        mIsStorageGroup = Objects.equals(mPermGroupName, STORAGE);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mPackageLabel = BidiFormatter.getInstance().unicodeWrap(
                KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(), mPackageName,
                        mUser));
        mPermGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                mPermGroupName).toString();
        mPackageIcon = KotlinUtils.INSTANCE.getBadgedPackageIcon(getActivity().getApplication(),
                mPackageName, mUser);
        try {
            mForegroundCapableType = Utils.getForegroundCapableType(getContext(), mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Package " + mPackageName + " not found", e);
        }

        mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        AppPermissionViewModelFactory factory = new AppPermissionViewModelFactory(
                getActivity().getApplication(), mPackageName, mPermGroupName, mUser, mSessionId,
                mForegroundCapableType);
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionViewModel.class);
        Handler delayHandler = new Handler(Looper.getMainLooper());
        mViewModel.getButtonStateLiveData().observe(this, buttonState -> {
            if (mIsInitialLoad) {
                setRadioButtonsState(buttonState);
            } else {
                delayHandler.removeCallbacksAndMessages(null);
                delayHandler.postDelayed(() -> setRadioButtonsState(buttonState), POST_DELAY_MS);
            }
        });
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mIsInitialLoad = true;
        setHeader(mPackageIcon, mPackageLabel, null,
                getString(R.string.app_permissions_decor_title));
        createPreferences();
        updatePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferences();
    }

    public void createPreferences() {
        PreferenceScreen screen = getPreferenceScreen();
        Context context = getContext();
        screen.removeAll();

        PackageInfo packageInfo = getPackageInfo(getActivity(), mPackageName);
        AppPermissions appPermissions = new AppPermissions(getActivity(), packageInfo, true,
                () -> getActivity().finish());
        AppPermissionGroup group = appPermissions.getPermissionGroup(mPermGroupName);
        Drawable icon = Utils.loadDrawable(context.getPackageManager(),
                group.getIconPkg(), group.getIconResId());

        screen.addPreference(createHeaderLineTwoPreference(context));

        Preference permHeader = new Preference(context);
        permHeader.setTitle(mPermGroupLabel);
        permHeader.setSummary(context.getString(R.string.app_permission_header, mPermGroupLabel));
        permHeader.setSelectable(false);
        permHeader.setIcon(Utils.applyTint(getContext(), icon, android.R.attr.colorControlNormal));
        screen.addPreference(permHeader);

        mAllowButton = new RadioButtonPreference(context, R.string.app_permission_button_allow);
        mAllowAlwaysButton =
                new RadioButtonPreference(context, R.string.app_permission_button_allow_always);
        mAllowForegroundButton =
                new RadioButtonPreference(context, R.string.app_permission_button_allow_foreground);
        mAskOneTimeButton = new RadioButtonPreference(context, R.string.app_permission_button_ask);
        mAskButton = new RadioButtonPreference(context, R.string.app_permission_button_ask);
        mDenyButton = new RadioButtonPreference(context, R.string.app_permission_button_deny);
        mDenyForegroundButton =
                new RadioButtonPreference(context, R.string.app_permission_button_deny);

        for (Preference preference : new Preference[] {
                mAllowButton,
                mAllowAlwaysButton,
                mAllowForegroundButton,
                mAskOneTimeButton,
                mAskButton,
                mDenyButton,
                mDenyForegroundButton}) {
            preference.setVisible(false);
            preference.setIcon(android.R.color.transparent);
            screen.addPreference(preference);
        }
    }

    public void updatePreferences() {
        if (mViewModel.getButtonStateLiveData().getValue() != null) {
            setRadioButtonsState(mViewModel.getButtonStateLiveData().getValue());
        }
    }

    private void setRadioButtonsState(Map<ButtonType, ButtonState> states) {
        if (states == null && mViewModel.getButtonStateLiveData().isInitialized()) {
            pressBack(this);
            Log.w(LOG_TAG, "invalid package " + mPackageName + " or perm group "
                    + mPermGroupName);
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            return;
        } else if (states == null) {
            return;
        }

        mAllowButton.setOnPreferenceClickListener((v) -> {
            mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
            setResult(GRANTED_ALWAYS);
            return false;
        });
        mAllowAlwaysButton.setOnPreferenceClickListener((v) -> {
            if (mIsStorageGroup) {
                showConfirmDialog(ChangeRequest.GRANT_All_FILE_ACCESS,
                        R.string.special_file_access_dialog, -1, false);
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_ALWAYS);
            }
            setResult(GRANTED_ALWAYS);
            return false;
        });
        mAllowForegroundButton.setOnPreferenceClickListener((v) -> {
            if (mIsStorageGroup) {
                mViewModel.setAllFilesAccess(false);
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_BOTH,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW);
                setResult(GRANTED_ALWAYS);
                return false;
            } else {
                mViewModel.requestChange(false, this, this, ChangeRequest.GRANT_FOREGROUND_ONLY,
                        APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ALLOW_FOREGROUND);
                setResult(GRANTED_FOREGROUND_ONLY);
                return false;
            }
        });
        // mAskOneTimeButton only shows if checked hence should do nothing
        mAskButton.setOnPreferenceClickListener((v) -> {
            mViewModel.requestChange(true, this, this, ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__ASK_EVERY_TIME);
            setResult(DENIED);
            return false;
        });
        mDenyButton.setOnPreferenceClickListener((v) -> {
            mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_BOTH,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
            return false;
        });
        mDenyForegroundButton.setOnPreferenceClickListener((v) -> {
            mViewModel.requestChange(false, this, this, ChangeRequest.REVOKE_FOREGROUND,
                    APP_PERMISSION_FRAGMENT_ACTION_REPORTED__BUTTON_PRESSED__DENY_FOREGROUND);
            setResult(DENIED_DO_NOT_ASK_AGAIN);
            return false;
        });

        setButtonState(mAllowButton, states.get(ButtonType.ALLOW));
        setButtonState(mAllowAlwaysButton, states.get(ButtonType.ALLOW_ALWAYS));
        setButtonState(mAllowForegroundButton, states.get(ButtonType.ALLOW_FOREGROUND));
        setButtonState(mAskOneTimeButton, states.get(ButtonType.ASK_ONCE));
        setButtonState(mAskButton, states.get(ButtonType.ASK));
        setButtonState(mDenyButton, states.get(ButtonType.DENY));
        setButtonState(mDenyForegroundButton, states.get(ButtonType.DENY_FOREGROUND));

        mIsInitialLoad = false;
    }

    private void setButtonState(RadioButtonPreference button, AppPermissionViewModel.ButtonState state) {
        button.setVisible(state.isShown());
        if (state.isShown()) {
            button.setChecked(state.isChecked());
            button.setEnabled(state.isEnabled());
        }
        if (state.isShown() && state.isChecked()) {
            scrollToPreference(button);
        }
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
        headerLineTwo.setTitle(mPackageLabel);
        headerLineTwo.setIcon(mPackageIcon);
        return headerLineTwo;
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

    private void setResult(@GrantPermissionsViewHandler.Result int result) {
        Intent intent = new Intent()
                .putExtra(EXTRA_RESULT_PERMISSION_INTERACTED, mPermGroupName)
                .putExtra(EXTRA_RESULT_PERMISSION_RESULT, result);
        getActivity().setResult(Activity.RESULT_OK, intent);
    }

    /**
     * Show a dialog that warns the user that they are about to revoke permissions that were
     * granted by default, or that they are about to grant full file access to an app.
     *
     *
     * The order of operation to revoke a permission granted by default is:
     * 1. `showConfirmDialog`
     * 1. [ConfirmDialog.onCreateDialog]
     * 1. [AppPermissionViewModel.onDenyAnyWay] or [AppPermissionViewModel.onConfirmFileAccess]
     * TODO: Remove once data can be passed between dialogs and fragments with nav component
     *
     * @param changeRequest Whether background or foreground should be changed
     * @param messageId     The Id of the string message to show
     * @param buttonPressed Button which was pressed to initiate the dialog, one of
     *                      AppPermissionFragmentActionReported.button_pressed constants
     * @param oneTime       Whether the one-time (ask) button was clicked rather than the deny
     *                      button
     */
    @Override
    public void showConfirmDialog(ChangeRequest changeRequest, @StringRes int messageId,
            int buttonPressed, boolean oneTime) {
        Bundle args = getArguments().deepCopy();
        args.putInt(ConfirmDialog.MSG, messageId);
        args.putSerializable(ConfirmDialog.CHANGE_REQUEST, changeRequest);
        args.putInt(ConfirmDialog.BUTTON, buttonPressed);
        args.putBoolean(ConfirmDialog.ONE_TIME, oneTime);
        ConfirmDialog defaultDenyDialog = new ConfirmDialog();
        defaultDenyDialog.setCancelable(true);
        defaultDenyDialog.setArguments(args);
        defaultDenyDialog.setTargetFragment(this, 0);
        defaultDenyDialog.show(getFragmentManager(),
                ConfirmDialog.class.getName());
    }

    /**
     * A dialog warning the user that they are about to deny a permission that was granted by
     * default, or that they are denying a permission on a Pre-M app
     *
     * @see AppPermissionViewModel.ConfirmDialogShowingFragment#showConfirmDialog(ChangeRequest,
     * int, int, boolean)
     * @see #showConfirmDialog(ChangeRequest, int, int)
     */
    public static class ConfirmDialog extends DialogFragment {
        static final String MSG = ConfirmDialog.class.getName() + ".arg.msg";
        static final String CHANGE_REQUEST = ConfirmDialog.class.getName()
                + ".arg.changeRequest";
        private static final String KEY = ConfirmDialog.class.getName() + ".arg.key";
        private static final String BUTTON = ConfirmDialog.class.getName() + ".arg.button";
        private static final String ONE_TIME = ConfirmDialog.class.getName() + ".arg.onetime";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AppPermissionFragment fragment = (AppPermissionFragment) getTargetFragment();
            boolean isGrantFileAccess = getArguments().getSerializable(CHANGE_REQUEST)
                    == ChangeRequest.GRANT_All_FILE_ACCESS;
            int positiveButtonStringResId = R.string.grant_dialog_button_deny_anyway;
            if (isGrantFileAccess) {
                positiveButtonStringResId = R.string.grant_dialog_button_allow;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                    .setMessage(getArguments().getInt(MSG))
                    .setNegativeButton(R.string.cancel,
                            (DialogInterface dialog, int which) -> dialog.cancel())
                    .setPositiveButton(positiveButtonStringResId,
                            (DialogInterface dialog, int which) -> {
                                if (isGrantFileAccess) {
                                    fragment.mViewModel.setAllFilesAccess(true);
                                } else {
                                    fragment.mViewModel.onDenyAnyWay((ChangeRequest)
                                                    getArguments().getSerializable(CHANGE_REQUEST),
                                            getArguments().getInt(BUTTON),
                                            getArguments().getBoolean(ONE_TIME));
                                }
                            });
            Dialog d = b.create();
            d.setCanceledOnTouchOutside(true);
            return d;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            AppPermissionFragment fragment = (AppPermissionFragment) getTargetFragment();
            fragment.setRadioButtonsState(fragment.mViewModel.getButtonStateLiveData().getValue());
        }
    }
}
