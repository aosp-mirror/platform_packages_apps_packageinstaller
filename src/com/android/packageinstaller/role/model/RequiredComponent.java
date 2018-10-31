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
import android.content.pm.ResolveInfo;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Specifies a required component for an application to qualify for a {@link Role}.
 */
public abstract class RequiredComponent {

    /**
     * Optional permission required on a component for match to succeed.
     */
    @Nullable
    private final String mPermission;

    /**
     * The {@code Intent} or {@code IntentFilter} data to match the components.
     */
    @NonNull
    private final IntentFilterData mIntentFilterData;

    public RequiredComponent(@Nullable String permission,
            @NonNull IntentFilterData intentFilterData) {
        mPermission = permission;
        mIntentFilterData = intentFilterData;
    }

    @Nullable
    public String getPermission() {
        return mPermission;
    }

    @NonNull
    public IntentFilterData getIntentFilterData() {
        return mIntentFilterData;
    }

    /**
     * Get the component that matches this required component within a package, if any.
     *
     * @param packageName the package name for this query
     * @param context the {@code Context} to retrieve the {@code PackageManager}
     *
     * @return the matching component, or {@code null} if none.
     */
    @Nullable
    public ComponentName getQualifyingComponentForPackage(@NonNull String packageName,
            @NonNull Context context) {
        List<ComponentName> componentNames = getQualifyingComponents(packageName,
                context);
        return !componentNames.isEmpty() ? componentNames.get(0) : null;
    }

    /**
     * Get the list of components that match this required component, <b>at most one component per
     * package</b> and ordered from best to worst.
     *
     * @param context the {@code Context} to retrieve the {@code PackageManager}
     *
     * @return the list of matching components
     *
     * @see Role#getQualifyingPackages(Context)
     */
    @NonNull
    public List<ComponentName> getQualifyingComponents(@NonNull Context context) {
        return getQualifyingComponents(null, context);
    }

    @NonNull
    private List<ComponentName> getQualifyingComponents(@Nullable String packageName,
            @NonNull Context context) {
        Intent intent = mIntentFilterData.createIntent();
        if (packageName != null) {
            intent.setPackage(packageName);
        }
        List<ResolveInfo> resolveInfos = queryIntentComponents(intent, context);

        ArraySet<String> componentPackageNames = new ArraySet<>();
        List<ComponentName> componentNames = new ArrayList<>();
        int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (mPermission == null || Objects.equals(getComponentPermission(resolveInfo),
                    mPermission)) {
                ComponentName componentName = getComponentComponentName(resolveInfo);
                String componentPackageName = componentName.getPackageName();
                if (componentPackageNames.contains(componentPackageName)) {
                    continue;
                }
                componentPackageNames.add(componentPackageName);
                componentNames.add(componentName);
            }
        }
        return componentNames;
    }

    /**
     * Query the {@code PackageManager} for components matching an {@code Intent}, ordered from best
     * to worst.
     *
     * @param intent the {@code Intent} to match against
     * @param context the {@code Context} to retrieve the {@code PackageManager}
     *
     * @return the list of matching components
     */
    @NonNull
    protected abstract List<ResolveInfo> queryIntentComponents(@NonNull Intent intent,
            @NonNull Context context);

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

    @Override
    public String toString() {
        return "RequiredComponent{"
                + "mPermission='" + mPermission + '\''
                + ", mIntentFilterData=" + mIntentFilterData
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
        return Objects.equals(mPermission, that.mPermission)
                && Objects.equals(mIntentFilterData, that.mIntentFilterData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPermission, mIntentFilterData);
    }
}
