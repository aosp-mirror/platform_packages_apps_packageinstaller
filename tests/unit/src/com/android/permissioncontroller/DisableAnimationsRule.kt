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

import com.android.compatibility.common.util.SystemUtil.runShellCommand
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule to disable the animations during a test
 */
class DisableAnimationsRule : TestRule {
    /** transition_animation_scale setting */
    private val transitionAnimationScale = GlobalSetting("transition_animation_scale")

    /** window_animation_scale setting */
    private val windowAnimationScale = GlobalSetting("window_animation_scale")

    /** animator_duration_scale setting */
    private val animatorDurationScale = GlobalSetting("animator_duration_scale")

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                transitionAnimationScale.zero()
                windowAnimationScale.zero()
                animatorDurationScale.zero()
                try {
                    base.evaluate()
                } finally {
                    transitionAnimationScale.restore()
                    windowAnimationScale.restore()
                    animatorDurationScale.restore()
                }
            }
        }
    }

    /**
     * Class representing a single global setting
     */
    private class GlobalSetting(val name: String) {
        private val initialValue = runShellCommand("settings get global $name")

        /**
         * Set setting to 0
         */
        fun zero() {
            runShellCommand("settings put global $name 0")
        }

        /**
         * Restore original state of setting
         */
        fun restore() {
            runShellCommand("settings put global $name $initialValue")
        }
    }
}