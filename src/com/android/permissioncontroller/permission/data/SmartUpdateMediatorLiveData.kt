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

package com.android.permissioncontroller.permission.data

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.android.permissioncontroller.permission.utils.ensureMainThread
import com.android.permissioncontroller.permission.utils.getInitializedValue
import com.android.permissioncontroller.permission.utils.shortStackTrace

/**
 * A MediatorLiveData which tracks how long it has been inactive, compares new values before setting
 * its value (avoiding unnecessary updates), and can calculate the set difference between a list
 * and a map (used when determining whether or not to add a LiveData as a source).
 */
abstract class SmartUpdateMediatorLiveData<T> : MediatorLiveData<T>(),
    DataRepository.InactiveTimekeeper {

    companion object {
        const val DEBUG_UPDATES = false
        val LOG_TAG = SmartUpdateMediatorLiveData::class.java.simpleName
    }

    /**
     * Boolean, whether or not the value of this uiDataLiveData has been explicitly set yet.
     * Differentiates between "null value because liveData is new" and "null value because
     * liveData is invalid"
     */
    var isInitialized = false
        private set

    /**
     * Boolean, whether or not this liveData has a stale value or not. Every time the liveData goes
     * inactive, its data becomes stale, until it goes active again, and is explicitly set.
     */
    var isStale = true
        private set

    private val staleObservers = mutableListOf<Pair<LifecycleOwner, Observer<in T>>>()

    private val sources = mutableListOf<SmartUpdateMediatorLiveData<*>>()

    private val children =
        mutableListOf<Triple<SmartUpdateMediatorLiveData<*>, Observer<in T>, Boolean>>()

    @MainThread
    override fun setValue(newValue: T?) {
        ensureMainThread()

        if (!isInitialized) {
            isInitialized = true
            isStale = false
            // If we have received an invalid value, and this is the first time we are set,
            // notify observers.
            if (newValue == null) {
                super.setValue(newValue)
                return
            }
        }

        if (valueNotEqual(super.getValue(), newValue)) {
            isStale = false
            super.setValue(newValue)
        } else if (isStale) {
            isStale = false
            // We are no longer stale- notify active stale observers we are up-to-date
            val liveObservers = staleObservers.filter { it.first.lifecycle.currentState >= STARTED }
            for ((_, observer) in liveObservers) {
                observer.onChanged(newValue)
            }

            for ((liveData, observer, shouldUpdate) in children) {
                if (liveData.hasActiveObservers() && shouldUpdate) {
                    observer.onChanged(newValue)
                }
            }
        }
    }

    /**
     * Update the value of this LiveData.
     *
     * This usually results in an IPC when active and no action otherwise.
     */
    @MainThread
    fun updateIfActive() {
        if (DEBUG_UPDATES) {
            Log.i(LOG_TAG, "updateIfActive ${javaClass.simpleName} ${shortStackTrace()}")
        }
        onUpdate()
    }

    @MainThread
    protected abstract fun onUpdate()

    override var timeWentInactive: Long? = null

    /**
     * Some LiveDatas have types, like Drawables which do not have a non-default equals method.
     * Those classes can override this method to change when the value is set upon calling setValue.
     *
     * @param valOne The first T to be compared
     * @param valTwo The second T to be compared
     *
     * @return True if the two values are different, false otherwise
     */
    protected open fun valueNotEqual(valOne: T?, valTwo: T?): Boolean {
        return valOne != valTwo
    }

    fun observeStale(owner: LifecycleOwner, observer: Observer<in T>) {
        val oldStaleObserver = hasStaleObserver()
        staleObservers.add(owner to observer)
        notifySourcesOnStaleUpdates(oldStaleObserver, true)
        if (owner == ForeverActiveLifecycle) {
            observeForever(observer)
        } else {
            observe(owner, observer)
        }
    }

    @MainThread
    override fun <S : Any?> addSource(source: LiveData<S>, onChanged: Observer<in S>) {
        // ensureMainThread() //TODO (b/148458939): violated in PackagePermissionsLiveData
        if (source is SmartUpdateMediatorLiveData) {
            source.addChild(this, onChanged,
                staleObservers.isNotEmpty() || children.any { it.third })
            sources.add(source)
        }
        super.addSource(source, onChanged)
    }

    @MainThread
    override fun <S : Any?> removeSource(toRemote: LiveData<S>) {
        if (toRemote is SmartUpdateMediatorLiveData) {
            toRemote.removeChild(this)
            sources.remove(toRemote)
        }
        super.removeSource(toRemote)
    }

    private fun <S : Any?> removeChild(liveData: LiveData<S>) {
        children.removeIf { it.first == liveData }
    }

    private fun <S : Any?> addChild(
        liveData: SmartUpdateMediatorLiveData<S>,
        onChanged: Observer<in T>,
        sendStaleUpdates: Boolean
    ) {
        children.add(Triple(liveData, onChanged, sendStaleUpdates))
    }

    private fun <S : Any?> updateStaleChildNotify(
        liveData: SmartUpdateMediatorLiveData<S>,
        sendStaleUpdates: Boolean
    ) {
        for ((idx, childTriple) in children.withIndex()) {
            if (childTriple.first == liveData) {
                children[idx] = Triple(liveData, childTriple.second, sendStaleUpdates)
            }
        }
    }

    override fun removeObserver(observer: Observer<in T>) {
        val oldStaleObserver = hasStaleObserver()
        staleObservers.removeIf { it.second == observer }
        notifySourcesOnStaleUpdates(oldStaleObserver, hasStaleObserver())
        super.removeObserver(observer)
    }

    override fun removeObservers(owner: LifecycleOwner) {
        val oldStaleObserver = hasStaleObserver()
        staleObservers.removeIf { it.first == owner }
        notifySourcesOnStaleUpdates(oldStaleObserver, hasStaleObserver())
        super.removeObservers(owner)
    }

    private fun notifySourcesOnStaleUpdates(oldHasStale: Boolean, newHasStale: Boolean) {
        if (oldHasStale == newHasStale) {
            return
        }
        for (liveData in sources) {
            liveData.updateStaleChildNotify(this, hasStaleObserver())
        }

        // if all sources are not stale, and we just requested stale updates, and we are stale,
        // update our value
        if (sources.all { !it.isStale } && newHasStale && isStale) {
            updateIfActive()
        }
    }

    private fun hasStaleObserver(): Boolean {
        return staleObservers.isNotEmpty() || children.any { it.third }
    }

    override fun onActive() {
        timeWentInactive = null
        super.onActive()
    }

    override fun onInactive() {
        timeWentInactive = System.nanoTime()
        isStale = true
        super.onInactive()
    }

    /**
     * Get the [initialized][isInitialized] value, suspending until one is available
     *
     * @param staleOk whether [isStale] value is ok to return
     * @param forceUpdate whether to call [updateIfActive] (usually triggers an IPC)
     */
    suspend fun getInitializedValue(staleOk: Boolean = false, forceUpdate: Boolean = false): T {
        return getInitializedValue(
            observe = { observer ->
                observeStale(ForeverActiveLifecycle, observer)
                if (forceUpdate) {
                    updateIfActive()
                }
            },
            isInitialized = { isInitialized && (staleOk || !isStale) })
    }

    /**
     * A [Lifecycle]/[LifecycleOwner] that is permanently [State.STARTED]
     *
     * Passing this to [LiveData.observe] is essentially equivalent to using
     * [LiveData.observeForever], so you have to make sure you handle your own cleanup whenever
     * using this.
     */
    private object ForeverActiveLifecycle : Lifecycle(), LifecycleOwner {

        override fun getLifecycle(): Lifecycle = this

        override fun addObserver(observer: LifecycleObserver) {}

        override fun removeObserver(observer: LifecycleObserver) {}

        override fun getCurrentState(): State = State.STARTED
    }
}