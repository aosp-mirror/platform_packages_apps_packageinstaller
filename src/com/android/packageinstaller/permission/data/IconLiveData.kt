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
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.lifecycle.LiveData
import com.android.packageinstaller.permission.model.livedatatypes.LightPackageInfo
import com.android.packageinstaller.permission.model.livedatatypes.PermGroup
import com.android.packageinstaller.permission.utils.Utils
import com.android.permissioncontroller.R

/**
 * An abstract LiveData which will contain a Drawable icon.
 *
 * @param app: The current application
 * @param name: The name of the item whose label and icon will be gotten
 */
abstract class IconLiveData<T>(
    private var app: Application,
    private val name: String,
    protected val liveData: LiveData<T>
) : SmartAsyncMediatorLiveData<Drawable>() {

    init {
        addSource(liveData) {
            if (it == null) {
                value = null
            } else {
                updateAsync()
            }
        }
    }

    /**
     * Needed because the default equals method of two Drawables is too strict.
     */
    override fun valueNotEqual(valOne: Drawable?, valTwo: Drawable?): Boolean {
        if (valOne != null && valTwo != null) {
            return valOne.constantState != valTwo.constantState
        }
        return super.valueNotEqual(valOne, valTwo)
    }
}

/**
 * A LiveData for a CharSequence and Drawable pair representing a package's label and icon.
 *
 * @param app: The current application
 * @param name: The name of the package
 * @param user: The user of the package
 */
class PackageIconLiveData(
    private val app: Application,
    name: String,
    private val user: UserHandle
) : IconLiveData<LightPackageInfo>(app, name,
    PackageInfoRepository.getPackageInfoLiveData(app, name, user)) {

    override fun loadData(isCancelled: () -> Boolean): Drawable? {
        val lightPackageInfo = liveData.value ?: return value

        if (isCancelled()) {
            return null
        }

        val appInfo = lightPackageInfo.getApplicationInfo(app)
        if (appInfo != null) {
            return Utils.getBadgedIcon(Utils.getUserContext(app, user), appInfo)
        }
        return null
    }
}

/**
 * A LiveData which stores the icon for a permission group. Loads its icon synchronously
 * upon initialization, and asynchronously afterwards.
 *
 * @param app: The current application
 * @param name: The name of the permission group
 */
class PermGroupIconLiveData(
    private val app: Application,
    private val name: String
) : IconLiveData<PermGroup>(app, name, PermGroupRepository.getPermGroupLiveData(app, name)) {

    /**
     * Permission group icons load synchronously, so we override update() in order to load them
     * in the foreground.
     * TODO ntmyren: determine if we can always have all icons be synchronous
     */
    override fun updateAsync() {
        liveData.value?.groupInfo?.let { groupInfo ->
            var newIcon: Drawable? = null
            if (groupInfo.icon != 0) {
                newIcon = Utils.loadDrawable(app.packageManager, groupInfo.packageName,
                    groupInfo.icon)
            }

            value = newIcon ?: app.applicationContext.getDrawable(R.drawable.ic_perm_device_info)
        }
    }

    /**
     * Since we override update, we provide a no-op implementation of loadData
     */
    override fun loadData(isCancelled: () -> Boolean): Drawable? {
        return null
    }
}

/**
 * A repository for package and permission group icons.
 * <p> Key value is a string package name/permission group name and
 * UserHandle pair (the UserHandle is null for permission groups), value is the corresponding icon
 * LiveData.
 */
object IconRepository : DataRepository<Pair<String, UserHandle?>, IconLiveData<*>>() {

    /**
     * Gets the icon for the given package and user, creating it if need be
     *
     * @param packageName The name of the package whose label is desired
     * @param user: The user whose version of the package should be queried
     *
     * @return the cached or newly generated icon
     */
    fun getPackageIconLiveData(
        app: Application,
        packageName: String,
        user: UserHandle
    ): IconLiveData<*> {
        return getDataObject(app, packageName to user)
    }

    /**
     * Gets the icon for the given permission group, creating it if need be
     *
     * @param groupName The name of the package whose label is desired
     *
     * @return the cached or newly generated icon
     */
    fun getPermGroupIconLiveData(
        app: Application,
        groupName: String
    ): IconLiveData<*> {
        return getDataObject(app, groupName to null)
    }

    override fun newValue(app: Application, key: Pair<String, UserHandle?>): IconLiveData<*> {
        key.second?.let {
            return PackageIconLiveData(app, key.first, it)
        }
        return PermGroupIconLiveData(app, key.first)
    }
}