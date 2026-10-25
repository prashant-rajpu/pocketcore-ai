package com.ai.pocketcore.data.native

import com.ai.pocketcore.domain.engine.TextEngine
import com.ai.pocketcore.domain.model.InferenceParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Production implementation of [TextEngine] interfacing directly with the native C++20 engine
 * (`libpocketcore.so`) via [LlamaNativeBridge].
 */
class LlamaTextEngine(
    private val bridge: LlamaNativeBridge = LlamaNativeBridge()
) : TextEngine {

    @Volatile
    private var nativeHandle: Long = 0L

    @Volatile
    var loadedModelPath: String? = null
        private set

    override val isLoaded: Boolean
        get() = nativeHandle != 0L

    /**
     * Initializes a GGUF model in native memory.
     *
     * @param modelPath Absolute file path to the .gguf model file on storage.
     * @param nGpuLayers Number of transformer layers to offload to the Adreno 830 GPU via Vulkan.
     * @param contextSize Model context window size in tokens.
     * @param threads CPU threads for compute.
     * @return True if initialized successfully, false otherwise.
     */
    fun load(
        modelPath: String,
        nGpuLayers: Int = 33,
        contextSize: Int = 2048,
        threads: Int = 4
    ): Boolean {
        unloadSync()
        return try {
            val handle = bridge.initModel(
                modelPath = modelPath,
                nGpuLayers = nGpuLayers,
                contextSize = contextSize,
                threads = threads
            )
            if (handle != 0L) {
                nativeHandle = handle
                loadedModelPath = modelPath
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun unload() {
        unloadSync()
    }

    private fun unloadSync() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            loadedModelPath = null
            try {
                bridge.releaseModel(handle)
            } catch (_: Throwable) {
            }
        }
    }

    override fun streamChat(prompt: String, params: InferenceParams): Flow<String> {
        val handle = nativeHandle
        if (handle == 0L) return emptyFlow()
        return bridge.streamTokens(
            handle = handle,
            prompt = prompt,
            temperature = params.temperature,
            topP = params.topP
        )
    }
}
