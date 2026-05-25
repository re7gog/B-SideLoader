#define JM_XORSTR_DISABLE_AVX_INTRINSICS

#include <string>
#include <jni.h>
#include "xorstr.hpp"

extern "C" JNIEXPORT jint JNICALL
Java_org_drinkless_tdlib_Secrets_getApiId(JNIEnv* env, jobject thiz) {
    volatile int API_ID = ID_SECRET ^ MASK_SECRET;
    return API_ID ^ MASK_SECRET;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_drinkless_tdlib_Secrets_getApiHash(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(xorstr(HASH_SECRET).crypt_get());
}