LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src)

LOCAL_STATIC_ANDROID_LIBRARIES += \
    iconloader \
    androidx.car_car \
    com.google.android.material_material \
    androidx.transition_transition \
    androidx.core_core \
    androidx.media_media \
    androidx.legacy_legacy-support-core-utils \
    androidx.legacy_legacy-support-core-ui \
    androidx.fragment_fragment \
    androidx.appcompat_appcompat \
    androidx.preference_preference \
    androidx.recyclerview_recyclerview \
    androidx.legacy_legacy-preference-v14 \
    androidx.leanback_leanback \
    androidx.leanback_leanback-preference \
    androidx.lifecycle_lifecycle-extensions \
    androidx.lifecycle_lifecycle-common-java8 \
    SettingsLibHelpUtils \
    SettingsLibRestrictedLockUtils \
    SettingsLibAppPreference \
    SettingsLibSearchWidget \
    SettingsLibSettingsSpinner \
    SettingsLibLayoutPreference \
    SettingsLibActionBarShadow \
    SettingsLibProgressBar

LOCAL_STATIC_JAVA_LIBRARIES := \
    androidx.annotation_annotation

LOCAL_PACKAGE_NAME := PermissionController
LOCAL_SDK_VERSION := system_current
LOCAL_MIN_SDK_VERSION := 28
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)
