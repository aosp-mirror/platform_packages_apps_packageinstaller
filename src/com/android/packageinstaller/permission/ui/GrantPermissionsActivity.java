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

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.packageinstaller.PermissionControllerStatsLog.GRANT_PERMISSIONS_ACTIVITY_BUTTON_ACTIONS;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_DENIED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_POLICY_FIXED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_RESTRICTED_PERMISSION;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_USER_FIXED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE;
import static com.android.packageinstaller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED;
import static com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.packageinstaller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.packageinstaller.permission.utils.Utils.getRequestMessage;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.text.Html;
import android.text.Spanned;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.DeviceUtils;
import com.android.packageinstaller.PermissionControllerStatsLog;
import com.android.packageinstaller.permission.model.AppPermissionGroup;
import com.android.packageinstaller.permission.model.AppPermissions;
import com.android.packageinstaller.permission.model.Permission;
import com.android.packageinstaller.permission.ui.auto.GrantPermissionsAutoViewHandler;
import com.android.packageinstaller.permission.utils.ArrayUtils;
import com.android.packageinstaller.permission.utils.PackageRemovalMonitor;
import com.android.packageinstaller.permission.utils.SafetyNetLogger;
import com.android.permissioncontroller.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GrantPermissionsActivity extends Activity
        implements GrantPermissionsViewHandler.ResultListener {

    private static final String LOG_TAG = "GrantPermissionsActivity";

    private static final String KEY_REQUEST_ID = GrantPermissionsActivity.class.getName()
            + "_REQUEST_ID";

    public static int NUM_BUTTONS = 5;
    public static int LABEL_ALLOW_BUTTON = 0;
    public static int LABEL_ALLOW_ALWAYS_BUTTON = 1;
    public static int LABEL_ALLOW_FOREGROUND_BUTTON = 2;
    public static int LABEL_DENY_BUTTON = 3;
    public static int LABEL_DENY_AND_DONT_ASK_AGAIN_BUTTON = 4;

    /** Unique Id of a request */
    private long mRequestId;

    private String[] mRequestedPermissions;
    private CharSequence[] mButtonLabels;

    private ArrayMap<Pair<String, Boolean>, GroupState> mRequestGrantPermissionGroups =
            new ArrayMap<>();

    private GrantPermissionsViewHandler mViewHandler;
    private AppPermissions mAppPermissions;

    boolean mResultSet;

    /**
     * Listens for changes to the permission of the app the permissions are currently getting
     * granted to. {@code null} when unregistered.
     */
    private @Nullable PackageManager.OnPermissionsChangedListener mPermissionChangeListener;

    /**
     * Listens for changes to the app the permissions are currently getting granted to. {@code null}
     * when unregistered.
     */
    private @Nullable PackageRemovalMonitor mPackageRemovalMonitor;

    /** Package that requested the permission grant */
    private String mCallingPackage;
    /** uid of {@link #mCallingPackage} */
    private int mCallingUid;

    private int getPermissionPolicy() {
        DevicePolicyManager devicePolicyManager = getSystemService(DevicePolicyManager.class);
        return devicePolicyManager.getPermissionPolicy(null);
    }

    /**
     * Try to add a single permission that is requested to be granted.
     *
     * <p>This does <u>not</u> expand the permissions into the {@link #computeAffectedPermissions
     * affected permissions}.
     *
     * @param group The group the permission belongs to (might be a background permission group)
     * @param permName The name of the permission to add
     * @param isFirstInstance Is this the first time the groupStates get created
     */
    private void addRequestedPermissions(AppPermissionGroup group, String permName,
            boolean isFirstInstance) {
        if (!group.isGrantingAllowed()) {
            reportRequestResult(permName,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
            // Skip showing groups that we know cannot be granted.
            return;
        }

        Permission permission = group.getPermission(permName);

        // If the permission is restricted it does not show in the UI and
        // is not added to the group at all, so check that first.
        if (permission == null && ArrayUtils.contains(
                mAppPermissions.getPackageInfo().requestedPermissions, permName)) {
            reportRequestResult(permName,
                  PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_RESTRICTED_PERMISSION);
            return;
        // We allow the user to choose only non-fixed permissions. A permission
        // is fixed either by device policy or the user denying with prejudice.
        } else if (group.isUserFixed()) {
            reportRequestResult(permName,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_USER_FIXED);
            return;
        } else if (group.isPolicyFixed() && !group.areRuntimePermissionsGranted()
                || permission.isPolicyFixed()) {
            reportRequestResult(permName,
                    PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_POLICY_FIXED);
            return;
        }

        Pair<String, Boolean> groupKey = new Pair<>(group.getName(),
                group.isBackgroundGroup());

        GroupState state = mRequestGrantPermissionGroups.get(groupKey);
        if (state == null) {
            state = new GroupState(group);
            mRequestGrantPermissionGroups.put(groupKey, state);
        }
        state.affectedPermissions = ArrayUtils.appendString(
                state.affectedPermissions, permName);

        boolean skipGroup = false;
        switch (getPermissionPolicy()) {
            case DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT: {
                final String[] filterPermissions = new String[]{permName};
                group.grantRuntimePermissions(false, filterPermissions);
                group.setPolicyFixed(filterPermissions);
                state.mState = GroupState.STATE_ALLOWED;
                skipGroup = true;

                reportRequestResult(permName,
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED);
            } break;

            case DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY: {
                final String[] filterPermissions = new String[]{permName};
                group.setPolicyFixed(filterPermissions);
                state.mState = GroupState.STATE_DENIED;
                skipGroup = true;

                reportRequestResult(permName,
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_DENIED);
            } break;

            default: {
                if (group.areRuntimePermissionsGranted()) {
                    group.grantRuntimePermissions(false, new String[]{permName});
                    state.mState = GroupState.STATE_ALLOWED;
                    skipGroup = true;

                    reportRequestResult(permName,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED);
                }
            } break;
        }

        if (skipGroup && isFirstInstance) {
            // Only allow to skip groups when this is the first time the dialog was created.
            // Otherwise the number of groups changes between instances of the dialog.
            state.mState = GroupState.STATE_SKIPPED;
        }
    }

    /**
     * Report the result of a grant of a permission.
     *
     * @param permission The permission that was granted or denied
     * @param result The permission grant result
     */
    private void reportRequestResult(@NonNull String permission, int result) {
        boolean isImplicit = !ArrayUtils.contains(mRequestedPermissions, permission);

        Log.v(LOG_TAG,
                "Permission grant result requestId=" + mRequestId + " callingUid=" + mCallingUid
                        + " callingPackage=" + mCallingPackage + " permission=" + permission
                        + " isImplicit=" + isImplicit + " result=" + result);

        PermissionControllerStatsLog.write(
                PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED, mRequestId,
                mCallingUid, mCallingPackage, permission, isImplicit, result);
    }

    /**
     * Report the result of a grant of a permission.
     *
     * @param permissions The permissions that were granted or denied
     * @param result The permission grant result
     */
    private void reportRequestResult(@NonNull String[] permissions, int result) {
        for (String permission : permissions) {
            reportRequestResult(permission, result);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle == null) {
            mRequestId = new Random().nextLong();
        } else {
            mRequestId = icicle.getLong(KEY_REQUEST_ID);
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        // Cache this as this can only read on onCreate, not later.
        mCallingPackage = getCallingPackage();

        setFinishOnTouchOutside(false);

        setTitle(R.string.permission_request_title);

        mRequestedPermissions = getIntent().getStringArrayExtra(
                PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);
        if (mRequestedPermissions == null) {
            mRequestedPermissions = new String[0];
        }

        final int requestedPermCount = mRequestedPermissions.length;

        if (requestedPermCount == 0) {
            setResultAndFinish();
            return;
        }

        PackageInfo callingPackageInfo = getCallingPackageInfo();

        if (callingPackageInfo == null || callingPackageInfo.requestedPermissions == null
                || callingPackageInfo.requestedPermissions.length <= 0) {
            setResultAndFinish();
            return;
        }

        // Don't allow legacy apps to request runtime permissions.
        if (callingPackageInfo.applicationInfo.targetSdkVersion < Build.VERSION_CODES.M) {
            // Returning empty arrays means a cancellation.
            mRequestedPermissions = new String[0];
            setResultAndFinish();
            return;
        }

        mCallingUid = callingPackageInfo.applicationInfo.uid;

        UserHandle userHandle = UserHandle.getUserHandleForUid(mCallingUid);

        if (DeviceUtils.isTelevision(this)) {
            mViewHandler = new com.android.packageinstaller.permission.ui.television
                    .GrantPermissionsViewHandlerImpl(this,
                    mCallingPackage).setResultListener(this);
        } else if (DeviceUtils.isWear(this)) {
            mViewHandler = new GrantPermissionsWatchViewHandler(this).setResultListener(this);
        } else if (DeviceUtils.isAuto(this)) {
            mViewHandler = new GrantPermissionsAutoViewHandler(this, mCallingPackage, userHandle)
                    .setResultListener(this);
        } else {
            mViewHandler = new com.android.packageinstaller.permission.ui.handheld
                    .GrantPermissionsViewHandlerImpl(this, mCallingPackage, userHandle)
                    .setResultListener(this);
        }

        mAppPermissions = new AppPermissions(this, callingPackageInfo, false,
                new Runnable() {
                    @Override
                    public void run() {
                        setResultAndFinish();
                    }
                });

        for (String requestedPermission : mRequestedPermissions) {
            if (requestedPermission == null) {
                continue;
            }

            ArrayList<String> affectedPermissions =
                    computeAffectedPermissions(requestedPermission);

            int numAffectedPermissions = affectedPermissions.size();
            for (int i = 0; i < numAffectedPermissions; i++) {
                AppPermissionGroup group =
                        mAppPermissions.getGroupForPermission(affectedPermissions.get(i));
                if (group == null) {
                    reportRequestResult(affectedPermissions.get(i),
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED);

                    continue;
                }

                addRequestedPermissions(group, affectedPermissions.get(i), icicle == null);
            }
        }

        int numGroupStates = mRequestGrantPermissionGroups.size();
        for (int groupStateNum = 0; groupStateNum < numGroupStates; groupStateNum++) {
            GroupState groupState = mRequestGrantPermissionGroups.valueAt(groupStateNum);
            AppPermissionGroup group = groupState.mGroup;

            // Restore permission group state after lifecycle events
            if (icicle != null) {
                groupState.mState = icicle.getInt(
                        getInstanceStateKey(mRequestGrantPermissionGroups.keyAt(groupStateNum)),
                        groupState.mState);
            }

            // Do not attempt to grant background access if foreground access is not either already
            // granted or requested
            if (group.isBackgroundGroup()) {
                // Check if a foreground permission is already granted
                boolean foregroundGroupAlreadyGranted = mAppPermissions.getPermissionGroup(
                        group.getName()).areRuntimePermissionsGranted();
                boolean hasForegroundRequest = (getForegroundGroupState(group.getName()) != null);

                if (!foregroundGroupAlreadyGranted && !hasForegroundRequest) {
                    // The background permission cannot be granted at this time
                    int numPermissions = groupState.affectedPermissions.length;
                    for (int permissionNum = 0; permissionNum < numPermissions; permissionNum++) {
                        Log.w(LOG_TAG,
                                "Cannot grant " + groupState.affectedPermissions[permissionNum]
                                        + " as the matching foreground permission is not already "
                                        + "granted.");
                    }

                    groupState.mState = GroupState.STATE_SKIPPED;

                    reportRequestResult(groupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED);
                }
            }
        }

        setContentView(mViewHandler.createView());

        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        mViewHandler.updateWindowAttributes(layoutParams);
        window.setAttributes(layoutParams);

        // Restore UI state after lifecycle events. This has to be before
        // showNextPermissionGroupGrantRequest is called. showNextPermissionGroupGrantRequest might
        // update the UI and the UI behaves differently for updates and initial creations.
        if (icicle != null) {
            mViewHandler.loadInstanceState(icicle);
        }

        if (!showNextPermissionGroupGrantRequest()) {
            setResultAndFinish();
        }
    }

    /**
     * Update the {@link #mRequestedPermissions} if the system reports them as granted.
     *
     * <p>This also updates the {@link #mAppPermissions} state and switches to the next group grant
     * request if the current group becomes granted.
     */
    private void updateIfPermissionsWereGranted() {
        PackageManager pm = getPackageManager();

        boolean mightShowNextGroup = true;
        int numGroupStates = mRequestGrantPermissionGroups.size();
        for (int i = 0; i < numGroupStates; i++) {
            GroupState groupState = mRequestGrantPermissionGroups.valueAt(i);

            if (groupState == null || groupState.mState != GroupState.STATE_UNKNOWN) {
                // Group has already been approved / denied via the UI by the user
                continue;
            }

            boolean allAffectedPermissionsOfThisGroupAreGranted = true;

            if (groupState.affectedPermissions == null) {
                // It is not clear which permissions belong to this group, hence never skip this
                // view
                allAffectedPermissionsOfThisGroupAreGranted = false;
            } else {
                for (int permNum = 0; permNum < groupState.affectedPermissions.length;
                        permNum++) {
                    if (pm.checkPermission(groupState.affectedPermissions[permNum], mCallingPackage)
                            == PERMISSION_DENIED) {
                        allAffectedPermissionsOfThisGroupAreGranted = false;
                        break;
                    }
                }
            }

            if (allAffectedPermissionsOfThisGroupAreGranted) {
                groupState.mState = GroupState.STATE_ALLOWED;

                if (mightShowNextGroup) {
                    // The UI currently displays the first group with
                    // mState == STATE_UNKNOWN. So we are switching to next group until we
                    // could not allow a group that was still unknown
                    if (!showNextPermissionGroupGrantRequest()) {
                        setResultAndFinish();
                    }
                }
            } else {
                mightShowNextGroup = false;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            mPermissionChangeListener = new PermissionChangeListener();
        } catch (NameNotFoundException e) {
            setResultAndFinish();
            return;
        }
        PackageManager pm = getPackageManager();
        pm.addOnPermissionsChangeListener(mPermissionChangeListener);

        // get notified when the package is removed
        mPackageRemovalMonitor = new PackageRemovalMonitor(this, mCallingPackage) {
            @Override
            public void onPackageRemoved() {
                Log.w(LOG_TAG, mCallingPackage + " was uninstalled");

                finish();
            }
        };
        mPackageRemovalMonitor.register();

        // check if the package was removed while this activity was not started
        try {
            pm.getPackageInfo(mCallingPackage, 0);
        } catch (NameNotFoundException e) {
            Log.w(LOG_TAG, mCallingPackage + " was uninstalled while this activity was stopped", e);
            finish();
        }

        updateIfPermissionsWereGranted();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mPackageRemovalMonitor != null) {
            mPackageRemovalMonitor.unregister();
            mPackageRemovalMonitor = null;
        }

        if (mPermissionChangeListener != null) {
            getPackageManager().removeOnPermissionsChangeListener(mPermissionChangeListener);
            mPermissionChangeListener = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View rootView = getWindow().getDecorView();
        if (rootView.getTop() != 0) {
            // We are animating the top view, need to compensate for that in motion events.
            ev.setLocation(ev.getX(), ev.getY() - rootView.getTop());
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Compose a key that stores the GroupState.mState in the instance state.
     *
     * @param requestGrantPermissionGroupsKey The key of the permission group
     *
     * @return A unique key to be used in the instance state
     */
    private static String getInstanceStateKey(
            Pair<String, Boolean> requestGrantPermissionGroupsKey) {
        return GrantPermissionsActivity.class.getName() + "_"
                + requestGrantPermissionGroupsKey.first + "_"
                + requestGrantPermissionGroupsKey.second;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mViewHandler.saveInstanceState(outState);

        outState.putLong(KEY_REQUEST_ID, mRequestId);

        int numGroups = mRequestGrantPermissionGroups.size();
        for (int i = 0; i < numGroups; i++) {
            int state = mRequestGrantPermissionGroups.valueAt(i).mState;

            if (state != GroupState.STATE_UNKNOWN) {
                outState.putInt(getInstanceStateKey(mRequestGrantPermissionGroups.keyAt(i)), state);
            }
        }
    }

    /**
     * @return the background group state for the permission group with the {@code name}
     */
    private GroupState getBackgroundGroupState(String name) {
        return mRequestGrantPermissionGroups.get(new Pair<>(name, true));
    }

    /**
     * @return the foreground group state for the permission group with the {@code name}
     */
    private GroupState getForegroundGroupState(String name) {
        return mRequestGrantPermissionGroups.get(new Pair<>(name, false));
    }

    private boolean shouldShowRequestForGroupState(GroupState groupState) {
        if (groupState.mState == GroupState.STATE_SKIPPED) {
            return false;
        }

        GroupState foregroundGroup = getForegroundGroupState(groupState.mGroup.getName());
        if (groupState.mGroup.isBackgroundGroup()
                && (foregroundGroup != null && shouldShowRequestForGroupState(foregroundGroup))) {
            // If an app requests both foreground and background permissions of the same group,
            // we only show one request
            return false;
        }

        return true;
    }

    private boolean showNextPermissionGroupGrantRequest() {
        int numGroupStates = mRequestGrantPermissionGroups.size();
        int numGrantRequests = 0;
        for (int i = 0; i < numGroupStates; i++) {
            if (shouldShowRequestForGroupState(mRequestGrantPermissionGroups.valueAt(i))) {
                numGrantRequests++;
            }
        }

        int currentIndex = 0;
        for (GroupState groupState : mRequestGrantPermissionGroups.values()) {
            if (!shouldShowRequestForGroupState(groupState)) {
                continue;
            }

            if (groupState.mState == GroupState.STATE_UNKNOWN) {
                GroupState foregroundGroupState;
                GroupState backgroundGroupState;
                if (groupState.mGroup.isBackgroundGroup()) {
                    backgroundGroupState = groupState;
                    foregroundGroupState = getForegroundGroupState(groupState.mGroup.getName());
                } else {
                    foregroundGroupState = groupState;
                    backgroundGroupState = getBackgroundGroupState(groupState.mGroup.getName());
                }

                CharSequence appLabel = mAppPermissions.getAppLabel();

                Icon icon;
                try {
                    icon = Icon.createWithResource(groupState.mGroup.getIconPkg(),
                            groupState.mGroup.getIconResId());
                } catch (Resources.NotFoundException e) {
                    Log.e(LOG_TAG, "Cannot load icon for group" + groupState.mGroup.getName(), e);
                    icon = null;
                }

                // If no background permissions are granted yet, we need to ask for background
                // permissions
                boolean needBackgroundPermission = false;
                boolean isBackgroundPermissionUserSet = false;
                if (backgroundGroupState != null) {
                    if (!backgroundGroupState.mGroup.areRuntimePermissionsGranted()) {
                        needBackgroundPermission = true;
                        isBackgroundPermissionUserSet = backgroundGroupState.mGroup.isUserSet();
                    }
                }

                // If no foreground permissions are granted yet, we need to ask for foreground
                // permissions
                boolean needForegroundPermission = false;
                boolean isForegroundPermissionUserSet = false;
                if (foregroundGroupState != null) {
                    if (!foregroundGroupState.mGroup.areRuntimePermissionsGranted()) {
                        needForegroundPermission = true;
                        isForegroundPermissionUserSet = foregroundGroupState.mGroup.isUserSet();
                    }
                }

                // The button doesn't show when its label is null
                mButtonLabels = new CharSequence[NUM_BUTTONS];
                mButtonLabels[LABEL_ALLOW_BUTTON] = getString(R.string.grant_dialog_button_allow);
                mButtonLabels[LABEL_ALLOW_ALWAYS_BUTTON] = null;
                mButtonLabels[LABEL_ALLOW_FOREGROUND_BUTTON] = null;
                mButtonLabels[LABEL_DENY_BUTTON] = getString(R.string.grant_dialog_button_deny);
                if (isForegroundPermissionUserSet || isBackgroundPermissionUserSet) {
                    mButtonLabels[LABEL_DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                            getString(R.string.grant_dialog_button_deny_and_dont_ask_again);
                } else {
                    mButtonLabels[LABEL_DENY_AND_DONT_ASK_AGAIN_BUTTON] = null;
                }

                int messageId;
                int detailMessageId = 0;
                if (needForegroundPermission) {
                    messageId = groupState.mGroup.getRequest();

                    if (groupState.mGroup.hasPermissionWithBackgroundMode()) {
                        mButtonLabels[LABEL_ALLOW_BUTTON] = null;
                        mButtonLabels[LABEL_ALLOW_FOREGROUND_BUTTON] =
                                getString(R.string.grant_dialog_button_allow_foreground);
                        if (needBackgroundPermission) {
                            mButtonLabels[LABEL_ALLOW_ALWAYS_BUTTON] =
                                    getString(R.string.grant_dialog_button_allow_always);
                            if (isForegroundPermissionUserSet || isBackgroundPermissionUserSet) {
                                mButtonLabels[LABEL_DENY_BUTTON] = null;
                            }
                        }
                    } else {
                        detailMessageId = groupState.mGroup.getRequestDetail();
                    }
                } else {
                    if (needBackgroundPermission) {
                        messageId = groupState.mGroup.getBackgroundRequest();
                        detailMessageId = groupState.mGroup.getBackgroundRequestDetail();
                        mButtonLabels[LABEL_ALLOW_BUTTON] =
                                getString(R.string.grant_dialog_button_allow_background);
                        mButtonLabels[LABEL_DENY_BUTTON] =
                                getString(R.string.grant_dialog_button_deny_background);
                        mButtonLabels[LABEL_DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                getString(R.string
                                        .grant_dialog_button_deny_background_and_dont_ask_again);
                    } else {
                        // Not reached as the permissions should be auto-granted
                        return false;
                    }
                }

                CharSequence message = getRequestMessage(appLabel, groupState.mGroup, this,
                        messageId);

                Spanned detailMessage = null;
                if (detailMessageId != 0) {
                    try {
                        detailMessage = Html.fromHtml(
                                getPackageManager().getResourcesForApplication(
                                        groupState.mGroup.getDeclaringPackage()).getString(
                                        detailMessageId), 0);
                    } catch (NameNotFoundException ignored) {
                    }
                }

                // Set the permission message as the title so it can be announced.
                setTitle(message);

                mViewHandler.updateUi(groupState.mGroup.getName(), numGrantRequests, currentIndex,
                        icon, message, detailMessage, mButtonLabels);

                return true;
            }

            if (groupState.mState != GroupState.STATE_SKIPPED) {
                currentIndex++;
            }
        }

        return false;
    }

    @Override
    public void onPermissionGrantResult(String name,
            @GrantPermissionsViewHandler.Result int result) {
        logGrantPermissionActivityButtons(name, result);
        GroupState foregroundGroupState = getForegroundGroupState(name);
        GroupState backgroundGroupState = getBackgroundGroupState(name);

        if (result == GRANTED_ALWAYS || result == GRANTED_FOREGROUND_ONLY
                || result == DENIED_DO_NOT_ASK_AGAIN) {
            KeyguardManager kgm = getSystemService(KeyguardManager.class);

            if (kgm.isDeviceLocked()) {
                kgm.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                            @Override
                            public void onDismissError() {
                                Log.e(LOG_TAG, "Cannot dismiss keyguard perm=" + name + " result="
                                        + result);
                            }

                            @Override
                            public void onDismissCancelled() {
                                // do nothing (i.e. stay at the current permission group)
                            }

                            @Override
                            public void onDismissSucceeded() {
                                // Now the keyguard is dismissed, hence the device is not locked
                                // anymore
                                onPermissionGrantResult(name, result);
                            }
                        });

                return;
            }
        }

        switch (result) {
            case GRANTED_ALWAYS :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, true, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, true, false);
                }
                break;
            case GRANTED_FOREGROUND_ONLY :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, true, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, false);
                }
                break;
            case DENIED :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, false, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, false);
                }
                break;
            case DENIED_DO_NOT_ASK_AGAIN :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, false, true);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, true);
                }
                break;
        }

        if (!showNextPermissionGroupGrantRequest()) {
            setResultAndFinish();
        }
    }

    /**
     * Grants or revoked the affected permissions for a single {@link groupState}.
     *
     * @param groupState The group state with the permissions to grant/revoke
     * @param granted {@code true} if the permissions should be granted, {@code false} if they
     *        should be revoked
     * @param doNotAskAgain if the permissions should be revoked should be app be allowed to ask
     *        again for the same permissions?
     */
    private void onPermissionGrantResultSingleState(GroupState groupState, boolean granted,
            boolean doNotAskAgain) {
        if (groupState != null && groupState.mGroup != null
                && groupState.mState == GroupState.STATE_UNKNOWN) {
            if (granted) {
                groupState.mGroup.grantRuntimePermissions(doNotAskAgain,
                        groupState.affectedPermissions);
                groupState.mState = GroupState.STATE_ALLOWED;

                reportRequestResult(groupState.affectedPermissions,
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED);
            } else {
                groupState.mGroup.revokeRuntimePermissions(doNotAskAgain,
                        groupState.affectedPermissions);
                groupState.mState = GroupState.STATE_DENIED;

                reportRequestResult(groupState.affectedPermissions, doNotAskAgain
                        ?
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE
                        : PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        // We do not allow backing out.
        return keyCode == KeyEvent.KEYCODE_BACK;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)  {
        // We do not allow backing out.
        return keyCode == KeyEvent.KEYCODE_BACK;
    }

    @Override
    public void finish() {
        setResultIfNeeded(RESULT_CANCELED);
        super.finish();
    }

    private PackageInfo getCallingPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(mCallingPackage,
                    PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.i(LOG_TAG, "No package: " + mCallingPackage, e);
            return null;
        }
    }

    private void setResultIfNeeded(int resultCode) {
        if (!mResultSet) {
            mResultSet = true;
            logRequestedPermissionGroups();
            Intent result = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, mRequestedPermissions);

            PackageManager pm = getPackageManager();
            int numRequestedPermissions = mRequestedPermissions.length;
            int[] grantResults = new int[numRequestedPermissions];
            for (int i = 0; i < numRequestedPermissions; i++) {
                grantResults[i] = pm.checkPermission(mRequestedPermissions[i], mCallingPackage);
            }

            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, grantResults);
            setResult(resultCode, result);
        }
    }

    private void setResultAndFinish() {
        setResultIfNeeded(RESULT_OK);
        finish();
    }

    private void logRequestedPermissionGroups() {
        if (mRequestGrantPermissionGroups.isEmpty()) {
            return;
        }

        final int groupCount = mRequestGrantPermissionGroups.size();
        List<AppPermissionGroup> groups = new ArrayList<>(groupCount);
        for (GroupState groupState : mRequestGrantPermissionGroups.values()) {
            groups.add(groupState.mGroup);
        }

        SafetyNetLogger.logPermissionsRequested(mAppPermissions.getPackageInfo(), groups);
    }

    /**
     * Get the actually requested permissions when a permission is requested.
     *
     * <p>>In some cases requesting to grant a single permission requires the system to grant
     * additional permissions. E.g. before N-MR1 a single permission of a group caused the whole
     * group to be granted. Another case are permissions that are split into two. For apps that
     * target an SDK before the split, this method automatically adds the split off permission.
     *
     * @param permission The requested permission
     *
     * @return The actually requested permissions
     */
    private ArrayList<String> computeAffectedPermissions(String permission) {
        int requestingAppTargetSDK =
                mAppPermissions.getPackageInfo().applicationInfo.targetSdkVersion;

        // If a permission is split, all permissions the original permission is split into are
        // affected
        ArrayList<String> extendedBySplitPerms = new ArrayList<>();
        extendedBySplitPerms.add(permission);

        List<PermissionManager.SplitPermissionInfo> splitPerms = getSystemService(
                PermissionManager.class).getSplitPermissions();
        int numSplitPerms = splitPerms.size();
        for (int i = 0; i < numSplitPerms; i++) {
            PermissionManager.SplitPermissionInfo splitPerm = splitPerms.get(i);

            if (requestingAppTargetSDK < splitPerm.getTargetSdk()
                    && permission.equals(splitPerm.getSplitPermission())) {
                extendedBySplitPerms.addAll(splitPerm.getNewPermissions());
            }
        }

        // For <= N_MR1 apps all permissions of the groups of the requested permissions are affected
        if (requestingAppTargetSDK <= Build.VERSION_CODES.N_MR1) {
            ArrayList<String> extendedBySplitPermsAndGroup = new ArrayList<>();

            int numExtendedBySplitPerms = extendedBySplitPerms.size();
            for (int splitPermNum = 0; splitPermNum < numExtendedBySplitPerms; splitPermNum++) {
                AppPermissionGroup group = mAppPermissions.getGroupForPermission(
                        extendedBySplitPerms.get(splitPermNum));

                if (group == null) {
                    continue;
                }

                ArrayList<Permission> permissionsInGroup = group.getPermissions();
                int numPermissionsInGroup = permissionsInGroup.size();
                for (int permNum = 0; permNum < numPermissionsInGroup; permNum++) {
                    extendedBySplitPermsAndGroup.add(permissionsInGroup.get(permNum).getName());
                }
            }

            return extendedBySplitPermsAndGroup;
        } else {
            return extendedBySplitPerms;
        }
    }

    private void logGrantPermissionActivityButtons(String permissionGroupName, int grantResult) {
        int clickedButton = 0;
        int presentedButtons = getButtonState();
        switch (grantResult) {
            case GRANTED_ALWAYS:
                if ((presentedButtons & (1 << LABEL_ALLOW_BUTTON)) != 0) {
                    clickedButton = 1 << LABEL_ALLOW_BUTTON;
                } else {
                    clickedButton = 1 << LABEL_ALLOW_ALWAYS_BUTTON;
                }
                break;
            case GRANTED_FOREGROUND_ONLY:
                clickedButton = 1 << LABEL_ALLOW_FOREGROUND_BUTTON;
                break;
            case DENIED:
                clickedButton = 1 << LABEL_DENY_BUTTON;
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                clickedButton = 1 << LABEL_DENY_AND_DONT_ASK_AGAIN_BUTTON;
                break;
            default:
                break;
        }

        PermissionControllerStatsLog.write(GRANT_PERMISSIONS_ACTIVITY_BUTTON_ACTIONS,
                permissionGroupName, mCallingUid, mCallingPackage, presentedButtons,
                clickedButton);
        Log.v(LOG_TAG, "Logged buttons presented and clicked permissionGroupName="
                + permissionGroupName + " uid=" + mCallingUid + " package=" + mCallingPackage
                + " presentedButtons=" + presentedButtons + " clickedButton=" + clickedButton);
    }

    private int getButtonState() {
        if (mButtonLabels == null) {
            return 0;
        }
        int buttonState = 0;
        for (int i = NUM_BUTTONS - 1; i >= 0; i--) {
            buttonState *= 2;
            if (mButtonLabels[i] != null) {
                buttonState++;
            }
        }
        return buttonState;
    }

    private static final class GroupState {
        static final int STATE_UNKNOWN = 0;
        static final int STATE_ALLOWED = 1;
        static final int STATE_DENIED = 2;
        static final int STATE_SKIPPED = 3;

        final AppPermissionGroup mGroup;
        int mState = STATE_UNKNOWN;

        /** Permissions of this group that need to be granted, null == no permissions of group */
        String[] affectedPermissions;

        GroupState(AppPermissionGroup group) {
            mGroup = group;
        }
    }

    private class PermissionChangeListener implements PackageManager.OnPermissionsChangedListener {
        final int mCallingPackageUid;

        PermissionChangeListener() throws NameNotFoundException {
            mCallingPackageUid = getPackageManager().getPackageUid(mCallingPackage, 0);
        }

        @Override
        public void onPermissionsChanged(int uid) {
            if (uid == mCallingPackageUid) {
                updateIfPermissionsWereGranted();
            }
        }
    }
}
