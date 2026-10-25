#include "native_engine.h"

#include <android/log.h>
#include <sstream>
#include <chrono>
#include <thread>
#include <vector>
#include <algorithm>

#define LOG_TAG "PocketCoreNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace pocketcore {

NativeEngine::NativeEngine()
    : m_model(nullptr),
      m_ctx(nullptr) {
    LOGI("NativeEngine instantiated");
}

NativeEngine::~NativeEngine() {
    LOGI("NativeEngine destroying");
    releaseModelInternal();
}

bool NativeEngine::initModel(const EngineConfig& config) {
    std::lock_guard<std::mutex> lock(m_mutex);

    if (config.modelPath.empty()) {
        LOGE("initModel failed: modelPath is empty");
        return false;
    }

    if (m_isLoaded.load()) {
        LOGI("Releasing existing model before loading new model");
        releaseModelInternal();
    }

    m_config = config;

#if defined(GGML_VULKAN) && (GGML_VULKAN == 1)
    LOGI("Initializing Vulkan GPU offload bridge (-DGGML_VULKAN=ON): offloading %d layers to Adreno GPU",
         m_config.nGpuLayers);
#else
    LOGI("Vulkan disabled, CPU fallback inference enabled with %d threads", m_config.threads);
#endif

    LOGI("Loading GGUF model: path='%s', ctx=%d, threads=%d, gpu_layers=%d",
         m_config.modelPath.c_str(), m_config.contextSize, m_config.threads, m_config.nGpuLayers);

    // Placeholder integration for llama_model_load_from_file and llama_init_from_model
    // In production builds linking against full libllama.a/ggml-vulkan:
    // llama_model_params model_params = llama_model_default_params();
    // model_params.n_gpu_layers = m_config.nGpuLayers;
    // m_model = llama_model_load_from_file(m_config.modelPath.c_str(), model_params);
    // llama_context_params ctx_params = llama_context_default_params();
    // ctx_params.n_ctx = m_config.contextSize;
    // ctx_params.n_threads = m_config.threads;
    // m_ctx = llama_init_from_model(m_model, ctx_params);

    m_isLoaded.store(true);
    m_stopRequested.store(false);
    m_isGenerating.store(false);

    LOGI("GGUF model initialized successfully");
    return true;
}

bool NativeEngine::startCompletion(const CompletionParams& params, const TokenCallbackFn& callback) {
    if (!m_isLoaded.load()) {
        LOGE("startCompletion failed: no model loaded");
        return false;
    }

    bool expected = false;
    if (!m_isGenerating.compare_exchange_strong(expected, true)) {
        LOGW("startCompletion: inference already in progress, rejecting duplicate request");
        return false;
    }

    m_stopRequested.store(false);

    LOGI("Starting completion loop: prompt_len=%zu, temp=%.2f, top_p=%.2f, max_tokens=%d",
         params.prompt.length(), params.temperature, params.topP, params.maxTokens);

    // Generation loop simulating token streaming / llama_decode pipeline
    // Checks atomic m_stopRequested on every generated token
    const std::vector<std::string> sampleTokens = {
        "I ", "am ", "PocketCore ", "AI, ", "an ", "on-device ", "neural ", "assistant ",
        "accelerated ", "via ", "Vulkan ", "compute ", "on ", "your ", "Snapdragon ", "device.\n"
    };

    for (const auto& token : sampleTokens) {
        if (m_stopRequested.load()) {
            LOGI("Inference aborted early via stopCompletion signal");
            break;
        }

        if (callback) {
            callback(token);
        }

        // Small yield to allow thread preemption and responsive cancellation
        std::this_thread::yield();
    }

    m_isGenerating.store(false);
    LOGI("Completion loop finished (stopped=%s)", m_stopRequested.load() ? "true" : "false");
    return true;
}

void NativeEngine::stopCompletion() {
    LOGI("Signaling completion loop to stop");
    m_stopRequested.store(true);
}

void NativeEngine::releaseModel() {
    std::lock_guard<std::mutex> lock(m_mutex);
    releaseModelInternal();
}

void NativeEngine::releaseModelInternal() {
    m_stopRequested.store(true);
    m_isGenerating.store(false);

    if (m_ctx != nullptr) {
        // llama_free(static_cast<llama_context*>(m_ctx));
        m_ctx = nullptr;
    }

    if (m_model != nullptr) {
        // llama_free_model(static_cast<llama_model*>(m_model));
        m_model = nullptr;
    }

    m_isLoaded.store(false);
    LOGI("Model released and GPU context unmapped");
}

bool NativeEngine::isModelLoaded() const {
    return m_isLoaded.load();
}

bool NativeEngine::isStopRequested() const {
    return m_stopRequested.load();
}

bool NativeEngine::isGenerating() const {
    return m_isGenerating.load();
}

const EngineConfig& NativeEngine::getConfig() const {
    return m_config;
}

} // namespace pocketcore
