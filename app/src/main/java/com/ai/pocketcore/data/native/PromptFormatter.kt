package com.ai.pocketcore.data.native

/**
 * Formats chat conversation turns into model-specific prompt representations.
 */
object PromptFormatter {

    /**
     * Formats a sequence of (role, content) pairs into a prompt string for the given template.
     */
    fun format(template: PromptTemplate, messages: List<Pair<String, String>>): String {
        return when (template) {
            PromptTemplate.CHAT_ML -> formatChatMl(messages)
            PromptTemplate.LLAMA_3 -> formatLlama3(messages)
            PromptTemplate.GEMMA -> formatGemma(messages)
            PromptTemplate.ALPACA -> formatAlpaca(messages)
        }
    }

    /**
     * Convenience method to format a single turn with an optional system prompt and user input.
     */
    fun format(template: PromptTemplate, systemPrompt: String?, userPrompt: String): String {
        val messages = mutableListOf<Pair<String, String>>()
        if (!systemPrompt.isNullOrBlank()) {
            messages.add("system" to systemPrompt)
        }
        messages.add("user" to userPrompt)
        return format(template, messages)
    }

    private fun formatChatMl(messages: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        for ((role, content) in messages) {
            sb.append("<|im_start|>").append(role).append("\n")
                .append(content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatLlama3(messages: List<Pair<String, String>>): String {
        val sb = StringBuilder("<|begin_of_text|>")
        for ((role, content) in messages) {
            sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n")
                .append(content).append("<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatGemma(messages: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        for ((role, content) in messages) {
            val normalizedRole = when (role.lowercase()) {
                "assistant" -> "model"
                "system" -> "user"
                else -> role
            }
            sb.append("<start_of_turn>").append(normalizedRole).append("\n")
                .append(content).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun formatAlpaca(messages: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        val systemMsg = messages.firstOrNull { it.first.equals("system", ignoreCase = true) }?.second
        val defaultSystem = "Below is an instruction that describes a task. Write a response that appropriately completes the request."
        
        sb.append(systemMsg ?: defaultSystem).append("\n\n")

        for ((role, content) in messages) {
            when (role.lowercase()) {
                "system" -> continue // Already included
                "user" -> sb.append("### Instruction:\n").append(content).append("\n\n")
                "assistant" -> sb.append("### Response:\n").append(content).append("\n\n")
                else -> sb.append("### ").append(role.replaceFirstChar { it.uppercase() }).append(":\n").append(content).append("\n\n")
            }
        }
        sb.append("### Response:\n")
        return sb.toString()
    }
}
