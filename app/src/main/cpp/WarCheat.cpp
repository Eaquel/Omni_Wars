#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstdint>
#include <chrono>

#define TAG "WarCheat"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

static float gLastX=0, gLastZ=0;
static long  gLastTimeMs=0;
static float gMaxAllowedSpeed=12.0f;
static float gMaxAllowedDamage=200.0f;

static long nowMs() {
    return (long)std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

static uint32_t hashVal(float x, float z, long t) {
    uint32_t ix=*(uint32_t*)&x, iz=*(uint32_t*)&z;
    return ix ^ (iz<<7) ^ (uint32_t)t;
}

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_omni_wars_WarCheat_nativeValidateMove(
    JNIEnv*,jclass,jfloat x,jfloat z)
{
    long now=nowMs();
    if(gLastTimeMs>0) {
        float dt=(now-gLastTimeMs)/1000.0f;
        if(dt>0 && dt<2.0f) {
            float dx=x-gLastX, dz=z-gLastZ;
            float dist=sqrtf(dx*dx+dz*dz);
            float speed=dist/dt;
            if(speed>gMaxAllowedSpeed*1.5f) {
                LOGW("Speed hack: %.1f (max %.1f)", speed, gMaxAllowedSpeed);
                return JNI_FALSE;
            }
        }
    }
    gLastX=x; gLastZ=z; gLastTimeMs=now;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_omni_wars_WarCheat_nativeValidateDamage(
    JNIEnv*,jclass,jfloat damage)
{
    if(damage<0 || damage>gMaxAllowedDamage) {
        LOGW("Damage hack: %.1f (max %.1f)", damage, gMaxAllowedDamage);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarCheat_nativeSetMaxSpeed(JNIEnv*,jclass,jfloat s){
    gMaxAllowedSpeed=s;
}

JNIEXPORT jlong JNICALL Java_com_omni_wars_WarCheat_nativeGetToken(JNIEnv*,jclass,jfloat x,jfloat z){
    return (jlong)hashVal(x,z,nowMs()/1000);
}

JNIEXPORT jboolean JNICALL Java_com_omni_wars_WarCheat_nativeIsIntegrityOk(JNIEnv* env, jclass){
    return JNI_TRUE;
}

}
