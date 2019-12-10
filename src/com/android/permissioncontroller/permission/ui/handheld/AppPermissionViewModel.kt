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

import android.Manifest.permission_group
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_PERMISSION_NAME
import android.os.Build
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_VIEWED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.AppPermGroupLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.model.AppPermissionGroup
import com.android.permissioncontroller.permission.model.PermissionUsages
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.SafetyNetLogger
import com.android.permissioncontroller.permission.utils.Utils
import com.android.settingslib.RestrictedLockUtils
import java.util.Random

/**
 * ViewModel for the AppPermissionFragment. Determines button state and detail text strings, logs
 * permission change information, and makes permission changes.
 *
 * @param app: The current application
 * @param packageName: The name of the package this ViewModel represents
 * @param permGroupName: The name of the permission group this ViewModel represents
 * @param user: The user of the package
 * @param sessionId: A session ID used in logs to identify this particular session
 */
class AppPermissionViewModel(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModel() {

    companion object {
        private val LOG_TAG = AppPermissionViewModel::class.java.simpleName
    }

    enum class ChangeTarget(val value: Int) {
        CHANGE_FOREGROUND(1),
        CHANGE_BACKGROUND(2),
        CHANGE_BOTH(CHANGE_FOREGROUND.value or CHANGE_BACKGROUND.value);

        infix fun andValue(other: ChangeTarget): Int {
            return value and other.value
        }
    }

    private var hasConfirmedRevoke = false
    private var appPermissionGroup: AppPermissionGroup? = null
    private var lightAppPermGroup: LightAppPermGroup? = null

    /**
     * A livedata which computes the state of the radio buttons
     */
    val buttonStateLiveData = AppPermButtonStateLiveData()
    /**
     * A livedata which determines which detail string, if any, should be shown
     */
    val detailResIdLiveData = SmartUpdateMediatorLiveData<Pair<Int, Int?>>()
    /**
     * A livedata which stores the device admin, if there is one
     */
    val showAdminSupportLiveData = SmartUpdateMediatorLiveData<RestrictedLockUtils.EnforcedAdmin>()

    data class ButtonState(
        var isChecked: Boolean,
        var isEnabled: Boolean,
        var isShown: Boolean,
        var customTarget: ChangeTarget?
    ) {
        constructor() : this(false, true, false, null)
    }

    inner class AppPermButtonStateLiveData
        : SmartUpdateMediatorLiveData<@kotlin.jvm.JvmSuppressWildcards List<ButtonState>>() {

        private val appPermGroupLiveData = AppPermGroupLiveData(app, packageName,
                permGroupName, user)

        init {
            addSource(appPermGroupLiveData) { appPermGroup ->
                lightAppPermGroup = appPermGroup
                if (appPermGroupLiveData.isInitialized && appPermGroup == null) {
                    value = null
                } else if (appPermGroup != null) {
                    if (value == null) {
                        logAppPermissionFragmentViewed()
                    }
                    update()
                }
            }
        }

        fun update() {
            appPermissionGroup = AppPermissionGroup.create(app, packageName, permGroupName, user,
                    false)
            if (appPermissionGroup == null) {
                value = null
                return
            }

            val group = appPermGroupLiveData.value ?: return

            val admin = RestrictedLockUtils.getProfileOrDeviceOwner(app, user)

            val allowedState = ButtonState()
            val allowedAlwaysState = ButtonState()
            val allowedForegroundState = ButtonState()
            val askOneTimeState = ButtonState()
            val askState = ButtonState()
            val deniedState = ButtonState()
            val deniedForegroundState = ButtonState()

            askOneTimeState.isShown = group.isOneTime
            askState.isShown = !group.isOneTime
            deniedState.isShown = true

            askOneTimeState.isChecked = group.isOneTime

            if (group.hasPermWithBackground) {
                // Background / Foreground / Deny case
                allowedForegroundState.isShown = true
                if (group.hasBackgroundPerms) {
                    allowedAlwaysState.isShown = true
                }

                allowedAlwaysState.isChecked = group.isBackgroundGranted &&
                        group.isForegroundGranted
                allowedForegroundState.isChecked = group.isForegroundGranted &&
                        !group.isBackgroundGranted
                askState.isChecked = !group.isForegroundGranted && !group.isUserFixed
                deniedState.isChecked = !group.isForegroundGranted && group.isUserFixed

                if (applyFixToForegroundBackground(group, group.isForegroundSystemFixed,
                                group.isBackgroundSystemFixed, allowedAlwaysState,
                                allowedForegroundState, askState, deniedState,
                                deniedForegroundState) ||
                        applyFixToForegroundBackground(group, group.isForegroundPolicyFixed,
                                group.isBackgroundPolicyFixed, allowedAlwaysState,
                                allowedForegroundState, askState, deniedState,
                                deniedForegroundState)) {
                    showAdminSupportLiveData.value = admin
                    val detailId = getDetailResIdForFixedByPolicyPermissionGroup(group,
                            admin != null)
                    if (detailId != 0) {
                        detailResIdLiveData.value = detailId to null
                    }
                } else if (Utils.areGroupPermissionsIndividuallyControlled(app, permGroupName)) {
                    val detailId = getIndividualPermissionDetailResId(group)
                    detailResIdLiveData.value = detailId.first to detailId.second
                }
            } else {
                // Allow / Deny case
                allowedState.isShown = true

                allowedState.isChecked = group.isForegroundGranted
                askState.isChecked = !group.isForegroundGranted && !group.isUserFixed
                deniedState.isChecked = !group.isForegroundGranted && group.isUserFixed

                if (group.isForegroundPolicyFixed) {
                    allowedState.isEnabled = false
                    askState.isEnabled = false
                    deniedState.isEnabled = false
                }
            }
            if (group.packageInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                // Pre-M app's can't ask for runtime permissions
                askState.isShown = false
                deniedState.isChecked = askState.isChecked || deniedState.isChecked
                deniedForegroundState.isChecked = askState.isChecked ||
                        deniedForegroundState.isChecked
            }
            value = listOf(allowedState, allowedAlwaysState, allowedForegroundState,
                    askOneTimeState, askState, deniedState, deniedForegroundState)
        }

        override fun onActive() {
            super.onActive()
            appPermissionGroup = AppPermissionGroup.create(app, packageName, permGroupName, user,
                    false)
        }
    }

    /**
     * Modifies the radio buttons to reflect the current policy fixing state
     *
     * @return if anything was changed
     */
    private fun applyFixToForegroundBackground(
        group: LightAppPermGroup,
        isForegroundFixed: Boolean,
        isBackgroundFixed: Boolean,
        allowedAlwaysState: ButtonState,
        allowedForegroundState: ButtonState,
        askState: ButtonState,
        deniedState: ButtonState,
        deniedForegroundState: ButtonState
    ): Boolean {
        if (isBackgroundFixed && isForegroundFixed) {
            // Background and foreground are both policy fixed. Disable everything
            allowedAlwaysState.isEnabled = false
            allowedForegroundState.isEnabled = false
            askState.isEnabled = false
            deniedState.isEnabled = false

            if (askState.isChecked) {
                askState.isChecked = false
                deniedState.isChecked = true
            }
        } else if (isBackgroundFixed && !isForegroundFixed) {
            if (group.isBackgroundGranted) {
                // Background policy fixed as granted, foreground flexible. Granting
                // foreground implies background comes with it in this case.
                // Only allow user to grant background or deny (which only toggles fg)
                allowedForegroundState.isEnabled = false
                askState.isEnabled = false
                deniedState.isShown = false
                deniedForegroundState.isShown = true
                deniedForegroundState.isChecked = deniedState.isChecked

                if (askState.isChecked) {
                    askState.isChecked = false
                    deniedState.isChecked = true
                }
            } else {
                // Background policy fixed as not granted, foreground flexible
                allowedAlwaysState.isEnabled = false
            }
        } else if (!isBackgroundFixed && isForegroundFixed) {
            if (group.isForegroundGranted) {
                // Foreground is fixed as granted, background flexible.
                // Allow switching between foreground and background. No denying
                askState.isEnabled = false
                deniedState.isEnabled = false
            } else {
                // Foreground is fixed denied. Background irrelevant
                allowedAlwaysState.isEnabled = false
                allowedForegroundState.isEnabled = false
                askState.isEnabled = false
                deniedState.isEnabled = false

                if (askState.isChecked) {
                    askState.isChecked = false
                    deniedState.isChecked = true
                }
            }
        } else {
            return false
        }
        return true
    }

    /**
     * Finish the current activity due to a data error, and display a short message to the user
     * saying "app not found".
     *
      * @param activity: The current activity
     */
    fun finishActivity(activity: Activity) {
        Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show()
        activity.setResult(Activity.RESULT_CANCELED)
        activity.finish()
    }

    /**
     * Set the bottom links to link either to the App Permission Groups screen, or the
     * Permission Apps Screen. If we just came from one of those two screens, hide the
     * corresponding link
     *
     * @param context: The fragment context
     * @param view: The TextView to be set
     * @param caller: The name of the fragment which called this fragment
     * @param action: The action to be taken
     */
    fun setBottomLinkState(context: Context, view: TextView, caller: String, action: String) {
        if ((caller == AppPermissionGroupsFragment::class.java.name &&
                        action == Intent.ACTION_MANAGE_APP_PERMISSIONS) ||
                (caller == PermissionAppsFragment::class.java.name &&
                        action == Intent.ACTION_MANAGE_PERMISSION_APPS)) {
            view.visibility = View.GONE
        } else {
            view.setOnClickListener {
                val intent = Intent(action)
                if (action == Intent.ACTION_MANAGE_PERMISSION_APPS) {
                    intent.putExtra(EXTRA_PERMISSION_NAME, permGroupName)
                } else {
                    intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                }
                intent.putExtra(EXTRA_SESSION_ID, sessionId)
                intent.putExtra(Intent.EXTRA_USER, user)
                context.startActivity(intent)
            }
        }
    }

    /**
     * @return Whether or not the fragment should show the permission usage string
     */
    fun shouldShowUsageView(): Boolean {
        return Utils.isPermissionsHubEnabled() && Utils.isModernPermissionGroup(permGroupName)
    }

    /**
     * @return Whether or not the fragment should get the individual permission usage to display
     */
    fun shouldShowPermissionUsage(): Boolean {
        return shouldShowUsageView() && Utils.shouldShowPermissionUsage(permGroupName)
    }

    /**
     * Request to grant/revoke permissions group.
     *
     * Does <u>not</u> handle:
     *
     *  * Individually granted permissions
     *  * Permission groups with background permissions
     *
     * <u>Does</u> handle:
     *
     *  * Default grant permissions
     *
     * @param requestGrant If this group should be granted
     * @param changeTarget Which permission group (foreground/background/both) should be changed
     *
     * @return The dialogue to show, if applicable, or if the request was processed.
     */
    fun requestChange(
        requestGrant: Boolean,
        userFixed: Boolean,
        fragment: AppPermissionFragment,
        changeTarget: ChangeTarget
    ) {
        val context = fragment.context ?: return
        val group = appPermissionGroup ?: return

        if (LocationUtils.isLocationGroupAndProvider(context, group.name,
                        group.app.packageName)) {
            val packageLabel = KotlinUtils.getPackageLabel(app, packageName, user)
            LocationUtils.showLocationDialog(context, packageLabel)
        }

        val shouldChangeForeground = changeTarget andValue ChangeTarget.CHANGE_FOREGROUND != 0
        val shouldChangeBackground = changeTarget andValue ChangeTarget.CHANGE_BACKGROUND != 0

        if (requestGrant) {
            val stateBefore = createPermissionSnapshot()!!
            if (shouldChangeForeground) {
                val runtimePermissionsGranted = group.areRuntimePermissionsGranted()
                group.grantRuntimePermissions(userFixed)

                if (!runtimePermissionsGranted) {
                    SafetyNetLogger.logPermissionToggled(group)
                }
            }
            if (shouldChangeBackground && group.backgroundPermissions != null) {
                val runtimePermissionsGranted =
                        group.backgroundPermissions.areRuntimePermissionsGranted()
                group.backgroundPermissions.grantRuntimePermissions(userFixed)

                if (!runtimePermissionsGranted) {
                    SafetyNetLogger.logPermissionToggled(group.backgroundPermissions)
                }
            }
            logPermissionChanges(stateBefore)
        } else {
            var showDefaultDenyDialog = false
            var showGrantedByDefaultWarning = false

            if (shouldChangeForeground && group.areRuntimePermissionsGranted()) {
                showDefaultDenyDialog = (group.hasGrantedByDefaultPermission() ||
                        !group.doesSupportRuntimePermissions() ||
                        group.hasInstallToRuntimeSplit())
                showGrantedByDefaultWarning = showGrantedByDefaultWarning ||
                        group.hasGrantedByDefaultPermission()
            }

            if (shouldChangeBackground &&
                    group.backgroundPermissions != null &&
                    group.backgroundPermissions.areRuntimePermissionsGranted()) {
                val bgGroup = group.backgroundPermissions
                showDefaultDenyDialog = showDefaultDenyDialog ||
                        bgGroup.hasGrantedByDefaultPermission() ||
                        !bgGroup.doesSupportRuntimePermissions() ||
                        bgGroup.hasInstallToRuntimeSplit()
                showGrantedByDefaultWarning = showGrantedByDefaultWarning ||
                        bgGroup.hasGrantedByDefaultPermission()
            }

            if (showDefaultDenyDialog && !hasConfirmedRevoke && showGrantedByDefaultWarning) {
                fragment.showDefaultDenyDialog(changeTarget, R.string.system_warning, userFixed)
                return
            } else if (showDefaultDenyDialog && !hasConfirmedRevoke) {
                fragment.showDefaultDenyDialog(changeTarget, R.string.old_sdk_deny_warning,
                        userFixed)
                return
            } else {
                val stateBefore = createPermissionSnapshot()!!
                if (shouldChangeForeground &&
                        group.areRuntimePermissionsGranted()) {
                    group.revokeRuntimePermissions(userFixed)

                    SafetyNetLogger.logPermissionToggled(group)
                }
                if (shouldChangeBackground &&
                        group.backgroundPermissions != null &&
                        group.backgroundPermissions.areRuntimePermissionsGranted()) {
                    group.backgroundPermissions.revokeRuntimePermissions(userFixed)

                    SafetyNetLogger.logPermissionToggled(group.backgroundPermissions)
                }
                if (userFixed && !group.isUserFixed) {
                    group.revokeRuntimePermissions(true)
                    if (group.backgroundPermissions != null) {
                        group.backgroundPermissions.revokeRuntimePermissions(true)
                    }
                }
                if (!userFixed && group.isUserFixed) {
                    group.revokeRuntimePermissions(false)
                    if (group.backgroundPermissions != null) {
                        group.backgroundPermissions.revokeRuntimePermissions(false)
                    }
                }
                logPermissionChanges(stateBefore)
            }
        }
    }

    /**
     * Once the user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @param changeTarget whether to change foreground, background, or both.
     *
     */
    fun onDenyAnyWay(changeTarget: ChangeTarget, userFixed: Boolean) {
        val group = appPermissionGroup ?: return
        var hasDefaultPermissions = false
        val stateBefore = createPermissionSnapshot()
        if (changeTarget andValue ChangeTarget.CHANGE_FOREGROUND != 0) {
            val runtimePermissionsGranted = group.areRuntimePermissionsGranted()
            group.revokeRuntimePermissions(userFixed)

            if (runtimePermissionsGranted) {
                SafetyNetLogger.logPermissionToggled(group)
            }
            hasDefaultPermissions = group.hasGrantedByDefaultPermission()
        }
        if (changeTarget andValue ChangeTarget.CHANGE_BACKGROUND != 0 &&
            group.backgroundPermissions != null) {
            val runtimePermissionsGranted =
                    group.backgroundPermissions.areRuntimePermissionsGranted()
            group.backgroundPermissions.revokeRuntimePermissions(userFixed)

            if (runtimePermissionsGranted) {
                SafetyNetLogger.logPermissionToggled(group.backgroundPermissions)
            }
            hasDefaultPermissions = hasDefaultPermissions ||
                    group.backgroundPermissions.hasGrantedByDefaultPermission()
        }
        logPermissionChanges(stateBefore!!)

        if (hasDefaultPermissions || !group.doesSupportRuntimePermissions()) {
            hasConfirmedRevoke = true
        }
    }

    private fun createPermissionSnapshot(): List<PermissionState>? {
        val group = lightAppPermGroup ?: return null
        val permissionSnapshot = ArrayList<PermissionState>()

        for ((permName, permission) in group.permissions) {
            permissionSnapshot.add(PermissionState(permName, permission.grantedIncludingAppOp))
        }

        return permissionSnapshot
    }

    /**
     * Get the usage summary for this App Permission Group
     *
     * @param context: The context from which to get the strings
     */
    fun getUsageSummary(context: Context, permGroupLabel: String, packageLabel: String): String {
        val group = appPermissionGroup ?: AppPermissionGroup.create(app, packageName, permGroupName,
                user, false)
        val timeDiffStr = Utils.getRelativeLastUsageString(context,
                PermissionUsages.loadLastGroupUsage(context, group))
        val label = permGroupLabel.toLowerCase()

        return if (timeDiffStr == null) {
            val strResId = getUsageStringResId(false)
            if (strResId == R.string.app_permission_footer_no_usages_generic) {
                context.getString(strResId, packageLabel, label)
            } else context.getString(strResId, packageLabel)
        } else {
            val strResId = getUsageStringResId(true)
            if (strResId == R.string.app_permission_footer_usage_summary_generic) {
                context.getString(strResId, packageLabel, label,
                        timeDiffStr)
            } else context.getString(strResId, packageLabel, timeDiffStr)
        }
    }

    private fun getUsageStringResId(hasUsage: Boolean): Int {
        if (hasUsage) {
            return when (permGroupName) {
                permission_group.ACTIVITY_RECOGNITION ->
                    R.string.app_permission_footer_usage_summary_activity_recognition
                permission_group.CALENDAR -> R.string.app_permission_footer_usage_summary_calendar
                permission_group.CALL_LOG -> R.string.app_permission_footer_usage_summary_call_log
                permission_group.CAMERA -> R.string.app_permission_footer_usage_summary_camera
                permission_group.CONTACTS -> R.string.app_permission_footer_usage_summary_contacts
                permission_group.LOCATION -> R.string.app_permission_footer_usage_summary_location
                permission_group.MICROPHONE ->
                    R.string.app_permission_footer_usage_summary_microphone
                permission_group.PHONE -> R.string.app_permission_footer_usage_summary_phone
                permission_group.SENSORS -> R.string.app_permission_footer_usage_summary_sensors
                permission_group.SMS -> R.string.app_permission_footer_usage_summary_sms
                permission_group.STORAGE -> R.string.app_permission_footer_usage_summary_storage
                else -> R.string.app_permission_footer_usage_summary_generic
            }
        } else {
            return when (permGroupName) {
                permission_group.ACTIVITY_RECOGNITION ->
                    R.string.app_permission_footer_no_usages_activity_recognition
                permission_group.CALENDAR -> R.string.app_permission_footer_no_usages_calendar
                permission_group.CALL_LOG -> R.string.app_permission_footer_no_usages_call_log
                permission_group.CAMERA -> R.string.app_permission_footer_no_usages_camera
                permission_group.CONTACTS -> R.string.app_permission_footer_no_usages_contacts
                permission_group.LOCATION -> R.string.app_permission_footer_no_usages_location
                permission_group.MICROPHONE -> R.string.app_permission_footer_no_usages_microphone
                permission_group.PHONE -> R.string.app_permission_footer_no_usages_phone
                permission_group.SENSORS -> R.string.app_permission_footer_no_usages_sensors
                permission_group.SMS -> R.string.app_permission_footer_no_usages_sms
                permission_group.STORAGE -> R.string.app_permission_footer_no_usages_storage
                else -> R.string.app_permission_footer_no_usages_generic
            }
        }
    }

    private fun getIndividualPermissionDetailResId(group: LightAppPermGroup): Pair<Int, Int> {
        return when (val numRevoked =
            group.permissions.filter { !it.value.grantedIncludingAppOp }.size) {
            0 -> R.string.permission_revoked_none to numRevoked
            group.permissions.size -> R.string.permission_revoked_all to numRevoked
            else -> R.string.permission_revoked_count to numRevoked
        }
    }

    /**
     * Get the detail string id of a permission group if it is at least partially fixed by policy.
     */
    private fun getDetailResIdForFixedByPolicyPermissionGroup(
        group: LightAppPermGroup,
        hasAdmin: Boolean
    ): Int {
        val isForegroundPolicyDenied = group.isForegroundPolicyFixed && !group.isForegroundGranted
        val isPolicyFullyFixedWithGrantedOrNoBkg = group.isPolicyFullyFixed &&
                (group.isBackgroundGranted || !group.hasBackgroundPerms)
        if (group.isForegroundSystemFixed || group.isBackgroundSystemFixed) {
            return R.string.permission_summary_enabled_system_fixed
        } else if (hasAdmin) {
            // Permission is fully controlled by policy and cannot be switched
            if (isForegroundPolicyDenied) {
                return R.string.disabled_by_admin
            } else if (isPolicyFullyFixedWithGrantedOrNoBkg) {
                return R.string.enabled_by_admin
            } else if (group.isPolicyFullyFixed) {
                return R.string.permission_summary_enabled_by_admin_foreground_only
            }

            // Part of the permission group can still be switched
            if (group.isBackgroundPolicyFixed && group.isBackgroundGranted) {
                return R.string.permission_summary_enabled_by_admin_background_only
            } else if (group.isBackgroundPolicyFixed) {
                return R.string.permission_summary_disabled_by_admin_background_only
            } else if (group.isForegroundPolicyFixed) {
                return R.string.permission_summary_enabled_by_admin_foreground_only
            }
        } else {
            // Permission is fully controlled by policy and cannot be switched
            if ((isForegroundPolicyDenied) || isPolicyFullyFixedWithGrantedOrNoBkg) {
                // Permission is fully controlled by policy and cannot be switched
                // State will be displayed by switch, so no need to add text for that
                return R.string.permission_summary_enforced_by_policy
            } else if (group.isPolicyFullyFixed) {
                return R.string.permission_summary_enabled_by_policy_foreground_only
            }

            // Part of the permission group can still be switched
            if (group.isBackgroundPolicyFixed && group.isBackgroundGranted) {
                return R.string.permission_summary_enabled_by_policy_background_only
            } else if (group.isBackgroundPolicyFixed) {
                return R.string.permission_summary_disabled_by_policy_background_only
            } else if (group.isForegroundPolicyFixed) {
                return R.string.permission_summary_enabled_by_policy_foreground_only
            }
        }
        return 0
    }

    data class PermissionState(val permissionName: String, val permissionGranted: Boolean)

    private fun logPermissionChanges(previousPermissionSnapshot: List<PermissionState>) {
        val group = appPermissionGroup ?: return

        val changeId = Random().nextLong()

        for ((permissionName, wasGranted) in previousPermissionSnapshot) {
            val permission = group.getPermission(permissionName)
                    ?: group.backgroundPermissions?.getPermission(permissionName)
                    ?: continue

            val isGranted = permission.isGrantedIncludingAppOp

            if (wasGranted != isGranted) {
                logAppPermissionFragmentActionReported(changeId, permissionName, isGranted)
            }
        }
    }

    private fun logAppPermissionFragmentActionReported(
        changeId: Long,
        permissionName: String,
        isGranted: Boolean
    ) {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return
        PermissionControllerStatsLog.write(APP_PERMISSION_FRAGMENT_ACTION_REPORTED, sessionId,
                changeId, uid, packageName,
                permissionName, isGranted)
        Log.v(LOG_TAG, "Permission changed via UI with sessionId=$sessionId changeId=" +
                "$changeId uid=$uid packageName=" +
                "$packageName permission=$permissionName isGranted=$isGranted")
    }

    /**
     * Logs information about this AppPermissionGroup and view session
     */
    fun logAppPermissionFragmentViewed() {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return
        PermissionControllerStatsLog.write(APP_PERMISSION_FRAGMENT_VIEWED, sessionId,
                uid, packageName, permGroupName)
        Log.v(LOG_TAG, "AppPermission fragment viewed with sessionId=$sessionId uid=" +
                "$uid packageName=$packageName" +
                "permGroupName=$permGroupName")
    }
}

/**
 * Factory for an AppPermissionViewModel
 *
 * @param app: The current application
 * @param packageName: The name of the package this ViewModel represents
 * @param permGroupName: The name of the permission group this ViewModel represents
 * @param user: The user of the package
 * @param sessionId: A session ID used in logs to identify this particular session
 */
class AppPermissionViewModelFactory(
    private val app: Application,
    private val packageName: String,
    private val permGroupName: String,
    private val user: UserHandle,
    private val sessionId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppPermissionViewModel(app, packageName, permGroupName, user, sessionId) as T
    }
}