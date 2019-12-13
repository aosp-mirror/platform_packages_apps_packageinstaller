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

package com.android.permissioncontroller.permission.ui.handheld

import android.app.Application
import android.content.Context
import android.os.UserHandle
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.preference.AndroidResources
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.AppPermissionActivity
import com.android.permissioncontroller.permission.utils.KotlinUtils

/**
 * A Preference representing a package for a user, which loads and displays its icon only upon
 * being bound to a viewHolder. This lets us synchronously load package icons and labels, while
 * still displaying the PermissionAppsFragment instantly.
 *
 * @param app The current application
 * @param packageName The name of the package whose icon this preference will retrieve
 * @param user The user whose package icon will be retrieved
 * @param context The current context
 * @param groupName The name of the permission group this Preference is showing for
 * @param caller The name of the caller of this constructor. See
 * @see AppPermissionActivity.EXTRA_CALLER_NAME
 * @param sessionId An int representing the current session
 * @param grantCategory The granted state of the app represented by this preference, will be
 * passed on to the App Permission Fragment
 */
open class SmartIconLoadPackagePermissionPreference @JvmOverloads constructor(
    private val app: Application,
    private val packageName: String,
    private val user: UserHandle,
    context: Context,
    private val groupName: String,
    private val caller: String,
    private val sessionId: Long = 0,
    var grantCategory: String
) : Preference(context) {

    /**
     * Loads the package's badged icon upon being bound to a viewholder. This allows us to load
     * icons synchronously, because we only load the icons that are visible on the screen.
     */
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val title = holder.findViewById(android.R.id.title) as TextView
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END

        val imageView = holder.findViewById(android.R.id.icon) as ImageView

        imageView.maxWidth =
            context.resources.getDimensionPixelSize(R.dimen.secondary_app_icon_size)
        imageView.maxHeight =
            context.resources.getDimensionPixelSize(R.dimen.secondary_app_icon_size)
        imageView.setImageDrawable(KotlinUtils.getBadgedPackageIcon(app, packageName, user))
        imageView.visibility = View.VISIBLE

        var imageFrame: View? = holder.findViewById(R.id.icon_frame)
        if (imageFrame == null) {
            imageFrame = holder.findViewById(AndroidResources.ANDROID_R_ICON_FRAME)
        }
        if (imageFrame != null) {
            imageFrame.visibility = View.VISIBLE
        }
        setOnPreferenceClickListener {
            val args = AppPermissionFragment.createArgs(packageName, null, groupName,
                user, caller, sessionId, grantCategory)
            holder.itemView.findNavController().navigate(R.id.perm_apps_to_app, args)
            true
        }
    }
}
