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

import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer

/**
 * A MediatorLiveData which tracks how long it has been inactive, compares new values before setting
 * its value (avoiding unnecessary updates), and can calculate the set difference between a list
 * and a map (used when determining whether or not to add a LiveData as a source).
 */
abstract class SmartUpdateMediatorLiveData<T> : MediatorLiveData<T>(),
    DataRepository.InactiveTimekeeper {

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

    override fun setValue(newValue: T?) {
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

    abstract fun update()

    override var timeWentInactive: Long? = null

    /**
     * Some LiveDatas have types, like Drawables which do not have a non-default equals method.
     * Those classes can override this method to change when the value is set upon calling setValue.
     *
     * @param valOne: The first T to be compared
     * @param valTwo: The second T to be compared
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
        observe(owner, observer)
    }

    override fun <S : Any?> addSource(source: LiveData<S>, onChanged: Observer<in S>) {
        if (source is SmartUpdateMediatorLiveData) {
            source.addChild(this, onChanged, staleObservers.isNotEmpty() ||
            children.any { it.third })
            sources.add(source)
        }
        super.addSource(source, onChanged)
    }

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
        onChanged: Observer< in T>,
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

        // if all sources are not stale, and we just requested stale updates, update ourselves
        if (sources.all { !it.isStale } && newHasStale) {
            update()
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
}