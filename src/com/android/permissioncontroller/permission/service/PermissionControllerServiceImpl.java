/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.permissioncontroller.permission.service;

import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.permission.PermissionControllerManager.REASON_INSTALLER_POLICY_VIOLATION;
import static android.permission.PermissionControllerManager.REASON_MALWARE;
import static android.util.Xml.newSerializer;

import static com.android.permissioncontroller.PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_ONE_TIME_PERMISSION_REVOKED;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.permission.RuntimePermissionPresentationInfo;
import android.permission.RuntimePermissionUsageInfo;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.permissioncontroller.PermissionControllerProto.PermissionControllerDumpProto;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissions;
import com.android.permissioncontroller.permission.model.Permission;
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo;
import com.android.permissioncontroller.permission.model.livedatatypes.AppPermGroupUiInfo.PermGrantState;
import com.android.permissioncontroller.permission.ui.AutoGrantPermissionsNotifier;
import com.android.permissioncontroller.permission.utils.ArrayUtils;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.UserSensitiveFlagsUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import kotlin.Pair;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.GlobalScope;

/**
 * Calls from the system into the permission controller.
 *
 * All reading methods are called async, and all writing method are called on the AsyncTask single
 * thread executor so that multiple writes won't override each other concurrently.
 */
public final class PermissionControllerServiceImpl extends PermissionControllerLifecycleService {
    private static final String LOG_TAG = PermissionControllerServiceImpl.class.getSimpleName();
    public static final String ONE_TIME_PERMISSION_REVOKED_REASON = "one-time permission revoked";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;


    private final PermissionControllerServiceModel mServiceModel = new
            PermissionControllerServiceModel(this);

    @Override
    public boolean onUnbind(@Nullable Intent intent) {
        mServiceModel.removeObservers();
        return super.onUnbind(intent);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        PermissionControllerDumpProto dump;
        try {
            dump = BuildersKt.runBlocking(
                    GlobalScope.INSTANCE.getCoroutineContext(),
                    (coroutineScope, continuation) -> mServiceModel.onDump(continuation));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot produce dump", e);
            return;
        }

        if (ArrayUtils.contains(args, "--proto")) {
            try (OutputStream out = new FileOutputStream(fd)) {
                dump.writeTo(out);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Cannot write dump", e);
            }
        } else {
            writer.println(dump.toString());
            writer.flush();
        }
    }

    /**
     * Expand {@code perms} by split permissions for an app with the given targetSDK.
     *
     * @param perms The permissions that should be expanded
     * @param targetSDK The target SDK to expand for
     *
     * @return The expanded permissions
     */
    private @NonNull ArrayList<String> addSplitPermissions(@NonNull List<String> perms,
            int targetSDK) {
        List<PermissionManager.SplitPermissionInfo> splitPerms =
                getSystemService(PermissionManager.class).getSplitPermissions();

        // Add split permissions to the request
        ArrayList<String> expandedPerms = new ArrayList<>(perms);
        int numReqPerms = perms.size();
        for (int reqPermNum = 0; reqPermNum < numReqPerms; reqPermNum++) {
            String reqPerm = perms.get(reqPermNum);

            int numSplitPerms = splitPerms.size();
            for (int splitPermNum = 0; splitPermNum < numSplitPerms; splitPermNum++) {
                PermissionManager.SplitPermissionInfo splitPerm = splitPerms.get(splitPermNum);

                if (targetSDK < splitPerm.getTargetSdk()
                        && splitPerm.getSplitPermission().equals(reqPerm)) {
                    expandedPerms.addAll(splitPerm.getNewPermissions());
                }
            }
        }

        return expandedPerms;
    }

