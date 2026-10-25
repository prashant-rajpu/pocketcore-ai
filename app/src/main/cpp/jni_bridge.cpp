#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <mutex>
#include <unordered_set>
#include "native_engine.h"

#define LOG_TAG "PocketCoreJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex s_engineMutex;
static std::unordered_set<pocketcore::NativeEngine*> s_activeEngines;

extern "C" {

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("libpocketcore.so loaded via JNI_OnLoad");
    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_com_ai_pocketcore_data_native_LlamaNativeBridge_nativeInitModel(
        JNIEnv* env,
        jobject /* thiz */,
        jstring jModelPath,
        jint jGpuLayers,
        jint jContextSize,
        jint jThreads) {
    if (!jModelPath) {
        LOGE("nativeInitModel: modelPath is null");
        return 0;
    }

    const char* pathChars = env->GetStringUTFChars(jModelPath, nullptr);
    if (!pathChars) {
        LOGE("nativeInitModel: failed to get string UTF chars");
        return 0;
    }
    std::string modelPath(pathChars);
    env->ReleaseStringUTFChars(jModelPath, pathChars);

    pocketcore::EngineConfig config;
    config.modelPath = modelPath;
    config.nGpuLayers = static_cast<int32_t>(jGpuLayers);
    config.contextSize = static_cast<int32_t>(jContextSize);
    config.threads = static_cast<int32_t>(jThreads);
    config.useVulkan = true;

    auto* engine = new (std::nothrow) pocketcore::NativeEngine();
    if (!engine) {
        LOGE("nativeInitModel: failed to allocate NativeEngine");
        return 0;
    }

    if (!engine->initModel(config)) {
        LOGE("nativeInitModel: engine->initModel failed");
        delete engine;
        return 0;
    }

    {
        std::lock_guard<std::mutex> lock(s_engineMutex);
        s_activeEngines.insert(engine);
    }

    LOGI("nativeInitModel: engine initialized successfully, returning handle %p", engine);
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_ai_pocketcore_data_native_LlamaNativeBridge_nativeStartCompletion(
        JNIEnv* env,
        jobject /* thiz */,
        jlong jHandle,
        jstring jPrompt,
        jfloat jTemperature,
        jfloat jTopP,
        jobject jCallback) {
    if (jHandle <= 0) {
        LOGE("nativeStartCompletion: invalid engine handle (%lld)", static_cast<long long>(jHandle));
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exClass) {
            env->ThrowNew(exClass, "Invalid engine handle");
            env->DeleteLocalRef(exClass);
        }
        return;
    }

    pocketcore::NativeEngine* engine = nullptr;
    {
        std::lock_guard<std::mutex> lock(s_engineMutex);
        auto* candidate = reinterpret_cast<pocketcore::NativeEngine*>(jHandle);
        if (s_activeEngines.find(candidate) == s_activeEngines.end()) {
            LOGE("nativeStartCompletion: engine handle is not registered or already released");
            jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
            if (exClass) {
                env->ThrowNew(exClass, "Invalid or released engine handle");
                env->DeleteLocalRef(exClass);
            }
            return;
        }
        engine = candidate;
    }

    if (!jPrompt) {
        LOGE("nativeStartCompletion: prompt is null");
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exClass) {
            env->ThrowNew(exClass, "Prompt cannot be null");
            env->DeleteLocalRef(exClass);
        }
        return;
    }

    const char* promptChars = env->GetStringUTFChars(jPrompt, nullptr);
    if (!promptChars) {
        LOGE("nativeStartCompletion: failed to get prompt UTF chars");
        return;
    }
    std::string prompt(promptChars);
    env->ReleaseStringUTFChars(jPrompt, promptChars);

    if (!jCallback) {
        LOGE("nativeStartCompletion: callback is null");
        jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exClass) {
            env->ThrowNew(exClass, "TokenCallback cannot be null");
            env->DeleteLocalRef(exClass);
        }
        return;
    }

    jclass callbackClass = env->GetObjectClass(jCallback);
    if (!callbackClass) {
        LOGE("nativeStartCompletion: cannot get callback class");
        return;
    }

    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(callbackClass);
    if (!onTokenMethod) {
        LOGE("nativeStartCompletion: cannot find onToken(String) method");
        return;
    }

    pocketcore::CompletionParams params;
    params.prompt = prompt;
    params.temperature = static_cast<float>(jTemperature);
    params.topP = static_cast<float>(jTopP);

    auto tokenCallback = [env, jCallback, onTokenMethod, engine](const std::string& token) {
        if (env->ExceptionCheck()) {
            engine->stopCompletion();
            return;
        }
        jstring jToken = env->NewStringUTF(token.c_str());
        if (jToken) {
            env->CallVoidMethod(jCallback, onTokenMethod, jToken);
            env->DeleteLocalRef(jToken);
        }
        if (env->ExceptionCheck()) {
            engine->stopCompletion();
        }
    };

    engine->startCompletion(params, tokenCallback);
}

JNIEXPORT void JNICALL
Java_com_ai_pocketcore_data_native_LlamaNativeBridge_nativeStopCompletion(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong jHandle) {
    if (jHandle <= 0) return;
    pocketcore::NativeEngine* engine = nullptr;
    {
        std::lock_guard<std::mutex> lock(s_engineMutex);
        auto* candidate = reinterpret_cast<pocketcore::NativeEngine*>(jHandle);
        if (s_activeEngines.find(candidate) != s_activeEngines.end()) {
            engine = candidate;
        }
    }
    if (engine) {
        engine->stopCompletion();
    }
}

JNIEXPORT void JNICALL
Java_com_ai_pocketcore_data_native_LlamaNativeBridge_nativeReleaseModel(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong jHandle) {
    if (jHandle <= 0) {
        LOGW("nativeReleaseModel: ignored invalid engine handle (%lld)", static_cast<long long>(jHandle));
        return;
    }

    pocketcore::NativeEngine* engine = nullptr;
    {
        std::lock_guard<std::mutex> lock(s_engineMutex);
        auto* candidate = reinterpret_cast<pocketcore::NativeEngine*>(jHandle);
        if (s_activeEngines.find(candidate) == s_activeEngines.end()) {
            LOGW("nativeReleaseModel: engine handle %lld already released or invalid", static_cast<long long>(jHandle));
            return;
        }
        s_activeEngines.erase(candidate);
        engine = candidate;
    }

    if (engine != nullptr) {
        engine->releaseModel();
        delete engine;
        LOGI("nativeReleaseModel: engine released and destroyed");
    }
}

} // extern "C"
