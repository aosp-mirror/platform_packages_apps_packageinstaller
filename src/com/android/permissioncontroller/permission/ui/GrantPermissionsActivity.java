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

package com.android.permissioncontroller.permission.ui;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permissioncontroller.PermissionControllerStatsLog.GRANT_PERMISSIONS_ACTIVITY_BUTTON_ACTIONS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_DENIED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_GRANTED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_POLICY_FIXED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_RESTRICTED_PERMISSION;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__IGNORED_USER_FIXED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE_IN_SETTINGS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_ONE_TIME;
import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_IGNORED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.LINKED_TO_SETTINGS;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_INTERACTED;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_RESULT_PERMISSION_RESULT;
import static com.android.permissioncontroller.permission.utils.Utils.getRequestMessage;

import android.Manifest;
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
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissions;
import com.android.permissioncontroller.permission.model.Permission;
import com.android.permissioncontroller.permission.ui.auto.GrantPermissionsAutoViewHandler;
import com.android.permissioncontroller.permission.ui.wear.GrantPermissionsWearViewHandler;
import com.android.permissioncontroller.permission.utils.ArrayUtils;
import com.android.permissioncontroller.permission.utils.PackageRemovalMonitor;
import com.android.permissioncontroller.permission.utils.SafetyNetLogger;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GrantPermissionsActivity extends Activity
        implements GrantPermissionsViewHandler.ResultListener {

    private static final String LOG_TAG = "GrantPermissionsActivity";

    private static final String KEY_REQUEST_ID = GrantPermissionsActivity.class.getName()
            + "_REQUEST_ID";
    private static final String KEY_PENDING_ACTIVITY_RESULT =
            GrantPermissionsActivity.class.getName() + "_PENDING_ACTIVITY_RESULT";
    public static final String ANNOTATION_ID = "link";

    public static final int NEXT_BUTTON = 11;
    public static final int ALLOW_BUTTON = 0;
    public static final int ALLOW_ALWAYS_BUTTON = 1;
    public static final int ALLOW_FOREGROUND_BUTTON = 2;
    public static final int DENY_BUTTON = 3;
    public static final int DENY_AND_DONT_ASK_AGAIN_BUTTON = 4;
    public static final int ALLOW_ONE_TIME_BUTTON = 5;
    public static final int NO_UPGRADE_BUTTON = 6;
    public static final int NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON = 7;
    public static final int NO_UPGRADE_OT_BUTTON = 8; // one-time
    public static final int NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON = 9; // one-time
    public static final int LINK_TO_SETTINGS = 10;

    private static final int APP_PERMISSION_REQUEST_CODE = 1;

    /** Unique Id of a request */
    private long mRequestId;

    private String[] mRequestedPermissions;
    private boolean[] mButtonVisibilities;
    private boolean mCouldHaveFgCapabilities;
    private boolean mPendingActivityResult;

    private ArrayMap<Pair<String, Boolean>, GroupState> mRequestGrantPermissionGroups =
            new ArrayMap<>();
    private ArraySet<String> mPermissionGroupsToSkip = new ArraySet<>();
    private Consumer<Intent> mActivityResultCallback;

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
    /** Notifier for auto-granted permissions */
    private AutoGrantPermissionsNotifier mAutoGrantPermissionsNotifier;
    private PackageInfo mCallingPackageInfo;

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
                group.grantRuntimePermissions(false, false, filterPermissions);
                group.setPolicyFixed(filterPermissions);
                state.mState = GroupState.STATE_ALLOWED;
                skipGroup = true;

                getAutoGrantNotifier().onPermissionAutoGranted(permName);
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
                    group.grantRuntimePermissions(false, false, new String[]{permName});
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
            mPendingActivityResult = icicle.getBoolean(KEY_PENDING_ACTIVITY_RESULT);
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        // Cache this as this can only read on onCreate, not later.
        mCallingPackage = getCallingPackage();
        try {
            mCouldHaveFgCapabilities = Utils.couldHaveForegroundCapabilities(this, mCallingPackage);
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "Calling package " + mCallingPackage + " not found", e);
        }

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

        mCallingPackageInfo = callingPackageInfo;

        mCallingUid = callingPackageInfo.applicationInfo.uid;

        UserHandle userHandle = UserHandle.getUserHandleForUid(mCallingUid);

        if (DeviceUtils.isTelevision(this)) {
            mViewHandler = new com.android.permissioncontroller.permission.ui.television
                    .GrantPermissionsViewHandlerImpl(this,
                    mCallingPackage).setResultListener(this);
        } else if (DeviceUtils.isWear(this)) {
            mViewHandler = new GrantPermissionsWearViewHandler(this).setResultListener(this);
        } else if (DeviceUtils.isAuto(this)) {
            mViewHandler = new GrantPermissionsAutoViewHandler(this, mCallingPackage)
                    .setResultListener(this);
        } else {
            mViewHandler = new com.android.permissioncontroller.permission.ui.handheld
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

                if (mAppPermissions.getPackageInfo().applicationInfo.targetSdkVersion
                        >= Build.VERSION_CODES.R && mRequestedPermissions.length > 1
                        && group.isBackgroundGroup()) {
                    Log.e(LOG_TAG, "Apps targeting " + Build.VERSION_CODES.R + " must"
                            + " have foreground permission before requesting background and must"
                            + " request background on its own.");
                    finish();
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
    protected void onResume() {
        if (!showNextPermissionGroupGrantRequest()) {
            setResultAndFinish();
        }
        super.onResume();
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
        outState.putBoolean(KEY_PENDING_ACTIVITY_RESULT, mPendingActivityResult);

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

        return !mPermissionGroupsToSkip.contains(groupState.mGroup.getName());
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
        List<GroupState> groupStates = new ArrayList<>(mRequestGrantPermissionGroups.values());
        Collections.sort(groupStates, (s1, s2) -> -Boolean.compare(s1.mGroup.supportsOneTimeGrant(),
                s2.mGroup.supportsOneTimeGrant()));
        for (GroupState groupState : groupStates) {
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

                mButtonVisibilities = new boolean[NEXT_BUTTON];
                mButtonVisibilities[ALLOW_BUTTON] = true;
                mButtonVisibilities[DENY_BUTTON] = true;
                mButtonVisibilities[ALLOW_ONE_TIME_BUTTON] =
                        groupState.mGroup.supportsOneTimeGrant();

                int messageId;
                int detailMessageId = 0;

                if (mAppPermissions.getPackageInfo().applicationInfo.targetSdkVersion
                        >= Build.VERSION_CODES.R) {
                    if (groupState.mGroup.hasPermissionWithBackgroundMode()
                            || groupState.mGroup.isBackgroundGroup()) {
                        if (needForegroundPermission && needBackgroundPermission) {
                            if (mCouldHaveFgCapabilities) {
                                sendToSettings(groupState);
                                return true;
                            }
                            // Shouldn't be reached as background must be requested as a singleton
                            return false;
                        } else if (needForegroundPermission) {
                            // Case: sdk >= R, BG/FG permission requesting FG only
                            messageId = groupState.mGroup.getRequest();
                            if (mCouldHaveFgCapabilities) {
                                sendToSettings(groupState);
                                return true;
                            }
                            mButtonVisibilities[ALLOW_BUTTON] = false;
                            mButtonVisibilities[ALLOW_FOREGROUND_BUTTON] = true;
                            mButtonVisibilities[DENY_BUTTON] =
                                    !isForegroundPermissionUserSet;
                            mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                    isForegroundPermissionUserSet;
                        } else if (needBackgroundPermission) {
                            // Case: sdk >= R, BG/FG permission requesting BG only
                            sendToSettings(groupState);
                            return true;
                        } else {
                            // Not reached as the permissions should be auto-granted
                            return false;
                        }
                    } else {
                        // Case: sdk >= R, Requesting normal permission
                        messageId = groupState.mGroup.getRequest();
                        mButtonVisibilities[DENY_BUTTON] =
                                !isForegroundPermissionUserSet;
                        mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                isForegroundPermissionUserSet;
                        if (groupState.mGroup.getName().equals(Manifest.permission_group.CAMERA)
                                || groupState.mGroup.getName().equals(
                                Manifest.permission_group.MICROPHONE)) {
                            mButtonVisibilities[ALLOW_BUTTON] = false;
                            if (mCouldHaveFgCapabilities
                                    || Utils.isEmergencyApp(this, mCallingPackage)) {
                                mButtonVisibilities[ALLOW_ALWAYS_BUTTON] = true;
                                mButtonVisibilities[ALLOW_ONE_TIME_BUTTON] = false;
                            } else {
                                mButtonVisibilities[ALLOW_FOREGROUND_BUTTON] = true;
                            }
                        }
                    }
                } else {
                    if (groupState.mGroup.hasPermissionWithBackgroundMode()
                            || groupState.mGroup.isBackgroundGroup()) {
                        if (mCouldHaveFgCapabilities) {
                            // Only allow granting of background location
                            messageId = groupState.mGroup.getBackgroundRequest();
                            detailMessageId = groupState.mGroup.getBackgroundRequestDetail();
                            mButtonVisibilities[ALLOW_BUTTON] = false;
                            mButtonVisibilities[ALLOW_ONE_TIME_BUTTON] = false;
                            mButtonVisibilities[DENY_BUTTON] =
                                    !isForegroundPermissionUserSet;
                            mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                    isForegroundPermissionUserSet;
                        } else {
                            if (needForegroundPermission && needBackgroundPermission) {
                                // Case: sdk < R, BG/FG permission requesting both
                                messageId = groupState.mGroup.getBackgroundRequest();
                                detailMessageId = groupState.mGroup.getBackgroundRequestDetail();
                                mButtonVisibilities[ALLOW_BUTTON] = false;
                                mButtonVisibilities[ALLOW_FOREGROUND_BUTTON] = true;
                                mButtonVisibilities[DENY_BUTTON] =
                                        !isForegroundPermissionUserSet;
                                mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                        isForegroundPermissionUserSet;
                            } else if (needForegroundPermission) {
                                // Case: sdk < R, BG/FG permission requesting FG only
                                messageId = groupState.mGroup.getRequest();
                                mButtonVisibilities[ALLOW_BUTTON] = false;
                                mButtonVisibilities[ALLOW_FOREGROUND_BUTTON] = true;
                                mButtonVisibilities[DENY_BUTTON] =
                                        !isForegroundPermissionUserSet;
                                mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                        isForegroundPermissionUserSet;
                            } else if (needBackgroundPermission) {
                                // Case: sdk < R, BG/FG permission requesting BG only
                                messageId = groupState.mGroup.getUpgradeRequest();
                                detailMessageId = groupState.mGroup.getUpgradeRequestDetail();
                                mButtonVisibilities[ALLOW_BUTTON] = false;
                                mButtonVisibilities[DENY_BUTTON] = false;
                                mButtonVisibilities[ALLOW_ONE_TIME_BUTTON] = false;
                                if (mAppPermissions.getPermissionGroup(
                                        groupState.mGroup.getName()).isOneTime()) {
                                    mButtonVisibilities[NO_UPGRADE_OT_BUTTON] =
                                            !isBackgroundPermissionUserSet;
                                    mButtonVisibilities[NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON] =
                                            isBackgroundPermissionUserSet;
                                } else {
                                    mButtonVisibilities[NO_UPGRADE_BUTTON] =
                                            !isBackgroundPermissionUserSet;
                                    mButtonVisibilities[NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON] =
                                            isBackgroundPermissionUserSet;
                                }
                            } else {
                                // Not reached as the permissions should be auto-granted
                                return false;
                            }
                        }
                    } else {
                        // Case: sdk < R, Requesting normal permission
                        messageId = groupState.mGroup.getRequest();
                        mButtonVisibilities[DENY_BUTTON] =
                                !isForegroundPermissionUserSet;
                        mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON] =
                                isForegroundPermissionUserSet;
                        if (groupState.mGroup.getName().equals(Manifest.permission_group.CAMERA)
                                || groupState.mGroup.getName().equals(
                                        Manifest.permission_group.MICROPHONE)) {
                            mButtonVisibilities[ALLOW_BUTTON] = false;
                            if (mCouldHaveFgCapabilities
                                    || Utils.isEmergencyApp(this, mCallingPackage)) {
                                mButtonVisibilities[ALLOW_ALWAYS_BUTTON] = true;
                                mButtonVisibilities[ALLOW_ONE_TIME_BUTTON] = false;
                            } else {
                                mButtonVisibilities[ALLOW_FOREGROUND_BUTTON] = true;
                            }
                        }
                    }
                }

                CharSequence message = getRequestMessage(appLabel, groupState.mGroup, this,
                        messageId);

                Spanned detailMessage = null;
                if (detailMessageId != 0) {
                    detailMessage =
                            new SpannableString(getText(detailMessageId));
                    Annotation[] annotations = detailMessage.getSpans(
                            0, detailMessage.length(), Annotation.class);
                    int numAnnotations = annotations.length;
                    for (int i = 0; i < numAnnotations; i++) {
                        Annotation annotation = annotations[i];
                        if (annotation.getValue().equals(ANNOTATION_ID)) {
                            int start = detailMessage.getSpanStart(annotation);
                            int end = detailMessage.getSpanEnd(annotation);
                            ClickableSpan clickableSpan = getLinkToAppPermissions(groupState);
                            SpannableString spannableString =
                                    new SpannableString(detailMessage);
                            spannableString.setSpan(clickableSpan, start, end, 0);
                            detailMessage = spannableString;
                            mButtonVisibilities[LINK_TO_SETTINGS] = true;
                            break;
                        }
                    }
                }

                // Set the permission message as the title so it can be announced.
                setTitle(message);

                mViewHandler.updateUi(groupState.mGroup.getName(), numGrantRequests, currentIndex,
                        icon, message, detailMessage, mButtonVisibilities);

                return true;
            }

            if (groupState.mState != GroupState.STATE_SKIPPED) {
                currentIndex++;
            }
        }

        return false;
    }

    private void sendToSettings(GroupState groupState) {
        if (mActivityResultCallback == null) {
            mPermissionGroupsToSkip.add(groupState.mGroup.getName());
            mPendingActivityResult = true;
            startAppPermissionFragment(groupState);
            mActivityResultCallback = data -> {
                mPendingActivityResult = false;
                if (data == null || data.getStringExtra(
                        EXTRA_RESULT_PERMISSION_INTERACTED) == null) {
                    // User didn't interact, count against rate limit
                    if (groupState.mGroup.isUserSet()) {
                        groupState.mGroup.setUserFixed(true);
                    } else {
                        groupState.mGroup.setUserSet(true);
                    }
                }
                if (!showNextPermissionGroupGrantRequest()) {
                    setResultAndFinish();
                }
            };
        }
    }

    private ClickableSpan getLinkToAppPermissions(GroupState groupState) {
        return new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                logGrantPermissionActivityButtons(groupState.mGroup.getName(), LINKED_TO_SETTINGS);
                startAppPermissionFragment(groupState);
                mActivityResultCallback = data -> {
                    if (data != null) {
                        String groupName = data.getStringExtra(EXTRA_RESULT_PERMISSION_INTERACTED);
                        if (groupName != null) {
                            mPermissionGroupsToSkip.add(groupName);
                            int result = data.getIntExtra(EXTRA_RESULT_PERMISSION_RESULT, -1);
                            logSettingsInteraction(groupName, result);
                        }
                    }
                };
            }
        };
    }

    private void logSettingsInteraction(String groupName, int result) {
        GroupState foregroundGroupState = getForegroundGroupState(groupName);
        GroupState backgroundGroupState = getBackgroundGroupState(groupName);
        switch (result) {
            case GRANTED_ALWAYS:
                if (foregroundGroupState != null) {
                    reportRequestResult(foregroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS);
                }
                if (backgroundGroupState != null) {
                    reportRequestResult(backgroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS);
                }
                break;
            case GRANTED_FOREGROUND_ONLY:
                if (foregroundGroupState != null) {
                    reportRequestResult(foregroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_IN_SETTINGS);
                }
                if (backgroundGroupState != null) {
                    reportRequestResult(backgroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS);
                }
                break;
            case DENIED:
                if (foregroundGroupState != null) {
                    reportRequestResult(foregroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS);
                }
                if (backgroundGroupState != null) {
                    reportRequestResult(backgroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_IN_SETTINGS);
                }
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                if (foregroundGroupState != null) {
                    reportRequestResult(foregroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE_IN_SETTINGS);
                }
                if (backgroundGroupState != null) {
                    reportRequestResult(backgroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE_IN_SETTINGS);
                }
                break;
        }
    }

    private void startAppPermissionFragment(GroupState groupState) {
        Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, mAppPermissions.getPackageInfo().packageName)
                .putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupState.mGroup.getName())
                .putExtra(Intent.EXTRA_USER, groupState.mGroup.getUser())
                .putExtra(EXTRA_CALLER_NAME, GrantPermissionsActivity.class.getName())
                .putExtra(Constants.EXTRA_SESSION_ID, mRequestId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivityForResult(intent, APP_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == APP_PERMISSION_REQUEST_CODE && mActivityResultCallback != null) {
            mActivityResultCallback.accept(data);
            mActivityResultCallback = null;
        }
    }

    @Override
    public void onPermissionGrantResult(String name,
            @GrantPermissionsViewHandler.Result int result) {
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

        logGrantPermissionActivityButtons(name, result);
        switch (result) {
            case CANCELED:
                if (foregroundGroupState != null) {
                    reportRequestResult(foregroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_IGNORED);
                }
                if (backgroundGroupState != null) {
                    reportRequestResult(backgroundGroupState.affectedPermissions,
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_IGNORED);
                }
                setResultAndFinish();
                return;
            case GRANTED_ALWAYS :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, true, false, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, true, false, false);
                }
                break;
            case GRANTED_FOREGROUND_ONLY :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, true, false, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, false, false);
                }
                break;
            case GRANTED_ONE_TIME:
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, true, true, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, true, false);
                }
                break;
            case DENIED :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, false, false, false);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, false, false);
                }
                break;
            case DENIED_DO_NOT_ASK_AGAIN :
                if (foregroundGroupState != null) {
                    onPermissionGrantResultSingleState(foregroundGroupState, false, false, true);
                }
                if (backgroundGroupState != null) {
                    onPermissionGrantResultSingleState(backgroundGroupState, false, false, true);
                }
                break;
        }

        if (!showNextPermissionGroupGrantRequest()) {
            setResultAndFinish();
        }
    }

    /**
     * Grants or revoked the affected permissions for a single {@link GroupState}.
     *
     * @param groupState The group state with the permissions to grant/revoke
     * @param granted {@code true} if the permissions should be granted, {@code false} if they
     *        should be revoked
     * @param isOneTime if the permission is temporary and should be revoked automatically
     * @param doNotAskAgain if the permissions should be revoked should be app be allowed to ask
     *        again for the same permissions?
     */
    private void onPermissionGrantResultSingleState(GroupState groupState, boolean granted,
            boolean isOneTime, boolean doNotAskAgain) {
        if (groupState != null && groupState.mGroup != null
                && groupState.mState == GroupState.STATE_UNKNOWN) {
            if (granted) {
                int permissionGrantRequestResult =
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED;

                if (isOneTime) {
                    groupState.mGroup.setOneTime(true);
                    permissionGrantRequestResult =
                            PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_GRANTED_ONE_TIME;
                } else {
                    groupState.mGroup.setOneTime(false);
                }

                groupState.mGroup.grantRuntimePermissions(true, doNotAskAgain,
                        groupState.affectedPermissions);
                groupState.mState = GroupState.STATE_ALLOWED;

                reportRequestResult(groupState.affectedPermissions, permissionGrantRequestResult);
            } else {
                groupState.mGroup.revokeRuntimePermissions(doNotAskAgain,
                        groupState.affectedPermissions);
                groupState.mGroup.setOneTime(false);
                groupState.mState = GroupState.STATE_DENIED;

                reportRequestResult(groupState.affectedPermissions, doNotAskAgain
                        ?
                        PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED_WITH_PREJUDICE
                        : PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__USER_DENIED);
            }
        }
    }

    @Override
    public void onBackPressed() {
        mViewHandler.onBackPressed();
    }

    @Override
    public void finish() {
        setResultIfNeeded(RESULT_CANCELED);
        if (mAutoGrantPermissionsNotifier != null) {
            mAutoGrantPermissionsNotifier.notifyOfAutoGrantPermissions(true);
        }
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
        if (mPendingActivityResult) {
            return;
        }
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
                if (mButtonVisibilities[ALLOW_BUTTON]) {
                    clickedButton = 1 << ALLOW_BUTTON;
                } else {
                    clickedButton = 1 << ALLOW_ALWAYS_BUTTON;
                }
                break;
            case GRANTED_FOREGROUND_ONLY:
                clickedButton = 1 << ALLOW_FOREGROUND_BUTTON;
                break;
            case DENIED:
                if (mButtonVisibilities[NO_UPGRADE_BUTTON]) {
                    clickedButton = 1 << NO_UPGRADE_BUTTON;
                } else if (mButtonVisibilities[NO_UPGRADE_OT_BUTTON]) {
                    clickedButton = 1 << NO_UPGRADE_OT_BUTTON;
                } else if (mButtonVisibilities[DENY_BUTTON]) {
                    clickedButton = 1 << DENY_BUTTON;
                }
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                if (mButtonVisibilities[NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON]) {
                    clickedButton = 1 << NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON;
                } else if (mButtonVisibilities[NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON]) {
                    clickedButton = 1 << NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON;
                } else if (mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON]) {
                    clickedButton = 1 << DENY_AND_DONT_ASK_AGAIN_BUTTON;
                }
                break;
            case GRANTED_ONE_TIME:
                clickedButton = 1 << ALLOW_ONE_TIME_BUTTON;
                break;
            case LINKED_TO_SETTINGS:
                clickedButton = 1 << LINK_TO_SETTINGS;
            case CANCELED:
                // fall through
            default:
                break;
        }

        PermissionControllerStatsLog.write(GRANT_PERMISSIONS_ACTIVITY_BUTTON_ACTIONS,
                permissionGroupName, mCallingUid, mCallingPackage, presentedButtons,
                clickedButton, mRequestId);
        Log.v(LOG_TAG, "Logged buttons presented and clicked permissionGroupName="
                + permissionGroupName + " uid=" + mCallingUid + " package=" + mCallingPackage
                + " presentedButtons=" + presentedButtons + " clickedButton=" + clickedButton
                + " sessionId=" + mRequestId);
    }

    private int getButtonState() {
        if (mButtonVisibilities == null) {
            return 0;
        }
        int buttonState = 0;
        for (int i = NEXT_BUTTON - 1; i >= 0; i--) {
            buttonState *= 2;
            if (mButtonVisibilities[i]) {
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

    /**
     * Creates the AutoGrantPermissionsNotifier lazily in case there's no policy set
     * device-wide (common case).
     *
     * @return An initalized {@code AutoGrantPermissionsNotifier} instance.
     */
    private @NonNull AutoGrantPermissionsNotifier getAutoGrantNotifier() {
        if (mAutoGrantPermissionsNotifier == null) {
            mAutoGrantPermissionsNotifier = new AutoGrantPermissionsNotifier(
                    this, mCallingPackageInfo);
        }

        return mAutoGrantPermissionsNotifier;
    }
}
