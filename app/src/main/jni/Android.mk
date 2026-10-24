LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := miowser_adblock
LOCAL_SRC_FILES := domain_index.c
LOCAL_CFLAGS := -O3 -Wall -Wextra
include $(BUILD_SHARED_LIBRARY)
