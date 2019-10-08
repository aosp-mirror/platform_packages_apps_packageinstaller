/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.permission.data

import android.app.Application
import android.os.UserHandle
import android.text.TextUtils
import androidx.lifecycle.LiveData
import com.android.packageinstaller.permission.model.livedatatypes.LightPackageInfo
import com.android.packageinstaller.permission.model.livedatatypes.PermGroup

abstract class LabelLiveData<T>(
    private val name: String,
    protected val liveData: LiveData<T>
) : SmartUpdateMediatorLiveData<CharSequence>() {

    init {
        addSource(liveData) {
            if (it == null) {
                value = null
            } else {
                update()
            }
        }
    }

    /**
     * Update the label for this liveData
     */
    protected abstract fun update()
}

/**
 * A LiveData for a CharSequence and Drawable pair representing a package's label and label.
 * TODO ntmyren: Are these livedatas required?
 *
 * @param app: The current application
 * @param name: The name of the package
 * @param user: The user of the package
 */
class PackageLabelLiveData(
    private val app: Application,
    name: String,
    private val user: UserHandle
) : LabelLiveData<LightPackageInfo>(name,
    PackageInfoRepository.getPackageInfoLiveData(app, name, user)) {

    override fun update() {
        val packageInfo = liveData.value
        if (packageInfo == null) {
            value = null
            return
        }

        value = packageInfo.getApplicationInfo(app)?.loadSafeLabel(app.packageManager, 0f,
        TextUtils.SAFE_STRING_FLAG_FIRST_LINE or TextUtils.SAFE_STRING_FLAG_TRIM)
    }
}

/**
 * A LiveData which stores the label for a permission group. Loads its label synchronously
 * upon initialization, and asynchronously afterwards.
 *
 * @param app: The current application
 * @param name: The name of the permission group
 */
class PermGroupLabelLiveData(
    private val app: Application,
    name: String
) : LabelLiveData<PermGroup>(
    name, PermGroupRepository.getPermGroupLiveData(app, name)) {

    override fun update() {
        val groupInfo = liveData.value?.groupInfo
        if (groupInfo == null) {
            value = null
            return
        }

        value = groupInfo.toPackageItemInfo(
            app)?.loadSafeLabel(app.packageManager, 0f,
            TextUtils.SAFE_STRING_FLAG_FIRST_LINE or TextUtils.SAFE_STRING_FLAG_TRIM)
    }
}

/**
 * A repository for package and permission group labels.
 * <p> Key value is a string package name/permission group name and
 * UserHandle pair (the UserHandle is null for permission groups), value is the corresponding label
 * LiveData.
 */
object LabelRepository : DataRepository<Pair<String, UserHandle?>, LabelLiveData<*>>() {

    /**
     * Gets the label for the given package and user, creating it if need be
     *
     * @param packageName The name of the package whose label is desired
     * @param user: The user whose version of the package should be queried
     *
     * @return the cached or newly generated label
     */
    fun getPackageLabelLiveData(
        app: Application,
        packageName: String,
        user: UserHandle
    ): LabelLiveData<*> {
        return getDataObject(app, packageName to user)
    }

    /**
     * Gets the icon for the given permission group, creating it if need be
     *
     * @param groupName The name of the package whose label is desired
     *
     * @return the cached or newly generated icon
     */
    fun getPermGroupLabelLiveData(
        app: Application,
        groupName: String
    ): LabelLiveData<*> {
        return getDataObject(app, groupName to null)
    }

    override fun newValue(app: Application, key: Pair<String, UserHandle?>): LabelLiveData<*> {
        key.second?.let {
            return PackageLabelLiveData(app, key.first, it)
        }
        return PermGroupLabelLiveData(app, key.first)
    }
}