    /**
     * Get the package info for a package.
     *
     * @param pkg The package name
     *
     * @return the package info or {@code null} if the package could not be found
     */
    private @Nullable PackageInfo getPkgInfo(@NonNull String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(LOG_TAG, pkg + " not found", e);
            return null;
        }
    }

    /**
     * Given a set of permissions, find all permission groups of an app that can be revoked and that
     * contain any of the permissions.
     *
     * @param permissions The permissions to revoke
     * @param appPerms The {@link AppPermissions} for the app that is currently investigated
     *
     * @return The groups to revoke
     */
    private @NonNull ArrayList<AppPermissionGroup> getRevocableGroupsForPermissions(
            @NonNull ArrayList<String> permissions, @NonNull AppPermissions appPerms) {
        ArrayList<AppPermissionGroup> groupsToRevoke = new ArrayList<>();
        int numGroups = appPerms.getPermissionGroups().size();
        for (int groupNum = 0; groupNum < numGroups; groupNum++) {
            AppPermissionGroup group = appPerms.getPermissionGroups().get(groupNum);

            // Do not override fixed permissions
            if (group.isPolicyFixed() || group.isSystemFixed()) {
                continue;
            }

            int numPerms = permissions.size();
            for (int permNum = 0; permNum < numPerms; permNum++) {
                String reqPerm = permissions.get(permNum);

                if (group.hasPermission(reqPerm)) {
                    groupsToRevoke.add(group);

                    // If fg permissions get revoked also revoke bg permissions as bg
                    // permissions require fg permissions.
                    AppPermissionGroup bgPerms = group.getBackgroundPermissions();
                    if (bgPerms != null) {
                        groupsToRevoke.add(bgPerms);
                    }
                } else {
                    AppPermissionGroup bgPerms = group.getBackgroundPermissions();
                    if (bgPerms != null && bgPerms.hasPermission(reqPerm)) {
                        groupsToRevoke.add(bgPerms);
                    }
                }
            }
        }

        return groupsToRevoke;
    }

    /**
     * Revoke all permissions of some groups.
     *
     * @param groupsToRevoke The groups
     *
     * @return The permissions that were revoked
     */
    private @NonNull ArrayList<String> revokePermissionGroups(
            @NonNull ArrayList<AppPermissionGroup> groupsToRevoke) {
        ArrayList<String> revokedPerms = new ArrayList<>();

        int numGroupsToRevoke = groupsToRevoke.size();
        for (int groupsToRevokeNum = 0; groupsToRevokeNum < numGroupsToRevoke;
                groupsToRevokeNum++) {
            AppPermissionGroup group = groupsToRevoke.get(groupsToRevokeNum);
            ArrayList<Permission> perms = group.getPermissions();

            // Mark the permissions as reviewed as we don't want to use to accidentally grant
            // the permission during review
            group.unsetReviewRequired();

            int numPerms = perms.size();
            for (int permNum = 0; permNum < numPerms; permNum++) {
                Permission perm = perms.get(permNum);

                // Only count individual permissions that are actually revoked
                if (perm.isGrantedIncludingAppOp()) {
                    revokedPerms.add(perm.getName());
                }
            }

            group.revokeRuntimePermissions(false);
        }

        return revokedPerms;
    }

    @Override
    public void onRevokeRuntimePermissions(@NonNull Map<String, List<String>> request,
            boolean doDryRun, int reason, @NonNull String callerPackageName,
            @NonNull Consumer<Map<String, List<String>>> callback) {
        AsyncTask.execute(() -> callback.accept(onRevokeRuntimePermissions(request, doDryRun,
                reason, callerPackageName)));
    }

    private @NonNull Map<String, List<String>> onRevokeRuntimePermissions(
            @NonNull Map<String, List<String>> request, boolean doDryRun,
            int reason, @NonNull String callerPackageName) {
        // The reason parameter is not checked by platform code as this might need to be updated
        // async to platform releases.
        if (reason != REASON_MALWARE && reason != REASON_INSTALLER_POLICY_VIOLATION) {
            Log.e(LOG_TAG, "Invalid reason " + reason);
            return Collections.emptyMap();
        }

        PackageManager pm = getPackageManager();

        PackageInfo callerPkgInfo = getPkgInfo(callerPackageName);
        if (callerPkgInfo == null) {
            return Collections.emptyMap();
        }
        int callerTargetSdk = callerPkgInfo.applicationInfo.targetSdkVersion;

        Map<String, List<String>> actuallyRevokedPerms = new ArrayMap<>();
        ArrayList<AppPermissions> appsWithRevokedPerms = new ArrayList<>();

        for (Map.Entry<String, List<String>> appRequest : request.entrySet()) {
            PackageInfo requestedPkgInfo = getPkgInfo(appRequest.getKey());
            if (requestedPkgInfo == null) {
                continue;
            }

            // Permissions are per UID. Hence permissions will be removed from all apps sharing an
            // UID.
            String[] pkgNames = pm.getPackagesForUid(requestedPkgInfo.applicationInfo.uid);
            if (pkgNames == null) {
                continue;
            }

            int numPkgNames = pkgNames.length;
            for (int pkgNum = 0; pkgNum < numPkgNames; pkgNum++) {
                String pkgName = pkgNames[pkgNum];

                PackageInfo pkgInfo = getPkgInfo(pkgName);
                if (pkgInfo == null) {
                    continue;
                }

                // If the revocation is because of a market policy violation only the installer can
                // revoke the permissions.
                if (reason == REASON_INSTALLER_POLICY_VIOLATION
                        && !callerPackageName.equals(pm.getInstallerPackageName(pkgName))) {
                    Log.i(LOG_TAG, "Ignoring " + pkgName + " as it is not installed by "
                            + callerPackageName);
                    continue;
                }

                // In rare cases the caller does not know about the permissions that have been added
                // due to splits. Hence add them now.
                ArrayList<String> expandedPerms = addSplitPermissions(appRequest.getValue(),
                        callerTargetSdk);

                AppPermissions appPerms = new AppPermissions(this, pkgInfo, false, true, null);

                // First find the groups that should be revoked and then revoke all permissions of
                // these groups. This is needed as soon as a single permission in the group is
                // granted, all other permissions get auto-granted on request.
                ArrayList<AppPermissionGroup> groupsToRevoke = getRevocableGroupsForPermissions(
                        expandedPerms, appPerms);
                ArrayList<String> revokedPerms = revokePermissionGroups(groupsToRevoke);

                // In racy conditions the group might not have had granted permissions anymore
                if (!revokedPerms.isEmpty()) {
                    actuallyRevokedPerms.put(pkgName, revokedPerms);
                    appsWithRevokedPerms.add(appPerms);
                }
            }
        }

        // Persist changes after we computed everything to remove
        // This is necessary as we would otherwise only look at the first app of a shared UID.
        if (!doDryRun) {
            int numChangedApps = appsWithRevokedPerms.size();
            for (int i = 0; i < numChangedApps; i++) {
                appsWithRevokedPerms.get(i).persistChanges(true);
            }
        }

        return actuallyRevokedPerms;
    }

    @Override
    public void onGetRuntimePermissionsBackup(@NonNull UserHandle user,
            @NonNull OutputStream backup, @NonNull Runnable callback) {
        AsyncTask.execute(() -> {
            onGetRuntimePermissionsBackup(user, backup);
            callback.run();
        });
    }

    private void onGetRuntimePermissionsBackup(@NonNull UserHandle user,
            @NonNull OutputStream backup) {
        BackupHelper backupHelper = new BackupHelper(this, user);

        try {
            XmlSerializer serializer = newSerializer();
            serializer.setOutput(backup, UTF_8.name());

            backupHelper.writeState(serializer);
            serializer.flush();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to write permissions backup", e);
        }
    }

    @Override
    public void onStageAndApplyRuntimePermissionsBackup(@NonNull UserHandle user,
            @NonNull InputStream backup, @NonNull Runnable callback) {
        AsyncTask.execute(() -> {
            onRestoreRuntimePermissionsBackup(user, backup);
            callback.run();
        });
    }

    private void onRestoreRuntimePermissionsBackup(@NonNull UserHandle user,
            @NonNull InputStream backup) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(backup, StandardCharsets.UTF_8.name());

            new BackupHelper(this, user).restoreState(parser);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception restoring permissions: " + e.getMessage());
        }
    }

    @Override
    public void onApplyStagedRuntimePermissionBackup(@NonNull String packageName,
            @NonNull UserHandle user, @NonNull Consumer<Boolean> callback) {
        AsyncTask.execute(() -> callback.accept(
                onRestoreDelayedRuntimePermissionsBackup(packageName, user)));
    }

    private boolean onRestoreDelayedRuntimePermissionsBackup(@NonNull String packageName,
            @NonNull UserHandle user) {
        try {
            return new BackupHelper(this, user).restoreDelayedState(packageName);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception restoring delayed permissions: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onGetAppPermissions(@NonNull String packageName,
            @NonNull Consumer<List<RuntimePermissionPresentationInfo>> callback) {
        mServiceModel.onGetAppPermissions(packageName, (groupUiInfos) -> {
            List<RuntimePermissionPresentationInfo> permissions = new ArrayList<>();

            for (Pair<String, AppPermGroupUiInfo> groupNameAndUiInfo : groupUiInfos) {
                String groupName = groupNameAndUiInfo.getFirst();
                AppPermGroupUiInfo uiInfo = groupNameAndUiInfo.getSecond();
                boolean isPlatform = Utils.getPlatformPermissionGroups().contains(groupName);
                CharSequence label = KotlinUtils.INSTANCE.getPermGroupLabel(this, groupName);

                RuntimePermissionPresentationInfo permission =
                        new RuntimePermissionPresentationInfo(label,
                                uiInfo.getPermGrantState() != PermGrantState.PERMS_DENIED
                                        && uiInfo.getPermGrantState() != PermGrantState.PERMS_ASK,
                                isPlatform);
                permissions.add(permission);
            }
            callback.accept(permissions);
        });
    }

    @Override
    public void onRevokeRuntimePermission(@NonNull String packageName,
            @NonNull String permissionName, @NonNull Runnable callback) {
        AsyncTask.execute(() -> {
            onRevokeRuntimePermission(packageName, permissionName);
            callback.run();
        });
    }

    private void onRevokeRuntimePermission(@NonNull String packageName,
            @NonNull String permissionName) {
        try {
            final PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName,
                    GET_PERMISSIONS);
            final AppPermissions appPermissions = new AppPermissions(this, packageInfo, false,
                    null);

            final AppPermissionGroup appPermissionGroup = appPermissions.getGroupForPermission(
                    permissionName);

            if (appPermissionGroup != null) {
                appPermissionGroup.revokeRuntimePermissions(false);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Error getting package:" + packageName, e);
        }
    }

    @Override
    public void onCountPermissionApps(@NonNull List<String> permissionNames, int flags,
            @NonNull IntConsumer callback) {
        // There is no data processing needed, so we just directly pass the result onto the callback
        mServiceModel.onCountPermissionAppsLiveData(permissionNames, flags,
                callback);
    }

    /**
     * Deprecated api call, only returns null.
     */
    @Override
    @Deprecated
    public void onGetPermissionUsages(boolean countSystem, long numMillis,
            @NonNull Consumer<List<RuntimePermissionUsageInfo>> callback) {
        callback.accept(null);
    }

    @Override
    public void onSetRuntimePermissionGrantStateByDeviceAdmin(@NonNull String callerPackageName,
            @NonNull String packageName, @NonNull String unexpandedPermission, int grantState,
            @NonNull Consumer<Boolean> callback) {
        AsyncTask.execute(() -> callback.accept(onSetRuntimePermissionGrantStateByDeviceAdmin(
                callerPackageName, packageName, unexpandedPermission, grantState)));
    }

    private boolean onSetRuntimePermissionGrantStateByDeviceAdmin(@NonNull String callerPackageName,
            @NonNull String packageName, @NonNull String unexpandedPermission, int grantState) {
        PackageInfo callerPkgInfo = getPkgInfo(callerPackageName);
        if (callerPkgInfo == null) {
            Log.w(LOG_TAG, "Cannot fix " + unexpandedPermission + " as admin "
                    + callerPackageName + " cannot be found");
            return false;
        }

        PackageInfo pkgInfo = getPkgInfo(packageName);
        if (pkgInfo == null) {
            Log.w(LOG_TAG, "Cannot fix " + unexpandedPermission + " as " + packageName
                    + " cannot be found");
            return false;
        }

        ArrayList<String> expandedPermissions = addSplitPermissions(
                Collections.singletonList(unexpandedPermission),
                callerPkgInfo.applicationInfo.targetSdkVersion);

        AppPermissions app = new AppPermissions(this, pkgInfo, false, true, null);
        AutoGrantPermissionsNotifier autoGrantPermissionsNotifier =
                new AutoGrantPermissionsNotifier(this, pkgInfo);

        int numPerms = expandedPermissions.size();
        for (int i = 0; i < numPerms; i++) {
            String permName = expandedPermissions.get(i);
            AppPermissionGroup group = app.getGroupForPermission(permName);
            if (group == null || group.isSystemFixed()) {
                continue;
            }

            Permission perm = group.getPermission(permName);
            if (perm == null) {
                continue;
            }

            switch (grantState) {
                case PERMISSION_GRANT_STATE_GRANTED:
                    perm.setPolicyFixed(true);
                    group.grantRuntimePermissions(false, false, new String[]{permName});
                    autoGrantPermissionsNotifier.onPermissionAutoGranted(permName);
                    break;
                case PERMISSION_GRANT_STATE_DENIED:
                    perm.setPolicyFixed(true);
                    group.revokeRuntimePermissions(false, new String[]{permName});
                    break;
                case PERMISSION_GRANT_STATE_DEFAULT:
                    perm.setPolicyFixed(false);
                    break;
                default:
                    return false;
            }
        }

        app.persistChanges(grantState == PERMISSION_GRANT_STATE_DENIED
                || !callerPackageName.equals(packageName));
        autoGrantPermissionsNotifier.notifyOfAutoGrantPermissions(false);

        return true;
    }

    @Override
    public void onGrantOrUpgradeDefaultRuntimePermissions(@NonNull Runnable callback) {
        performDefaultPermissionGrants();
        RuntimePermissionsUpgradeController.INSTANCE.upgradeIfNeeded(this, () -> {
            callback.run();
        });
    }

    private void performDefaultPermissionGrants() {
        // TODO: Default permission grants should go here
    }

    @Override
    public void onUpdateUserSensitivePermissionFlags(int uid, Executor executor,
            Runnable callback) {
        onUpdateUserSensistivePermissionFlagsWithRetry(uid, executor, callback, 0);
    }

    private void onUpdateUserSensistivePermissionFlagsWithRetry(int uid, Executor executor,
            Runnable callback, int numAttempts) {
        String idString = uid == Process.INVALID_UID
                ? "user " + Process.myUserHandle().getIdentifier() : "uid " + uid;
        try {
            Log.i(LOG_TAG, "Updating user sensitive for " + idString);
            if (uid == Process.INVALID_UID) {
                UserSensitiveFlagsUtils.updateUserSensitiveForUser(Process.myUserHandle(),
                        () -> executor.execute(callback));
            } else {
                UserSensitiveFlagsUtils.updateUserSensitiveForUid(uid,
                        () -> executor.execute(callback));
            }
        } catch (Exception e) {
            // We specifically want to catch DeadSystemExceptions, but cannot explicitly request
            // them, as it results in a compiler error
            Log.w(LOG_TAG, "Failed to complete user sensitive update for " + idString
                    + ", attempt number " + (numAttempts + 1) + " of " + MAX_RETRY_ATTEMPTS, e);
            if (numAttempts == MAX_RETRY_ATTEMPTS) {
                throw e;
            } else {
                int attempts = numAttempts + 1;
                Handler h = new Handler(Looper.getMainLooper());
                h.postDelayed(() -> onUpdateUserSensistivePermissionFlagsWithRetry(uid,
                        executor, callback, attempts), RETRY_DELAY_MS);
            }
        }

    }

    @Override
    public void onOneTimePermissionSessionTimeout(@NonNull String packageName) {
        PackageManager pm = getPackageManager();
        PackageInfo packageInfo;
        int uid;
        try {
            packageInfo = pm.getPackageInfo(packageName, GET_PERMISSIONS);
            uid = pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }

        String[] permissions = packageInfo.requestedPermissions;
        if (permissions == null) {
            return;
        }

        Set<AppPermissionGroup> groups = new ArraySet<>();
        for (String permission : permissions) {
            AppPermissionGroup group = AppPermissionGroup.create(this, packageInfo, permission,
                    true);
            if (group != null && group.isOneTime()) {
                groups.add(group);
            }
        }
        long requestId = Utils.getValidSessionId();
        for (AppPermissionGroup group : groups) {
            if (group.areRuntimePermissionsGranted()) {
                logOneTimeSessionRevoke(packageName, uid, group, requestId);
                group.revokeRuntimePermissions(false);
            }
            group.setUserSet(false);
            group.persistChanges(false, ONE_TIME_PERMISSION_REVOKED_REASON);
        }
    }

    private void logOneTimeSessionRevoke(@NonNull String packageName, int uid,
            AppPermissionGroup group, long requestId) {
        // used to keep lines below 100 chars
        int r = PERMISSION_GRANT_REQUEST_RESULT_REPORTED__RESULT__AUTO_ONE_TIME_PERMISSION_REVOKED;

        for (Permission permission : group.getPermissions()) {
            if (permission.isGranted()) {
                String permName = permission.getName();
                Log.v(LOG_TAG,
                        "Permission grant result requestId=" + requestId + " callingUid="
                                + uid + " callingPackage=" + packageName + " permission="
                                + permName + " isImplicit=false" + " result=" + r);

                PermissionControllerStatsLog.write(
                        PermissionControllerStatsLog.PERMISSION_GRANT_REQUEST_RESULT_REPORTED,
                        requestId, uid, packageName, permName, false, r);
            }
        }
    }
}
