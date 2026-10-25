package com.ai.pocketcore.domain.engine

/**
 * Base interface for all on-device inference engines.
 * Manages model loading state and resource release across CPU and Vulkan backends.
 */
interface InferenceEngine {
    /**
     * Indicates whether the model weights and inference context are loaded in memory.
     */
    val isLoaded: Boolean

    /**
     * Unloads the model and releases associated compute buffers and context memory.
     */
    suspend fun unload()
}
