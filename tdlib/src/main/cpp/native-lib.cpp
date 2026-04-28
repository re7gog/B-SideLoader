#include <jni.h>
#include <string>

extern "C" JNIEXPORT jint JNICALL
Java_org_drinkless_tdlib_Secrets_getApiId(JNIEnv* env, jobject thiz) {
    // YOUR APP ID
    return 123456;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_drinkless_tdlib_Secrets_getApiHash(JNIEnv* env, jobject thiz) {
    std::string hash = "YOUR_HASH";
    return env->NewStringUTF(hash.c_str());
}