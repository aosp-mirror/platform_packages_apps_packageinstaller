# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := PermissionControllerTest
LOCAL_CERTIFICATE := platform

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := android.test.runner.stubs
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_STATIC_JAVA_LIBRARIES := \
    ctstestrunner \
    compatibility-device-util \
	androidx.annotation_annotation \
	androidx.test.runner \
	androidx.test.rules \
	androidx.test.uiautomator_uiautomator \
    androidx.legacy_legacy-support-v4 \
    platform-test-annotations

LOCAL_SDK_VERSION := test_current

LOCAL_COMPATIBILITY_SUITE := general-tests
LOCAL_INSTRUMENTATION_FOR := PermissionController

include $(BUILD_PACKAGE)
