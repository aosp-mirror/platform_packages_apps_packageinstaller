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

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.permissioncontroller.R

/**
 * A Preference for the footer of a screen. Has two summaries, and adjusted spacing and icon
 * placement.
 */
class FooterPreference : Preference {
    constructor(c: Context): super(c)
    constructor(c: Context, a: AttributeSet): super(c, a)
    constructor(c: Context, a: AttributeSet, attr: Int): super(c, a, attr)
    constructor(c: Context, a: AttributeSet, attr: Int, res: Int): super(c, a, attr, res)

    init {
        layoutResource = R.layout.footer_preference
    }
    var secondSummary: CharSequence = ""
        set(value) {
            secondSummaryView?.text = value
            field = value
            notifyChanged()
        }

    private var secondSummaryView: TextView? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        secondSummaryView = holder?.findViewById(R.id.summary2) as TextView
        secondSummaryView?.text = secondSummary
        super.onBindViewHolder(holder)
    }
}