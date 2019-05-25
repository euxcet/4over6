//
// Created by liu-yt16 on 2019/5/17.
//

#ifndef CLIENT1_ALL_H
#define CLIENT1_ALL_H
#include <unistd.h>
#include <stdint.h>
#include <stdio.h>
#include <errno.h>
#include <android/log.h>

#define TAG "backend"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__);
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__);
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__);
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__);
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__);

#define ERROR_CHECK(code, exit_label) \
{ \
    int __ret = (code); \
    if (__ret < 0) { \
        LOGE("error at %s:%d error=%s", __FILE__, __LINE__, strerror(errno)); \
        goto exit_label; \
    } \
}
#define ASSERT(cond, exit_label) \
{ \
    if (!(cond)) { \
        LOGE("error at %s:%d [%s]", __FILE__, __LINE__, #cond);\
        goto exit_label; \
    } \
}
//

#endif //CLIENT1_ALL_H
