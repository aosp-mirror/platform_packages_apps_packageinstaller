#############################################
# PackageInstaller Robolectric test target. #
#############################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Include the testing libraries (JUnit4 + Robolectric libs).
LOCAL_STATIC_JAVA_LIBRARIES := \
    platform-system-robolectric

LOCAL_JAVA_LIBRARIES := \
    junit \
    platform-robolectric-prebuilt

LOCAL_INSTRUMENTATION_FOR := PackageInstaller
LOCAL_MODULE := PackageInstallerRoboTests

LOCAL_MODULE_TAGS := optional

include $(BUILD_STATIC_JAVA_LIBRARY)

#############################################################
# PackageInstaller runner target to run the previous target. #
#############################################################
include $(CLEAR_VARS)

LOCAL_MODULE := RunPackageInstallerRoboTests

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    PackageInstallerRoboTests

LOCAL_TEST_PACKAGE := PackageInstaller

LOCAL_ROBOTEST_FAILURE_FATAL := true

include prebuilts/misc/common/robolectric/run_robotests.mk
