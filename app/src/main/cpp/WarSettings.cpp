#include <jni.h>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <chrono>
#include <numeric>
#include <array>

#define TAG "WarSettings"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static int   gQualityLevel=2;
static float gMasterVolume=1.0f;
static float gSfxVolume=1.0f;
static bool  gVibration=true;

static std::array<float,30> gFrameTimes{};
static int gFTIdx=0;
static long gLastFrameNs=0;

static AAudioStream* gAudioStream=nullptr;

static long nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

extern "C" {

JNIEXPORT void JNICALL Java_com_omni_wars_WarSettings_nativeSetQuality(JNIEnv*,jclass,jint level){
    gQualityLevel=level;
    LOGI("Quality set to %d", level);
}

JNIEXPORT jint JNICALL Java_com_omni_wars_WarSettings_nativeGetQuality(JNIEnv*,jclass){
    return gQualityLevel;
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarSettings_nativeSetVolume(JNIEnv*,jclass,jfloat master,jfloat sfx){
    gMasterVolume=master; gSfxVolume=sfx;
}

JNIEXPORT jfloat JNICALL Java_com_omni_wars_WarSettings_nativeGetFPS(JNIEnv*,jclass){
    long now=nowNs();
    if(gLastFrameNs>0) {
        float dt=(now-gLastFrameNs)/1e9f;
        gFrameTimes[gFTIdx%30]=dt;
        gFTIdx++;
    }
    gLastFrameNs=now;
    float avg=0;
    int count=std::min(gFTIdx,30);
    if(count==0) return 60.0f;
    for(int i=0;i<count;i++) avg+=gFrameTimes[i];
    avg/=count;
    return avg>0?1.0f/avg:60.0f;
}

JNIEXPORT jint JNICALL Java_com_omni_wars_WarSettings_nativeGetRecommendedQuality(JNIEnv*,jclass){
    float fps=0;
    int count=std::min(gFTIdx,30);
    for(int i=0;i<count;i++) fps+=gFrameTimes[i];
    if(count>0) { fps/=count; fps=fps>0?1/fps:60; }
    else fps=60;
    if(fps>=55) return 2;
    if(fps>=30) return 1;
    return 0;
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarSettings_nativeInitAudio(JNIEnv*,jclass){
    AAudioStreamBuilder* builder;
    if(AAudio_createStreamBuilder(&builder)!=AAUDIO_OK) return;
    AAudioStreamBuilder_setPerformanceMode(builder,AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder,AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_openStream(builder,&gAudioStream);
    AAudioStreamBuilder_delete(builder);
    if(gAudioStream) AAudioStream_requestStart(gAudioStream);
    LOGI("AAudio initialized");
}

JNIEXPORT void JNICALL Java_com_omni_wars_WarSettings_nativeReleaseAudio(JNIEnv*,jclass){
    if(gAudioStream) {
        AAudioStream_requestStop(gAudioStream);
        AAudioStream_close(gAudioStream);
        gAudioStream=nullptr;
    }
}

}
