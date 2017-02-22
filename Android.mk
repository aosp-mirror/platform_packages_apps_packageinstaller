LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src)

LOCAL_STATIC_ANDROID_LIBRARIES += \
    android-support-v4 \
    android-support-annotations \
    android-support-v7-recyclerview \
    android-support-v7-preference \
    android-support-v7-appcompat \
    android-support-v14-preference \
    android-support-v17-preference-leanback \
    android-support-v17-leanback \
    SettingsLib

LOCAL_STATIC_JAVA_LIBRARIES := \
    xz-java

LOCAL_PACKAGE_NAME := PackageInstaller
LOCAL_CERTIFICATE := platform

LOCAL_PRIVILEGED_MODULE := true

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

# Comment for now unitl all private API dependencies are removed
# LOCAL_SDK_VERSION := system_current

include $(BUILD_PACKAGE)

ifeq (PackageInstaller,$(LOCAL_PACKAGE_NAME))
# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
endif
