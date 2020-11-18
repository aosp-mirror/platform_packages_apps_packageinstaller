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

package com.android.permissioncontroller.permission.ui.model

import android.Manifest.permission.UPDATE_APP_OPS_STATS
import android.Manifest.permission_group.CAMERA
import android.Manifest.permission_group.LOCATION
import android.Manifest.permission_group.MICROPHONE
import android.Manifest.permission_group.PHONE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.data.AppPermGroupUiInfoLiveData
import com.android.permissioncontroller.permission.data.AttributionLabelLiveData
import com.android.permissioncontroller.permission.data.LoadAndFreezeLifeData
import com.android.permissioncontroller.permission.data.OpAccess
import com.android.permissioncontroller.permission.data.OpUsageLiveData
import com.android.permissioncontroller.permission.data.PermGroupUsageLiveData
import com.android.permissioncontroller.permission.data.SmartAsyncMediatorLiveData
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.permissioncontroller.permission.data.get
import com.android.permissioncontroller.permission.data.micMutedLiveData
import com.android.permissioncontroller.permission.debug.shouldShowPermissionsDashboard
import com.android.permissioncontroller.permission.ui.handheld.ReviewOngoingUsageFragment.PHONE_CALL
import com.android.permissioncontroller.permission.ui.handheld.ReviewOngoingUsageFragment.VIDEO_CALL
import com.android.permissioncontroller.permission.utils.Utils
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.max

private const val FIRST_OPENED_KEY = "FIRST_OPENED"
private const val CALL_OP_USAGE_KEY = "CALL_OP_USAGE"
private const val USAGES_KEY = "USAGES_KEY"
private const val MIC_MUTED_KEY = "MIC_MUTED_KEY"

/**
 * ViewModel for {@link ReviewOngoingUsageFragment}
 */
