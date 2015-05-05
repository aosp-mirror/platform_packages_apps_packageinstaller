LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    src/com/android/packageinstaller/EventLogTags.logtags

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4 \
    android-support-v17-leanback \
    android-support-v7-recyclerview

LOCAL_RESOURCE_DIR := \
    frameworks/support/v17/leanback/res \
    frameworks/support/v7/recyclerview/res \
    $(LOCAL_PATH)/res \

LOCAL_AAPT_FLAGS := --auto-add-overlay --extra-packages android.support.v17.leanback:android.support.v7.recyclerview

LOCAL_PACKAGE_NAME := PackageInstaller
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)
