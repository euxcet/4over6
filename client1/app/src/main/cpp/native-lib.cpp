#include <jni.h>
#include <string>
#include "main.h"
using namespace std;

extern "C"
JNIEXPORT jint JNICALL
Java_com_java_liuyingtian_ivi_BackendThread_backend_1thread(JNIEnv *env, jobject instance, jstring server_ip,
                                              jint server_port, jint back_read_fd, jint back_write_fd) {
    const char *__server_ip = env->GetStringUTFChars(server_ip, 0);
    int ret = backend_main(__server_ip, server_port, back_read_fd, back_write_fd);
    env->ReleaseStringUTFChars(server_ip, __server_ip);
    return ret;
}
