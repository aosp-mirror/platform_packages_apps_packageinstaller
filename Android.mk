LOCAL_PATH:= $(call my-dir)

# In order to build the apk and tests for both AOSP and other builds
# via inherit-package, the makefile for PermissionController itself must
# not include the subdir makefiles, so it is split into its own makefile.
include $(LOCAL_PATH)/PermissionController.mk $(call all-makefiles-under,$(LOCAL_PATH))
