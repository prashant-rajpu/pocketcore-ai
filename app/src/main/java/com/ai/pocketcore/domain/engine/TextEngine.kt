package com.ai.pocketcore.domain.engine

import com.ai.pocketcore.domain.model.InferenceParams
import kotlinx.coroutines.flow.Flow

/**
 * Text inference engine interface for Large Language Models (LLMs).
 * Extends [InferenceEngine] with token streaming capabilities.
 */
interface TextEngine : InferenceEngine {
    /**
     * Streams generated text tokens asynchronously for the given prompt and parameters.
     *
     * @param prompt Formatted chat prompt or input text.
     * @param params Inference parameters including temperature, top-p, and context length.
     * @return Flow emitting individual token chunks as they are generated.
     */
    fun streamChat(prompt: String, params: InferenceParams = InferenceParams.DEFAULT): Flow<String>
}
