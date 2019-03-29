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

package com.android.packageinstaller.permission.model;

import android.app.AppOpsManager;
import android.app.AppOpsManager.HistoricalOp;
import android.app.AppOpsManager.HistoricalPackageOps;
import android.app.AppOpsManager.OpEntry;
import android.app.AppOpsManager.PackageOps;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.model.PermissionApps.PermissionApp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Stats for permission usage of an app. This data is for a given time period,
 * i.e. does not contain the full history.
 */
public final class AppPermissionUsage {
    private final @NonNull List<GroupUsage> mGroupUsages = new ArrayList<>();
    private final @NonNull PermissionApp mPermissionApp;

    private AppPermissionUsage(@NonNull PermissionApp permissionApp,
            @NonNull List<AppPermissionGroup> groups, @Nullable PackageOps lastUsage,
            @Nullable HistoricalPackageOps historicalUsage) {
        mPermissionApp = permissionApp;
        final int groupCount = groups.size();
        for (int i = 0; i < groupCount; i++) {
            final AppPermissionGroup group = groups.get(i);
            mGroupUsages.add(new GroupUsage(group, lastUsage, historicalUsage));
        }
    }

    public @NonNull PermissionApp getApp() {
        return mPermissionApp;
    }

    public @NonNull String getPackageName() {
        return mPermissionApp.getPackageName();
    }

    public int getUid() {
        return mPermissionApp.getUid();
    }

    public long getLastAccessTime() {
        long lastAccessTime = 0;
        final int permissionCount = mGroupUsages.size();
        for (int i = 0; i < permissionCount; i++) {
            final GroupUsage groupUsage = mGroupUsages.get(i);
            lastAccessTime = Math.max(lastAccessTime, groupUsage.getLastAccessTime());
        }
        return lastAccessTime;
    }

    public long getAccessCount() {
        long accessCount = 0;
        final int permissionCount = mGroupUsages.size();
        for (int i = 0; i < permissionCount; i++) {
            final GroupUsage permission = mGroupUsages.get(i);
            accessCount += permission.getAccessCount();
        }
        return accessCount;
    }

    public @NonNull List<GroupUsage> getGroupUsages() {
        return mGroupUsages;
    }

    /**
     * Stats for permission usage of a permission group. This data is for a
     * given time period, i.e. does not contain the full history.
     */
    public static class GroupUsage {
        private final @NonNull AppPermissionGroup mGroup;
        private final @Nullable PackageOps mLastUsage;
        private final @Nullable HistoricalPackageOps mHistoricalUsage;

        GroupUsage(@NonNull AppPermissionGroup group, @Nullable PackageOps lastUsage,
                @Nullable HistoricalPackageOps historicalUsage) {
            mGroup = group;
            mLastUsage = lastUsage;
            mHistoricalUsage = historicalUsage;
        }

        public long getLastAccessTime() {
            if (mLastUsage == null) {
                return 0;
            }
            return lastAccessAggregate(
                    (op) -> op.getLastAccessTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED));
        }

