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

package com.android.packageinstaller.role.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Specifies a required component for an application to qualify for a {@link Role}.
 */
public abstract class RequiredComponent {

    private static final String LOG_TAG = RequiredComponent.class.getSimpleName();

    /**
     * The {@code Intent} or {@code IntentFilter} data to match the components.
     */
    @NonNull
    private final IntentFilterData mIntentFilterData;

    /**
     * Optional permission required on a component for match to succeed.
     *
     * @see android.content.pm.ActivityInfo#permission
     * @see android.content.pm.ServiceInfo#permission
     */
    @Nullable
    private final String mPermission;

    /**
     * The meta data required on a component for match to succeed.
     *
     * @see android.content.pm.PackageItemInfo#metaData
     */
    @NonNull
    private final List<RequiredMetaData> mMetaData;

    public RequiredComponent(@NonNull IntentFilterData intentFilterData,
            @Nullable String permission,
            @NonNull List<RequiredMetaData> metaData) {
        mIntentFilterData = intentFilterData;
        mPermission = permission;
        mMetaData = metaData;
    }

    @NonNull
    public IntentFilterData getIntentFilterData() {
        return mIntentFilterData;
    }

    @Nullable
    public String getPermission() {
        return mPermission;
    }

    @NonNull
    public List<RequiredMetaData> getMetaData() {
        return mMetaData;
    }

    /**
     * Get the component that matches this required component within a package, if any.
     *
     * @param packageName the package name for this query
     * @param context the {@code Context} to retrieve system services
     *
     * @return the matching component, or {@code null} if none.
     */
    @Nullable
    public ComponentName getQualifyingComponentForPackage(@NonNull String packageName,
            @NonNull Context context) {
        List<ComponentName> componentNames = getQualifyingComponentsInternal(packageName,
                Process.myUserHandle(), context);
        return !componentNames.isEmpty() ? componentNames.get(0) : null;
    }

    /**
     * Get the list of components that match this required component, <b>at most one component per
     * package</b> and ordered from best to worst.
     *
     * @param user the user to get the qualifying components.
     * @param context the {@code Context} to retrieve system services
     *
     * @return the list of matching components
     *
     * @see Role#getQualifyingPackagesAsUser(UserHandle, Context)
     */
    @NonNull
    public List<ComponentName> getQualifyingComponentsAsUser(@NonNull UserHandle user,
            @NonNull Context context) {
        return getQualifyingComponentsInternal(null, user, context);
    }

    @NonNull
    private List<ComponentName> getQualifyingComponentsInternal(@Nullable String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        Intent intent = mIntentFilterData.createIntent();
        if (packageName != null) {
            intent.setPackage(packageName);
        }
        int flags = PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        boolean hasMetaData = !mMetaData.isEmpty();
        if (hasMetaData) {
            flags |= PackageManager.GET_META_DATA;
        }
        List<ResolveInfo> resolveInfos = queryIntentComponentsAsUser(intent, flags, user, context);

        ArraySet<String> componentPackageNames = new ArraySet<>();
        List<ComponentName> componentNames = new ArrayList<>();
        int resolveInfosSize = resolveInfos.size();
        for (int resolveInfosIndex = 0; resolveInfosIndex < resolveInfosSize; resolveInfosIndex++) {
            ResolveInfo resolveInfo = resolveInfos.get(resolveInfosIndex);

            if (mPermission != null) {
                String componentPermission = getComponentPermission(resolveInfo);
                if (!Objects.equals(componentPermission, mPermission)) {
                    continue;
                }
            }

            if (hasMetaData) {
                Bundle componentMetaData = getComponentMetaData(resolveInfo);
                if (componentMetaData == null) {
                    Log.w(LOG_TAG, "Component meta data is null");
                    continue;
                }
                boolean isMetaDataQualified = true;
                int metaDataSize = mMetaData.size();
                for (int metaDataIndex = 0; metaDataIndex < metaDataSize; metaDataIndex++) {
                    RequiredMetaData metaData = mMetaData.get(metaDataIndex);

                    if (!metaData.isQualified(componentMetaData)) {
                        isMetaDataQualified = false;
                        break;
                    }
                }
                if (!isMetaDataQualified) {
                    continue;
                }
            }

            ComponentName componentName = getComponentComponentName(resolveInfo);
            String componentPackageName = componentName.getPackageName();
            if (componentPackageNames.contains(componentPackageName)) {
                continue;
            }

            componentPackageNames.add(componentPackageName);
            componentNames.add(componentName);
        }
        return componentNames;
    }

    /**
     * Query the {@code PackageManager} for components matching an {@code Intent}, ordered from best
     * to worst.
     *
     * @param intent the {@code Intent} to match against
     * @param flags the flags for this query
     * @param user the user for this query
     * @param context the {@code Context} to retrieve system services
     *
     * @return the list of matching components
     */
    @NonNull
    protected abstract List<ResolveInfo> queryIntentComponentsAsUser(@NonNull Intent intent,
            int flags, @NonNull UserHandle user, @NonNull Context context);

    /**
     * Get the {@code ComponentName} of a component.
     *
     * @param resolveInfo the {@code ResolveInfo} of the component
     *
     * @return the {@code ComponentName} of the component
     */
    @NonNull
    protected abstract ComponentName getComponentComponentName(@NonNull ResolveInfo resolveInfo);

    /**
     * Get the permission required to access a component.
     *
     * @param resolveInfo the {@code ResolveInfo} of the component
     *
     * @return the permission required to access a component
     */
    @Nullable
    protected abstract String getComponentPermission(@NonNull ResolveInfo resolveInfo);

    /**
     * Get the meta data associated with a component.
     *
     * @param resolveInfo the {@code ResolveInfo} of the component
     *
     * @return the meta data associated with a component
     */
    @Nullable
    protected abstract Bundle getComponentMetaData(@NonNull ResolveInfo resolveInfo);

    @Override
    public String toString() {
        return "RequiredComponent{"
                + "mIntentFilterData=" + mIntentFilterData
                + ", mPermission='" + mPermission + '\''
                + ", mMetaData=" + mMetaData
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        RequiredComponent that = (RequiredComponent) object;
        return Objects.equals(mIntentFilterData, that.mIntentFilterData)
                && Objects.equals(mPermission, that.mPermission)
                && Objects.equals(mMetaData, that.mMetaData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIntentFilterData, mPermission, mMetaData);
    }
}
