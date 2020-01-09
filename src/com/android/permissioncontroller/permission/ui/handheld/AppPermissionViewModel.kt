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

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.android.permissioncontroller.PermissionControllerStatsLog
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_ACTION_REPORTED
import com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSION_FRAGMENT_VIEWED
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.LightAppPermGroupLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission
import com.android.permissioncontroller.permission.utils.KotlinUtils
import com.android.permissioncontroller.permission.utils.LocationUtils
import com.android.permissioncontroller.permission.utils.SafetyNetLogger
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.ALLOW
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.ALLOW_ALWAYS
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.ALLOW_FOREGROUND
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.ASK_ONCE
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.ASK
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.DENY
import com.android.permissioncontroller.permission.ui.handheld.AppPermissionViewModel.ButtonType.DENY_FOREGROUND
import com.android.permissioncontroller.permission.utils.Utils
import com.android.settingslib.RestrictedLockUtils
import java.util.Random
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.filter
import kotlin.collections.iterator
import kotlin.jvm.JvmSuppressWildcards

/**
 * ViewModel for the AppPermissionFragment. Determines button state and detail text strings, logs
 * permission change information, and makes permission changes.
 *
 * @param app The current application
 * @param packageName The name of the package this ViewModel represents
 * @param permGroupName The name of the permission group this ViewModel represents
 * @param user The user of the package
 * @param sessionId A session ID used in logs to identify this particular session
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

    enum class ButtonType(val type: Int) {
        ALLOW(0),
        ALLOW_ALWAYS(1),
        ALLOW_FOREGROUND(2),
        ASK_ONCE(3),
        ASK(4),
        DENY(5),
        DENY_FOREGROUND(6);
    }

    private var hasConfirmedRevoke = false
    private var lightAppPermGroup: LightAppPermGroup? = null

    /**
     * A livedata which computes the state of the radio buttons
     */
    val buttonStateLiveData = AppPermButtonStateLiveData()
    /**
     * A livedata which determines which detail string, if any, should be shown
     */
    val detailResIdLiveData = MutableLiveData<Pair<Int, Int?>>()
    /**
     * A livedata which stores the device admin, if there is one
     */
    val showAdminSupportLiveData = MutableLiveData<RestrictedLockUtils.EnforcedAdmin>()

    data class ButtonState(
        var isChecked: Boolean,
        var isEnabled: Boolean,
        var isShown: Boolean,
        var customTarget: ChangeTarget?
    ) {
        constructor() : this(false, true, false, null)
    }

    inner class AppPermButtonStateLiveData
        : SmartUpdateMediatorLiveData<@JvmSuppressWildcards Map<ButtonType, ButtonState>>() {

        private val appPermGroupLiveData = LightAppPermGroupLiveData[packageName, permGroupName,
            user]

        init {
            addSource(appPermGroupLiveData) { appPermGroup ->
                lightAppPermGroup = appPermGroup
                if (appPermGroupLiveData.isInitialized && appPermGroup == null) {
                    value = null
                } else if (appPermGroup != null) {
                    if (value == null) {
                        logAppPermissionFragmentViewed()
                    }
                    updateIfActive()
                }
            }
        }

        override fun onUpdate() {
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

            if (group.hasPermWithBackgroundMode) {
                // Background / Foreground / Deny case
                allowedForegroundState.isShown = true
                if (group.hasBackgroundGroup) {
                    allowedAlwaysState.isShown = true
                }

                allowedAlwaysState.isChecked = group.background.isGranted &&
                        group.foreground.isGranted
                allowedForegroundState.isChecked = group.foreground.isGranted &&
                        !group.background.isGranted && !group.isOneTime
                val groupUserFixed = group.foreground.isUserFixed || group.background.isUserFixed
                askState.isChecked = !group.foreground.isGranted && !groupUserFixed
                deniedState.isChecked = !group.foreground.isGranted && groupUserFixed

                if (applyFixToForegroundBackground(group, group.foreground.isSystemFixed,
                                group.background.isSystemFixed, allowedAlwaysState,
                                allowedForegroundState, askState, deniedState,
                                deniedForegroundState) ||
                        applyFixToForegroundBackground(group, group.foreground.isPolicyFixed,
                                group.background.isPolicyFixed, allowedAlwaysState,
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

                allowedState.isChecked = group.foreground.isGranted
                askState.isChecked = !group.foreground.isGranted && !group.foreground.isUserFixed
                deniedState.isChecked = !group.foreground.isGranted && group.foreground.isUserFixed

                if (group.foreground.isPolicyFixed || group.foreground.isSystemFixed) {
                    allowedState.isEnabled = false
                    askState.isEnabled = false
                    deniedState.isEnabled = false
                    val detailId = getDetailResIdForFixedByPolicyPermissionGroup(group,
                            admin != null)
                    if (detailId != 0) {
                        detailResIdLiveData.value = detailId to null
                    }
                }
            }
            if (group.packageInfo.targetSdkVersion < Build.VERSION_CODES.M) {
                // Pre-M app's can't ask for runtime permissions
                askState.isShown = false
                deniedState.isChecked = askState.isChecked || deniedState.isChecked
                deniedForegroundState.isChecked = askState.isChecked ||
                        deniedForegroundState.isChecked
            }
            value = mapOf(ALLOW to allowedState, ALLOW_ALWAYS to allowedAlwaysState,
                ALLOW_FOREGROUND to allowedForegroundState, ASK_ONCE to askOneTimeState,
                ASK to askState, DENY to deniedState, DENY_FOREGROUND to deniedForegroundState)
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
            if (group.background.isGranted) {
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
            if (group.foreground.isGranted) {
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
     * @param activity The current activity
     */
    fun finishActivity(activity: Activity) {
        Toast.makeText(activity, R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show()
        activity.setResult(Activity.RESULT_CANCELED)
        activity.finish()
    }

    /**
     * Navigate to either the App Permission Groups screen, or the Permission Apps Screen.
     * @param fragment The current fragment
     * @param action The action to be taken
     */
    fun showBottomLinkPage(fragment: Fragment, action: String) {
        lateinit var args: Bundle
        var actionId = R.id.app_to_perm_apps
        if (action == Intent.ACTION_MANAGE_PERMISSION_APPS) {
            args = PermissionAppsFragment.createArgs(permGroupName, sessionId)
        } else {
            args = AppPermissionGroupsFragment.createArgs(packageName, user, sessionId, true)
            actionId = R.id.app_to_perm_groups
        }

        fragment.findNavController().navigate(actionId, args)
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
     * @param buttonClicked button which was pressed to initiate the change, one of
     *                      AppPermissionFragmentActionReported.button_pressed constants
     *
     * @return The dialogue to show, if applicable, or if the request was processed.
     */
    fun requestChange(
        requestGrant: Boolean,
        userFixed: Boolean,
        fragment: AppPermissionFragment,
        changeTarget: ChangeTarget,
        buttonClicked: Int
    ) {
        val context = fragment.context ?: return
        val group = lightAppPermGroup ?: return
        val wasForegroundGranted = group.foreground.isGranted
        val wasBackgroundGranted = group.background.isGranted

        if (LocationUtils.isLocationGroupAndProvider(context, permGroupName, packageName)) {
            val packageLabel = KotlinUtils.getPackageLabel(app, packageName, user)
            LocationUtils.showLocationDialog(context, packageLabel)
        }

        val shouldChangeForeground = changeTarget andValue ChangeTarget.CHANGE_FOREGROUND != 0
        val shouldChangeBackground = changeTarget andValue ChangeTarget.CHANGE_BACKGROUND != 0

        if (requestGrant) {
            var newGroup = group
            val oldGroup = group
            if (shouldChangeForeground) {
                newGroup = KotlinUtils.grantForegroundRuntimePermissions(app, newGroup)

                if (!wasForegroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup)
                }
            }
            if (shouldChangeBackground && group.hasBackgroundGroup) {
                newGroup = KotlinUtils.grantBackgroundRuntimePermissions(app, newGroup)

                if (!wasBackgroundGranted) {
                    SafetyNetLogger.logPermissionToggled(newGroup, true)
                }
            }
            logPermissionChanges(oldGroup, newGroup, buttonClicked)
        } else {
            var showDefaultDenyDialog = false
            var showGrantedByDefaultWarning = false

            if (shouldChangeForeground && wasForegroundGranted) {
                showDefaultDenyDialog = (group.foreground.isGrantedByDefault ||
                    !group.supportsRuntimePerms ||
                    group.hasInstallToRuntimeSplit)
                showGrantedByDefaultWarning = showGrantedByDefaultWarning ||
                    group.foreground.isGrantedByDefault
            }

            if (shouldChangeBackground && wasBackgroundGranted) {
                showDefaultDenyDialog = showDefaultDenyDialog ||
                    group.background.isGrantedByDefault ||
                    !group.supportsRuntimePerms ||
                    group.hasInstallToRuntimeSplit
                showGrantedByDefaultWarning = showGrantedByDefaultWarning ||
                    group.background.isGrantedByDefault
            }

            if (showDefaultDenyDialog && !hasConfirmedRevoke && showGrantedByDefaultWarning) {
                fragment.showDefaultDenyDialog(changeTarget, R.string.system_warning, userFixed,
                    buttonClicked)
                return
            } else if (showDefaultDenyDialog && !hasConfirmedRevoke) {
                fragment.showDefaultDenyDialog(changeTarget, R.string.old_sdk_deny_warning,
                    userFixed, buttonClicked)
                return
            } else {
                var newGroup = group
                val oldGroup = group
                if (shouldChangeForeground &&
                    (wasForegroundGranted || userFixed != group.foreground.isUserFixed)) {
                    newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, newGroup,
                        userFixed)

                    // only log if we have actually denied permissions, not if we switch from
                    // "ask every time" to denied
                    if (wasForegroundGranted) {
                        SafetyNetLogger.logPermissionToggled(newGroup)
                    }
                }
                if (shouldChangeBackground && group.hasBackgroundGroup &&
                    (wasBackgroundGranted || userFixed != group.background.isUserFixed)) {
                    newGroup = KotlinUtils.revokeBackgroundRuntimePermissions(app,
                        newGroup, userFixed)

                    // only log if we have actually denied permissions, not if we switch from
                    // "ask every time" to denied
                    if (wasBackgroundGranted) {
                        SafetyNetLogger.logPermissionToggled(newGroup, true)
                    }
                }
                logPermissionChanges(oldGroup, newGroup, buttonClicked)
            }
        }
    }

    /**
     * Once the user has confirmed that he/she wants to revoke a permission that was granted by
     * default, actually revoke the permissions.
     *
     * @param changeTarget whether to change foreground, background, or both.
     * @param userFixed whether the user has stated they do not wish to be prompted about the
     * permission any more.
     * @param buttonPressed button pressed to initiate the change, one of
     *                      AppPermissionFragmentActionReported.button_pressed constants
     *
     */
    fun onDenyAnyWay(changeTarget: ChangeTarget, userFixed: Boolean, buttonPressed: Int) {
        val group = lightAppPermGroup ?: return
        val wasForegroundGranted = group.foreground.isGranted
        val wasBackgroundGranted = group.background.isGranted
        var hasDefaultPermissions = false

        var newGroup = group
        val oldGroup = group
        if (changeTarget andValue ChangeTarget.CHANGE_FOREGROUND != 0) {
            newGroup = KotlinUtils.revokeForegroundRuntimePermissions(app, newGroup, userFixed)
            if (wasForegroundGranted) {
                SafetyNetLogger.logPermissionToggled(newGroup)
            }
            hasDefaultPermissions = group.foreground.isGrantedByDefault
        }
        if (changeTarget andValue ChangeTarget.CHANGE_BACKGROUND != 0 && group.hasBackgroundGroup) {
            newGroup = KotlinUtils.revokeBackgroundRuntimePermissions(app, newGroup, userFixed)

            if (wasBackgroundGranted) {
                SafetyNetLogger.logPermissionToggled(newGroup)
            }
            hasDefaultPermissions = hasDefaultPermissions ||
                group.background.isGrantedByDefault
        }
        logPermissionChanges(oldGroup, newGroup, buttonPressed)

        if (hasDefaultPermissions || !group.supportsRuntimePerms) {
            hasConfirmedRevoke = true
        }
    }

    /**
     * Show the All App Permissions screen with the proper filter group, package name, and user.
     *
     * @param fragment The current fragment we wish to transition from
     */
    fun showAllPermissions(fragment: AppPermissionFragment) {
        val args = AllAppPermissionsFragment.createArgs(packageName, permGroupName, user)
        fragment.findNavController().navigate(R.id.app_to_all_perms, args)
    }

    private fun getIndividualPermissionDetailResId(group: LightAppPermGroup): Pair<Int, Int> {
        return when (val numRevoked =
            group.permissions.filter { !it.value.isGrantedIncludingAppOp }.size) {
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
        val isForegroundPolicyDenied = group.foreground.isPolicyFixed && !group.foreground.isGranted
        val isPolicyFullyFixedWithGrantedOrNoBkg = group.isPolicyFullyFixed &&
                (group.background.isGranted || !group.hasBackgroundGroup)
        if (group.foreground.isSystemFixed || group.background.isSystemFixed) {
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
            if (group.background.isPolicyFixed && group.background.isGranted) {
                return R.string.permission_summary_enabled_by_admin_background_only
            } else if (group.background.isPolicyFixed) {
                return R.string.permission_summary_disabled_by_admin_background_only
            } else if (group.foreground.isPolicyFixed) {
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
            if (group.background.isPolicyFixed && group.background.isGranted) {
                return R.string.permission_summary_enabled_by_policy_background_only
            } else if (group.background.isPolicyFixed) {
                return R.string.permission_summary_disabled_by_policy_background_only
            } else if (group.foreground.isPolicyFixed) {
                return R.string.permission_summary_enabled_by_policy_foreground_only
            }
        }
        return 0
    }

    private fun logPermissionChanges(
        oldGroup: LightAppPermGroup,
        newGroup: LightAppPermGroup,
        buttonPressed: Int
    ) {
        val changeId = Random().nextLong()

        for ((permName, permission) in oldGroup.permissions) {
            val newPermission = newGroup.permissions[permName] ?: continue

            if (permission.isGrantedIncludingAppOp != newPermission.isGrantedIncludingAppOp ||
                permission.flags != newPermission.flags) {
                logAppPermissionFragmentActionReported(changeId, newPermission, buttonPressed)
            }
        }
    }

    private fun logAppPermissionFragmentActionReported(
        changeId: Long,
        permission: LightPermission,
        buttonPressed: Int
    ) {
        val uid = KotlinUtils.getPackageUid(app, packageName, user) ?: return
        PermissionControllerStatsLog.write(APP_PERMISSION_FRAGMENT_ACTION_REPORTED, sessionId,
            changeId, uid, packageName, permission.permInfo.name,
            permission.isGrantedIncludingAppOp, permission.flags, buttonPressed)
        Log.v(LOG_TAG, "Permission changed via UI with sessionId=$sessionId changeId=" +
            "$changeId uid=$uid packageName=$packageName permission=" + permission.permInfo.name +
            " isGranted=" + permission.isGrantedIncludingAppOp + " permissionFlags=" +
            permission.flags + " buttonPressed=$buttonPressed")
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
 * @param app The current application
 * @param packageName The name of the package this ViewModel represents
 * @param permGroupName The name of the permission group this ViewModel represents
 * @param user The user of the package
 * @param sessionId A session ID used in logs to identify this particular session
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