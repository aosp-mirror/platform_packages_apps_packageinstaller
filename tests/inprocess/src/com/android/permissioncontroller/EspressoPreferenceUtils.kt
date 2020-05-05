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

package com.android.permissioncontroller

import android.view.View
import android.widget.TextView
import androidx.preference.R
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf

/**
 * Get a {@link ViewAction} that runs a command on a view.
 *
 * @param onViewAction action to run on the view
 */
fun <T : View> runOnView(onViewAction: (T) -> Unit): ViewAction {
    return object : ViewAction {
        override fun getDescription() = "run on view"

        override fun getConstraints() = Matchers.any(View::class.java)

        override fun perform(uiController: UiController, view: View) {
            onViewAction(view as T)
        }
    }
}

/**
 * Scroll until a preference is visible.
 *
 * @param title title of the preference
 */
fun scrollToPreference(title: CharSequence) {
    onView(withId(R.id.recycler_view))
            .perform(scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText(title.toString()))))
}

/**
 * Make sure a preference cannot be found.
 *
 * @param title title of the preference
 */
fun assertDoesNotHavePreference(title: CharSequence) {
    try {
        scrollToPreference(title)
    } catch (e: Exception) {
        return
    }

    throw AssertionError("View with title $title was found")
}

/**
 * Get summary of preference.
 *
 * @param title title of the preference
 *
 * @return summary of preference
 */
fun getPreferenceSummary(title: CharSequence): CharSequence {
    lateinit var summary: CharSequence

    onView(allOf(hasSibling(withText(title.toString())), withResourceName("summary")))
            .perform(runOnView<TextView> { summary = it.text })

    return summary
}
