package com.ai.pocketcore.domain.engine

import android.graphics.Bitmap

/**
 * Image synthesis engine interface for on-device diffusion models (Stable Diffusion).
 * Extends [InferenceEngine] for multi-modal text-to-image generation.
 */
interface ImageEngine : InferenceEngine {
    /**
     * Generates an image bitmap from a natural language text prompt.
     *
     * @param prompt Descriptive prompt guiding image generation.
     * @param steps Number of denoising diffusion steps.
     * @param cfg Classifier-Free Guidance scale.
     * @return Generated [Bitmap] rendered on device.
     */
    suspend fun generateImage(prompt: String, steps: Int = 20, cfg: Float = 7.5f): Bitmap
}
