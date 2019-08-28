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

package com.android.packageinstaller.permission.utils

/**
 * A set of util functions designed to work with kotlin, though they can work with java, as well.
 */
object KotlinUtils {

    /**
     * Given a Map, and a List, determines which elements are in the list, but not the map, and
     * vice versa. Used primarily for determining which liveDatas are already being watched, and
     * which need to be removed or added
     *
     * @param oldValues: A map of key type K, with any value type
     * @param newValues: A list of type K
     *
     * @return A pair, where the first value is all items in the list, but not the map, and the
     * second is all keys in the map, but not the list
     */
    fun <K> getMapAndListDifferences(
        newValues: List<K>,
        oldValues: Map<K, *>
    ): Pair<List<K>, List<K>> {
        val mapHas = oldValues.keys.toMutableList()
        val listHas = newValues.toMutableList()
        for (newVal in newValues) {
            if (oldValues.containsKey(newVal)) {
                mapHas.remove(newVal)
                listHas.remove(newVal)
            }
        }
        return listHas to mapHas
    }
}