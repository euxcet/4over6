#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_java_liuyingtian_ivi_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


extern "C" JNIEXPORT jint JNICALL
Java_com_java_liuyingtian_ivi_IVIService_backend_1thread(
        JNIEnv *env,
        jobject,
        jstring sererIp_,
        jstring serverPort,
        jstring localIp) {
    return 23;
}


extern "C" JNIEXPORT jint JNICALL
Java_com_java_liuyingtian_ivi_IVIService_get_1ipv4_1addr(
        JNIEnv *env,
        jobject,
        jstring sererIp_,
        jstring serverPort,
        jstring localIp) {
    return 23;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_java_liuyingtian_ivi_IVIService_set_1backend_1pipes(
        JNIEnv *env,
        jobject,
        jint backRead,
        jint backWrite){
    return 0;
}


