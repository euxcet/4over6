LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := vpn-lib
LOCAL_SRC_FILES := com_java_liuyingtian_ivi_IVIService.c

include $(BUILD_SHARED_LIBRARY)
