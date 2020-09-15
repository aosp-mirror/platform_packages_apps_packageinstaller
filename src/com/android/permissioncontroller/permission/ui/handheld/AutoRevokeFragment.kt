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

package com.android.permissioncontroller.permission.ui.handheld

import android.Manifest.permission_group
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.permissioncontroller.Constants.EXTRA_SESSION_ID
import com.android.permissioncontroller.Constants.INVALID_SESSION_ID
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.model.AutoRevokeViewModel
import com.android.permissioncontroller.permission.ui.model.AutoRevokeViewModel.Months
import com.android.permissioncontroller.permission.ui.model.AutoRevokeViewModel.RevokedPackageInfo
import com.android.permissioncontroller.permission.ui.model.AutoRevokeViewModelFactory
import com.android.permissioncontroller.permission.utils.IPC
import com.android.permissioncontroller.permission.utils.KotlinUtils
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * A fragment displaying all applications that have been auto-revoked, as well as the option to
 * remove them, and to open them.
 */
class AutoRevokeFragment : PermissionsFrameFragment() {

    private lateinit var viewModel: AutoRevokeViewModel
    private lateinit var collator: Collator
    private var sessionId: Long = 0L
    private var isFirstLoad = false

    companion object {
        private const val SHOW_LOAD_DELAY_MS = 200L
        private const val INFO_MSG_KEY = "info_msg"
        private const val ELEVATION_HIGH = 8f
        private val LOG_TAG = AutoRevokeFragment::class.java.simpleName

        @JvmStatic
        fun newInstance(): AutoRevokeFragment {
            return AutoRevokeFragment()
        }

        /**
         * Create the args needed for this fragment
         *
         * @param sessionId The current session Id
         *
         * @return A bundle containing the session Id
         */
        @JvmStatic
        fun createArgs(sessionId: Long): Bundle {
            val bundle = Bundle()
            bundle.putLong(EXTRA_SESSION_ID, sessionId)
            return bundle
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mUseShadowController = false
        super.onCreate(savedInstanceState)
        isFirstLoad = true

        collator = Collator.getInstance(
            context!!.getResources().getConfiguration().getLocales().get(0))
        sessionId = arguments!!.getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID)
        val factory = AutoRevokeViewModelFactory(activity!!.application, sessionId)
        viewModel = ViewModelProvider(this, factory).get(AutoRevokeViewModel::class.java)
        viewModel.autoRevokedPackageCategoriesLiveData.observe(this, Observer {
            it?.let { pkgs ->
                updatePackages(pkgs)
                setLoading(false, true)
            }
        })

        setHasOptionsMenu(true)
        activity?.getActionBar()?.setDisplayHomeAsUpEnabled(true)

        if (!viewModel.areAutoRevokedPackagesLoaded()) {
            GlobalScope.launch(IPC) {
                delay(SHOW_LOAD_DELAY_MS)
                if (!viewModel.areAutoRevokedPackagesLoaded()) {
                    GlobalScope.launch(Main) {
                        setLoading(true, true)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val ab = activity?.actionBar
        if (ab != null) {
            ab!!.setElevation(ELEVATION_HIGH)
        }
        activity!!.title = getString(R.string.permission_removed_page_title)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            this.pressBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updatePackages(categorizedPackages: Map<Months, List<RevokedPackageInfo>>) {
        if (preferenceScreen == null) {
            addPreferencesFromResource(R.xml.unused_app_categories)
            val infoPref = preferenceScreen?.findPreference<FooterPreference>(INFO_MSG_KEY)
            infoPref?.secondSummary = getString(R.string.auto_revoke_open_app_message)
        }

        val removedPrefs = mutableMapOf<String, AutoRevokePermissionPreference>()
        for (month in Months.allMonths()) {
            val category = findPreference<PreferenceCategory>(month.value)!!
            for (i in 0 until category.preferenceCount) {
                val pref = category.getPreference(i) as AutoRevokePermissionPreference
                val contains = categorizedPackages[Months.THREE]?.any { (pkgName, user, _) ->
                    val key = createKey(pkgName, user)
                    pref.key == key
                }
                if (contains != true) {
                    removedPrefs[pref.key] = pref
                }
            }

            for ((_, pref) in removedPrefs) {
                category.removePreference(pref)
            }
        }

        for ((month, packages) in categorizedPackages) {
            val category = findPreference<PreferenceCategory>(month.value)!!
            category.title = if (month == Months.THREE) {
                getString(R.string.last_opened_category_title, "3")
            } else {
                getString(R.string.last_opened_category_title, "6")
            }
            category.isVisible = packages.isNotEmpty()

            for ((pkgName, user, shouldDisable, permSet) in packages) {
                val revokedPerms = permSet.toList()
                val key = createKey(pkgName, user)

                var pref = category.findPreference<AutoRevokePermissionPreference>(key)
                if (pref == null) {
                    pref = removedPrefs[key] ?: AutoRevokePermissionPreference(
                        activity!!.application, pkgName, user, preferenceManager.context!!)
                    pref.key = key
                    pref.title = KotlinUtils.getPackageLabel(activity!!.application, pkgName, user)
                }

                if (shouldDisable) {
                    pref.removeClickListener = View.OnClickListener {
                        createDisableDialog(pkgName, user)
                    }
                } else {
                    pref.removeClickListener = View.OnClickListener {
                        viewModel.requestUninstallApp(this, pkgName, user)
                    }
                }

                pref.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
                    viewModel.navigateToAppInfo(pkgName, user, sessionId)
                    true
                }

                val mostImportant = getMostImportantGroup(revokedPerms)
                val importantLabel = KotlinUtils.getPermGroupLabel(context!!, mostImportant)
                pref.summary = when {
                    revokedPerms.size == 1 -> getString(R.string.auto_revoked_app_summary_one,
                        importantLabel)
                    revokedPerms.size == 2 -> {
                        val otherLabel = if (revokedPerms[0] == mostImportant) {
                            KotlinUtils.getPermGroupLabel(context!!, revokedPerms[1])
                        } else {
                            KotlinUtils.getPermGroupLabel(context!!, revokedPerms[0])
                        }
                        getString(R.string.auto_revoked_app_summary_two, importantLabel, otherLabel)
                    }
                    else -> getString(R.string.auto_revoked_app_summary_many, importantLabel,
                        "${revokedPerms.size - 1}")
                }
                category.addPreference(pref)
                KotlinUtils.sortPreferenceGroup(category, this::comparePreference, false)
            }
        }

        if (isFirstLoad) {
            if (categorizedPackages[Months.SIX]!!.isNotEmpty() ||
                    categorizedPackages[Months.THREE]!!.isNotEmpty()) {
                isFirstLoad = false
            }
            Log.i(LOG_TAG, "sessionId: $sessionId Showed Auto Revoke Page")
            for (month in Months.values()) {
                Log.i(LOG_TAG, "sessionId: $sessionId $month unused: " +
                    "${categorizedPackages[month]}")
                for (revokedPackageInfo in categorizedPackages[month]!!) {
                    for (groupName in revokedPackageInfo.revokedGroups) {
                        val isNewlyRevoked = month == Months.THREE
                        viewModel.logAppView(revokedPackageInfo.packageName,
                            revokedPackageInfo.user, groupName, isNewlyRevoked)
                    }
                }
            }
        }
    }

    private fun comparePreference(lhs: Preference, rhs: Preference): Int {
        var result = collator.compare(lhs.title.toString(),
            rhs.title.toString())
        if (result == 0) {
            result = lhs.key.compareTo(rhs.key)
        }
        return result
    }

    private fun createKey(packageName: String, user: UserHandle): String {
        return "$packageName:${user.identifier}"
    }

    private fun getMostImportantGroup(groupNames: List<String>): String {
        return when {
            groupNames.contains(permission_group.LOCATION) -> permission_group.LOCATION
            groupNames.contains(permission_group.MICROPHONE) -> permission_group.MICROPHONE
            groupNames.contains(permission_group.CAMERA) -> permission_group.CAMERA
            groupNames.contains(permission_group.CONTACTS) -> permission_group.CONTACTS
            groupNames.contains(permission_group.STORAGE) -> permission_group.STORAGE
            groupNames.contains(permission_group.CALENDAR) -> permission_group.CALENDAR
            groupNames.isNotEmpty() -> groupNames[0]
            else -> ""
        }
    }

    private fun createDisableDialog(packageName: String, user: UserHandle) {
        val dialog = DisableDialog()

        val args = Bundle()
        args.putString(Intent.EXTRA_PACKAGE_NAME, packageName)
        args.putParcelable(Intent.EXTRA_USER, user)
        dialog.arguments = args

        dialog.isCancelable = true

        dialog.show(childFragmentManager.beginTransaction(), DisableDialog::class.java.name)
    }

    class DisableDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val fragment = parentFragment as AutoRevokeFragment
            val packageName = arguments!!.getString(Intent.EXTRA_PACKAGE_NAME)!!
            val user = arguments!!.getParcelable<UserHandle>(Intent.EXTRA_USER)!!
            val b = AlertDialog.Builder(context!!)
                .setMessage(R.string.app_disable_dlg_text)
                .setPositiveButton(R.string.app_disable_dlg_positive) { _, _ ->
                    fragment.viewModel.disableApp(packageName, user)
                }
                .setNegativeButton(R.string.cancel, null)
            val d: Dialog = b.create()
            d.setCanceledOnTouchOutside(true)
            return d
        }
    }
}