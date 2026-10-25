package com.ai.pocketcore.domain.model

/**
 * Hyperparameters and resource configuration for on-device inference.
 *
 * @property temperature Sampling temperature (lower = more deterministic, higher = more creative).
 * @property topP Nucleus sampling probability cutoff.
 * @property contextLength Context window size in tokens allocated in the KV cache.
 * @property threads CPU thread allocation for compute.
 * @property gpuLayers Number of model layers offloaded to the Vulkan GPU backend.
 * @property maxTokens Maximum number of tokens to generate per completion request.
 */
data class InferenceParams(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val contextLength: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 33,
    val maxTokens: Int = 2048
) {
    companion object {
        val DEFAULT = InferenceParams()
    }
}