        public long getLastAccessForegroundTime() {
            if (mLastUsage == null) {
                return 0;
            }
            return lastAccessAggregate(
                    (op) -> op.getLastAccessForegroundTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED));
        }

        public long getLastAccessBackgroundTime() {
            if (mLastUsage == null) {
                return 0;
            }
            return lastAccessAggregate(
                    (op) -> op.getLastAccessBackgroundTime(AppOpsManager.OP_FLAGS_ALL_TRUSTED));
        }

        public long getForegroundAccessCount() {
            if (mHistoricalUsage == null) {
                return 0;
            }
            return extractAggregate((HistoricalOp op)
                    -> op.getForegroundAccessCount(AppOpsManager.OP_FLAGS_ALL_TRUSTED));
        }

        public long getBackgroundAccessCount() {
            if (mHistoricalUsage == null) {
                return 0;
            }
            return extractAggregate((HistoricalOp op)
                    -> op.getBackgroundAccessCount(AppOpsManager.OP_FLAGS_ALL_TRUSTED));
        }

        public long getAccessCount() {
            if (mHistoricalUsage == null) {
                return 0;
            }
            return extractAggregate((HistoricalOp op) ->
                op.getForegroundAccessCount(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                        + op.getBackgroundAccessCount(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
            );
        }

        public long getAccessDuration() {
            if (mHistoricalUsage == null) {
                return 0;
            }
            return extractAggregate((HistoricalOp op) ->
                    op.getForegroundAccessDuration(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
                            + op.getBackgroundAccessDuration(AppOpsManager.OP_FLAGS_ALL_TRUSTED)
            );
        }

        public boolean isRunning() {
            if (mLastUsage == null) {
                return false;
            }
            final ArrayList<Permission> permissions = mGroup.getPermissions();
            final int permissionCount = permissions.size();
            for (int i = 0; i < permissionCount; i++) {
                final Permission permission = permissions.get(i);
                final String opName = permission.getAppOp();
                final List<OpEntry> ops = mLastUsage.getOps();
                final int opCount = ops.size();
                for (int j = 0; j < opCount; j++) {
                    final OpEntry op = ops.get(j);
                    if (op.getOpStr().equals(opName) && op.isRunning()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private long extractAggregate(@NonNull Function<HistoricalOp, Long> extractor) {
            long aggregate = 0;
            final ArrayList<Permission> permissions = mGroup.getPermissions();
            final int permissionCount = permissions.size();
            for (int i = 0; i < permissionCount; i++) {
                final Permission permission = permissions.get(i);
                final String opName = permission.getAppOp();
                final HistoricalOp historicalOp = mHistoricalUsage.getOp(opName);
                if (historicalOp != null) {
                    aggregate += extractor.apply(historicalOp);
                }
            }
            return aggregate;
        }

        private long lastAccessAggregate(@NonNull Function<OpEntry, Long> extractor) {
            long aggregate = 0;
            final ArrayList<Permission> permissions = mGroup.getPermissions();
            final int permissionCount = permissions.size();
            for (int permissionNum = 0; permissionNum < permissionCount; permissionNum++) {
                final Permission permission = permissions.get(permissionNum);
                final String opName = permission.getAppOp();
                final List<OpEntry> ops = mLastUsage.getOps();
                final int opCount = ops.size();
                for (int opNum = 0; opNum < opCount; opNum++) {
                    final OpEntry op = ops.get(opNum);
                    if (op.getOpStr().equals(opName)) {
                        aggregate = Math.max(aggregate, extractor.apply(op));
                    }
                }
            }
            return aggregate;
        }

        public @NonNull AppPermissionGroup getGroup() {
            return mGroup;
        }
    }

    public static class Builder {
        private final @NonNull List<AppPermissionGroup> mGroups = new ArrayList<>();
        private final @NonNull PermissionApp mPermissionApp;
        private @Nullable PackageOps mLastUsage;
        private @Nullable HistoricalPackageOps mHistoricalUsage;

        public Builder(@NonNull PermissionApp permissionApp) {
            mPermissionApp = permissionApp;
        }

        public @NonNull Builder addGroup(@NonNull AppPermissionGroup group) {
            mGroups.add(group);
            return this;
        }

        public @NonNull Builder setLastUsage(@Nullable PackageOps lastUsage) {
            mLastUsage = lastUsage;
            return this;
        }

        public @NonNull Builder setHistoricalUsage(@Nullable HistoricalPackageOps historicalUsage) {
            mHistoricalUsage = historicalUsage;
            return this;
        }

        public @NonNull AppPermissionUsage build() {
            if (mGroups.isEmpty()) {
                throw new IllegalStateException("mGroups cannot be empty.");
            }
            return new AppPermissionUsage(mPermissionApp, mGroups, mLastUsage, mHistoricalUsage);
        }
    }
}
