package com.ai.pocketcore.data.native

/**
 * Prompt templates supported by PocketCore AI for formatting chat conversation turns.
 */
enum class PromptTemplate(
    val templateName: String,
    val stopTokens: List<String>
) {
    CHAT_ML(
        templateName = "ChatML",
        stopTokens = listOf("<|im_end|>", "<|im_start|>")
    ),
    LLAMA_3(
        templateName = "Llama-3",
        stopTokens = listOf("<|eot_id|>", "<|end_of_text|>")
    ),
    GEMMA(
        templateName = "Gemma",
        stopTokens = listOf("<end_of_turn>", "<eos>")
    ),
    ALPACA(
        templateName = "Alpaca",
        stopTokens = listOf("### Instruction:", "</s>")
    );

    companion object {
        /**
         * Infers the appropriate prompt template based on model filename or identifier.
         */
        fun detect(modelIdentifier: String): PromptTemplate {
            val lower = modelIdentifier.lowercase()
            return when {
                lower.contains("llama-3") || lower.contains("llama3") -> LLAMA_3
                lower.contains("gemma") -> GEMMA
                lower.contains("alpaca") -> ALPACA
                lower.contains("qwen") || lower.contains("chatml") -> CHAT_ML
                else -> CHAT_ML
            }
        }
    }
}
