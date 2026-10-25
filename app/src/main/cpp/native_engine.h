#ifndef POCKETCORE_NATIVE_ENGINE_H
#define POCKETCORE_NATIVE_ENGINE_H

#include <string>
#include <functional>
#include <atomic>
#include <mutex>
#include <memory>
#include <vector>
#include <cstdint>

namespace pocketcore {

/**
 * Callback function type invoked for every generated token string.
 */
using TokenCallbackFn = std::function<void(const std::string&)>;

/**
 * Model initialization configuration parameters.
 */
struct EngineConfig {
    std::string modelPath;
    int32_t nGpuLayers{33};
    int32_t contextSize{2048};
    int32_t threads{4};
    bool useVulkan{true};
};

/**
 * Generation completion parameters.
 */
struct CompletionParams {
    std::string prompt;
    float temperature{0.7f};
    float topP{0.9f};
    int32_t maxTokens{1024};
};

/**
 * Native C++ inference engine.
 * Thread-safe engine coordinating model lifecycle, Vulkan GPU offload setup,
 * token decoding loop, and atomic cooperative generation cancellation.
 */
class NativeEngine {
public:
    NativeEngine();
    virtual ~NativeEngine();

    // Non-copyable
    NativeEngine(const NativeEngine&) = delete;
    NativeEngine& operator=(const NativeEngine&) = delete;

    /**
     * Loads a GGUF model from the specified file path, initializing GPU offload
     * layers for Vulkan compute (Adreno GPU) and CPU fallback thread count.
     *
     * @return true if initialized successfully, false otherwise.
     */
    bool initModel(const EngineConfig& config);

    /**
     * Executes the token generation loop for the given prompt and sampling parameters.
     * Invokes `callback` synchronously for each decoded token until completion
     * or until `stopCompletion()` is signaled.
     *
     * @return true if completion finished normally or was stopped cleanly.
     */
    bool startCompletion(const CompletionParams& params, const TokenCallbackFn& callback);

    /**
     * Signals the ongoing token generation loop to abort immediately via atomic flag.
     */
    void stopCompletion();

    /**
     * Releases loaded model and context memory.
     */
    void releaseModel();

    /**
     * Returns true if a model is currently loaded and ready for inference.
     */
    bool isModelLoaded() const;

    /**
     * Returns true if stop generation has been requested.
     */
    bool isStopRequested() const;

    /**
     * Returns true if generation is currently in progress.
     */
    bool isGenerating() const;

    /**
     * Returns the active configuration.
     */
    const EngineConfig& getConfig() const;

private:
    void releaseModelInternal();

    mutable std::mutex m_mutex;
    std::atomic<bool> m_stopRequested{false};
    std::atomic<bool> m_isGenerating{false};
    std::atomic<bool> m_isLoaded{false};

    EngineConfig m_config;

    // Internal context pointers for llama.cpp / ggml structures
    void* m_model{nullptr};
    void* m_ctx{nullptr};
};

} // namespace pocketcore

#endif // POCKETCORE_NATIVE_ENGINE_H