class ReviewOngoingUsageViewModel(
    state: SavedStateHandle,
    extraDurationMills: Long
) : ViewModel() {
    /** Time of oldest usages considered */
    private val startTime = max(state.get<Long>(FIRST_OPENED_KEY)!! - extraDurationMills,
            Instant.EPOCH.toEpochMilli())

    data class Usages(
        /** attribution-res-id/packageName/user -> perm groups accessed */
        val appUsages: Map<PackageAttribution, Set<String>>,
        /** Op-names of phone call accesses */
        val callUsages: Collection<String>,
        /** Perm groups accessed by system */
        val systemUsages: Set<String>,
        /** A map of attribut, packageName and user -> attribution label to show with microphone*/
        val shownAttributionTags: Map<PackageAttribution, CharSequence> = emptyMap()
    )

    data class PackageAttribution(
        val attributionTag: String?,
        val packageName: String,
        val user: UserHandle
    )

    /**
     * Base permission usage that will filtered by SystemPermGroupUsages and
     * UserSensitivePermGroupUsages.
     *
     * <p>Note: This does not use a cached live-data to avoid getting stale data
     */
    private val permGroupUsages = LoadAndFreezeLifeData(state, USAGES_KEY,
            PermGroupUsageLiveData(PermissionControllerApplication.get(),
                    if (shouldShowPermissionsDashboard()) {
                        listOf(CAMERA, LOCATION, MICROPHONE)
                    } else {
                        listOf(CAMERA, MICROPHONE)
                    }, System.currentTimeMillis() - startTime))

    /**
     * Whether the mic is muted
     */
    private val isMicMuted = LoadAndFreezeLifeData(state, MIC_MUTED_KEY, micMutedLiveData)

    /** App runtime permission usages */
    private val appUsagesLiveData = object : SmartUpdateMediatorLiveData<Map<PackageAttribution,
        Set<String>>>() {
        /** (packageName, user, permissionGroupName) -> uiInfo */
        private var permGroupUiInfos = mutableMapOf<Triple<String, String, UserHandle>,
            AppPermGroupUiInfoLiveData>()

        init {
            addSource(permGroupUsages) {
                update()
            }

            addSource(isMicMuted) {
                update()
            }
        }

        override fun onUpdate() {
            if (!permGroupUsages.isInitialized || !isMicMuted.isInitialized) {
                return
            }

            if (permGroupUsages.value == null) {
                value = null
                return
            }

            // Update set of permGroupUiInfos if needed
            val requiredUiInfos = permGroupUsages.value!!.flatMap {
                (permissionGroupName, accesses) ->
                accesses.map { access ->
                    Triple(access.packageName, permissionGroupName, access.user)
                }
            }

            val getLiveDataFun = { key: Triple<String, String, UserHandle> ->
                AppPermGroupUiInfoLiveData[key.first, key.second, key.third] }
            setSourcesToDifference(requiredUiInfos, permGroupUiInfos, getLiveDataFun) {
                GlobalScope.launch(Main.immediate) { update() }
            }

            // Update set of attributionLabels needed
            val requiredLabels = permGroupUsages.value!!.flatMap {
                (_, accesses) ->
                accesses.map { access ->
                    Triple(access.attributionTag, access.packageName, access.user)
                }
            }.distinct()

            if (permGroupUiInfos.values.any { !it.isInitialized }) {
                return
            }

            // Filter out system (== non user sensitive) apps
            val filteredUsages = mutableMapOf<PackageAttribution, MutableSet<String>>()
            for ((permGroupName, usages) in permGroupUsages.value!!) {
                for (usage in usages) {
                    if (permGroupUiInfos[Triple(usage.packageName, permGroupName, usage.user)]!!
                            .value?.isSystem == false) {
                        if (permGroupName == MICROPHONE && isMicMuted.value == true) {
                            continue
                        }

                        filteredUsages.getOrPut(PackageAttribution(
                            usage.attributionTag,
                            usage.packageName,
                            usage.user), { mutableSetOf() }).add(permGroupName)
                    }
                }
            }

            value = filteredUsages
        }
    }

    /**
     * Gets all trusted proxied voice IME and voice recognition microphone uses, and get the
     * label needed to display with it.
     */
    private val trustedAttrsLiveData = object : SmartAsyncMediatorLiveData<Map<PackageAttribution,
        CharSequence>>() {
        private val VOICE_IME_SUBTYPE = "voice"

        private val attributionLabelLiveDatas =
            mutableMapOf<Triple<String?, String, UserHandle>, AttributionLabelLiveData>()
        init {
            addSource(permGroupUsages) {
                updateAsync()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (!permGroupUsages.isInitialized) {
                return
            }
            val usages = permGroupUsages.value?.get(MICROPHONE) ?: run {
                postValue(emptyMap())
                return
            }
            val proxies = usages.mapNotNull { it.proxyAccess }

            val proxyLabelLiveDatas = proxies.map {
                Triple(it.attributionTag, it.packageName, it.user) }
            val toAddLabelLiveDatas = (usages.map { Triple(it.attributionTag, it.packageName,
                it.user) } + proxyLabelLiveDatas).distinct()
            val getLiveDataFun = { key: Triple<String?, String, UserHandle> ->
                AttributionLabelLiveData[key] }
            setSourcesToDifference(toAddLabelLiveDatas, attributionLabelLiveDatas, getLiveDataFun)

            if (attributionLabelLiveDatas.any { !it.value.isInitialized }) {
                return
            }

            val approvedAttrs = mutableMapOf<PackageAttribution, String>()
            for (user in usages.map { it.user }.distinct()) {
                val userContext = Utils.getUserContext(PermissionControllerApplication.get(), user)

                // TODO ntmyren: Observe changes, possibly split into separate LiveDatas
                val voiceInputs = mutableMapOf<String, CharSequence>()
                userContext.getSystemService(InputMethodManager::class.java)!!
                    .enabledInputMethodList.forEach {
                        for (i in 0 until it.subtypeCount) {
                            if (it.getSubtypeAt(i).mode == VOICE_IME_SUBTYPE) {
                                voiceInputs[it.packageName] =
                                    it.serviceInfo.loadSafeLabel(userContext.packageManager,
                                        Float.MAX_VALUE, 0)
                                break
                            }
                        }
                    }

                // Get the currently selected recognizer from the secure setting.
                val recognitionPackageName = Settings.Secure.getString(userContext.contentResolver,
                    // Settings.Secure.VOICE_RECOGNITION_SERVICE
                    "voice_recognition_service")
                    ?.let(ComponentName::unflattenFromString)?.packageName

                val recognizers = mutableMapOf<String, CharSequence>()
                val availableRecognizers = userContext.packageManager.queryIntentServices(
                    Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.GET_META_DATA)
                availableRecognizers.forEach {
                    val sI = it.serviceInfo
                    if (sI.packageName == recognitionPackageName) {
                        recognizers[sI.packageName] = sI.loadSafeLabel(userContext.packageManager,
                        Float.MAX_VALUE, 0)
                    }
                }

                val recognizerIntents = mutableMapOf<String, CharSequence>()
                val availableRecognizerIntents = userContext.packageManager.queryIntentActivities(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), PackageManager.GET_META_DATA)
                availableRecognizers.forEach { rI ->
                    val servicePkg = rI.serviceInfo.packageName
                    if (userContext.packageManager.checkPermission(UPDATE_APP_OPS_STATS, servicePkg)
                        == PERMISSION_GRANTED && availableRecognizerIntents.any {
                            it.activityInfo.packageName == servicePkg }) {
                        // If this recognizer intent is also a recognizer service, and is trusted,
                        // Then attribute to voice recognition
                        recognizerIntents[servicePkg] =
                            rI.serviceInfo.loadSafeLabel(userContext.packageManager,
                                Float.MAX_VALUE, 0)
                    }
                }

                // get attribution labels for voice IME, recognition intents, and recognition
                // services
                for (opAccess in usages) {
                    setTrustedAttrsForAccess(userContext, opAccess, user, false, voiceInputs,
                        approvedAttrs)
                    setTrustedAttrsForAccess(userContext, opAccess, user, false, recognizerIntents,
                        approvedAttrs)
                    setTrustedAttrsForAccess(userContext, opAccess, user, true, recognizers,
                        approvedAttrs)
                }
            }
            postValue(approvedAttrs)
        }

        private fun setTrustedAttrsForAccess(
            context: Context,
            opAccess: OpAccess,
            currUser: UserHandle,
            getProxyLabel: Boolean,
            trustedMap: Map<String, CharSequence>,
            toSetMap: MutableMap<PackageAttribution, String>
        ) {
            val access = if (getProxyLabel) {
                opAccess.proxyAccess
            } else {
                opAccess
            }

            if (access == null || access.user != currUser || access.packageName !in trustedMap) {
                return
            }

            // Always use the original op access for the PackageAttribution object
            val appAttr = PackageAttribution(opAccess.attributionTag, opAccess.packageName,
                opAccess.user)
            val packageName = access.packageName

            val labelResId = attributionLabelLiveDatas[Triple(access.attributionTag,
                access.packageName, access.user)]?.value ?: 0
            val label = try {
                context.createPackageContext(packageName, 0)
                    .getString(labelResId)
            } catch (e: Exception) {
                return
            }
            if (trustedMap[packageName] == label) {
                toSetMap[appAttr] = label
            }
        }
    }

    /** System runtime permission usages */
    private val systemUsagesLiveData = object : SmartAsyncMediatorLiveData<Set<String>>() {
        private val app = PermissionControllerApplication.get()

        init {
            addSource(permGroupUsages) {
                update()
            }

            addSource(isMicMuted) {
                update()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (job.isCancelled) {
                return
            }

            if (!permGroupUsages.isInitialized || !isMicMuted.isInitialized) {
                return
            }

            if (permGroupUsages.value == null) {
                value = null
                return
            }

            val filteredUsages = mutableSetOf<String>()
            for ((permGroupName, usages) in permGroupUsages.value!!) {
                for (usage in usages) {
                    if (app.getSystemService(LocationManager::class.java)!!
                            .isProviderPackage(usage.packageName) &&
                        (permGroupName == CAMERA ||
                            (permGroupName == MICROPHONE &&
                                isMicMuted.value == false))) {
                        filteredUsages.add(permGroupName)
                    }
                }
            }

            postValue(filteredUsages)
        }
    }

    /** Phone call usages */
    private val callOpUsageLiveData =
        object : SmartUpdateMediatorLiveData<Collection<String>>() {
            private val rawOps = LoadAndFreezeLifeData(state, CALL_OP_USAGE_KEY,
                OpUsageLiveData[listOf(PHONE_CALL, VIDEO_CALL),
                    System.currentTimeMillis() - startTime])

            init {
                addSource(rawOps) {
                    update()
                }

                addSource(isMicMuted) {
                    update()
                }
            }

            override fun onUpdate() {
                if (!isMicMuted.isInitialized || !rawOps.isInitialized) {
                    return
                }

                value = if (isMicMuted.value == true) {
                    rawOps.value!!.keys.filter { it != PHONE_CALL }
                } else {
                    rawOps.value!!.keys
                }
            }
        }

    /** App, system, and call usages in a single, nice, handy package */
    val usages = object : SmartAsyncMediatorLiveData<Usages>() {
        private val app = PermissionControllerApplication.get()

        init {
            addSource(appUsagesLiveData) {
                update()
            }

            addSource(systemUsagesLiveData) {
                update()
            }

            addSource(callOpUsageLiveData) {
                update()
            }

            addSource(trustedAttrsLiveData) {
                update()
            }
        }

        override suspend fun loadDataAndPostValue(job: Job) {
            if (job.isCancelled) {
                return
            }

            if (!callOpUsageLiveData.isInitialized || !appUsagesLiveData.isInitialized ||
                    !systemUsagesLiveData.isInitialized || !trustedAttrsLiveData.isInitialized) {
                return
            }

            val callOpUsages = callOpUsageLiveData.value?.toMutableSet()
            val appUsages = appUsagesLiveData.value?.toMutableMap()
            val systemUsages = systemUsagesLiveData.value
            val approvedAttrs = trustedAttrsLiveData.value ?: emptyMap()

            if (callOpUsages == null || appUsages == null || systemUsages == null) {
                postValue(null)
                return
            }

            // If there is nothing to show the dialog should be closed, hence return a "invalid"
            // value
            if (appUsages.isEmpty() && callOpUsages.isEmpty() && systemUsages.isEmpty()) {
                postValue(null)
                return
            }

            // If we are in a VOIP call (aka MODE_IN_COMMUNICATION), and have a carrier privileged
            // app using the mic, hide phone usage.
            val audioManager = app.getSystemService(AudioManager::class.java)!!
            if (callOpUsages.isNotEmpty() && audioManager.mode == MODE_IN_COMMUNICATION) {
                val telephonyManager = app.getSystemService(TelephonyManager::class.java)!!
                for ((pkg, usages) in appUsages) {
                    if (telephonyManager.checkCarrierPrivilegesForPackage(pkg.packageName) ==
                        CARRIER_PRIVILEGE_STATUS_HAS_ACCESS && usages.contains(MICROPHONE)) {
                        appUsages[pkg] = usages.toMutableSet().apply {
                            // TODO ntmyren: Replace this with real behavior
                            remove(MICROPHONE)
                            add(PHONE)
                        }

                        callOpUsages.clear()
                        continue
                    }
                }
            }

            postValue(Usages(appUsages, callOpUsages, systemUsages, approvedAttrs))
        }
    }
}

/**
 * Factory for a ReviewOngoingUsageViewModel
 *
 * @param extraDurationMillis The number of milliseconds old usages are considered for
 * @param owner The owner of this saved state
 * @param defaultArgs The default args to pass
 */
class ReviewOngoingUsageViewModelFactory(
    private val extraDurationMillis: Long,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel?> create(p0: String, p1: Class<T>, state: SavedStateHandle): T {
        state.set(FIRST_OPENED_KEY, state.get<Long>(FIRST_OPENED_KEY)
                ?: System.currentTimeMillis())
        @Suppress("UNCHECKED_CAST")
        return ReviewOngoingUsageViewModel(state, extraDurationMillis) as T
    }
}
