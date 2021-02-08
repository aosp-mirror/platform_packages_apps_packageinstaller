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

package com.android.permissioncontroller.permission.data

import android.app.Application
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources.ID_NULL
import android.os.UserHandle
import android.util.Log
import com.android.permissioncontroller.PermissionControllerApplication
import kotlinx.coroutines.Job
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import java.io.FileNotFoundException

private const val MANIFEST_FILE_NAME = "AndroidManifest.xml"
private const val MANIFEST_TAG = "manifest"
private const val PKG_ATTR = "package"
private const val ATTRIBUTION_TAG = "attribution"
private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val TAG_ATTR = "tag"
private const val LABEL_ATTR = "label"

/**
 * Label-resource-id of an attribution of a package/user.
 *
 * <p>Obviously the resource is found in the package, hence needs to be loaded via a Resources
 * object created for this package.
 */
class AttributionLabelLiveData private constructor(
    private val app: Application,
    private val attributionTag: String?,
    private val packageName: String,
    private val user: UserHandle
) : SmartAsyncMediatorLiveData<Int>(), PackageBroadcastReceiver.PackageBroadcastListener {
    private val LOG_TAG = AttributionLabelLiveData::class.java.simpleName

    override suspend fun loadDataAndPostValue(job: Job) {
        if (job.isCancelled) {
            return
        }

        if (attributionTag == null) {
            postValue(ID_NULL)
            return
        }

        val pkgContext = try {
            app.createPackageContextAsUser(packageName, 0, user)
        } catch (e: NameNotFoundException) {
            Log.e(LOG_TAG, "Cannot find $packageName for $user")

            postValue(null)
            return
        }

        // TODO (moltmann): Read this from PackageInfo once available
        var cookie = 0
        while (true) {
            // Some resources have multiple "AndroidManifest.xml" loaded and hence we need
            // to find the right one
            cookie++
            val parser = try {
                pkgContext.assets.openXmlResourceParser(cookie, MANIFEST_FILE_NAME)
            } catch (e: FileNotFoundException) {
                break
            }

            try {
                do {
                    if (parser.eventType != START_TAG) {
                        continue
                    }

                    if (parser.name != MANIFEST_TAG) {
                        parser.skipTag()
                        continue
                    }

                    // Ensure this is the right manifest
                    if (parser.getAttributeValue(null, PKG_ATTR) != packageName) {
                        break
                    }

                    while (parser.next() != END_TAG) {
                        if (parser.eventType != START_TAG) {
                            continue
                        }

                        if (parser.name != ATTRIBUTION_TAG) {
                            parser.skipTag()
                            continue
                        }

                        if (parser.getAttributeValue(ANDROID_NS, TAG_ATTR) == attributionTag) {
                            postValue(parser.getAttributeResourceValue(ANDROID_NS, LABEL_ATTR,
                                    ID_NULL))
                            return
                        } else {
                            parser.skipTag()
                        }
                    }
                } while (parser.next() != END_DOCUMENT)
            } finally {
                parser.close()
            }
        }

        postValue(null)
    }

    /**
     * Skip tag parser is currently pointing to (including all tags nested in it)
     */
    private fun XmlPullParser.skipTag() {
        var depth = 1
        while (depth != 0) {
            when (next()) {
                END_TAG -> depth--
                START_TAG -> depth++
            }
        }
    }

    override fun onActive() {
        super.onActive()

        // Listen for changes to the attributions
        PackageBroadcastReceiver.addChangeCallback(packageName, this)
        update()
    }

    override fun onInactive() {
        super.onInactive()

        PackageBroadcastReceiver.removeChangeCallback(packageName, this)
    }

    override fun onPackageUpdate(packageName: String) {
        update()
    }

    /**
     * Repository for AttributionLiveData.
     * <p> Key value is a pair of string attribution tag, string package name, user handle, value is
     * its corresponding LiveData.
     */
    companion object : DataRepository<Triple<String?, String, UserHandle>,
            AttributionLabelLiveData>() {
        override fun newValue(key: Triple<String?, String, UserHandle>): AttributionLabelLiveData {
            return AttributionLabelLiveData(PermissionControllerApplication.get(),
                    key.first, key.second, key.third)
        }

        operator fun get(attributionTag: String?, packageName: String, user: UserHandle):
                AttributionLabelLiveData =
            get(Triple(attributionTag, packageName, user))
    }
}
