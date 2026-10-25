package com.ai.pocketcore.data.native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {

    @Test
    fun formatsChatMlPromptCorrectly() {
        val messages = listOf(
            Pair("system", "You are an AI on Android."),
            Pair("user", "Hello!")
        )
        val formatted = PromptFormatter.format(PromptTemplate.CHAT_ML, messages)
        assertTrue(formatted.contains("<|im_start|>system\nYou are an AI on Android.<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nHello!<|im_end|>\n<|im_start|>assistant\n"))
    }

    @Test
    fun formatsLlama3PromptCorrectly() {
        val messages = listOf(
            Pair("system", "You are a helpful assistant."),
            Pair("user", "What is Galaxy AI?")
        )
        val formatted = PromptFormatter.format(PromptTemplate.LLAMA_3, messages)
        assertTrue(formatted.contains("<|begin_of_text|>"))
        assertTrue(formatted.contains("<|start_header_id|>system<|end_header_id|>\n\nYou are a helpful assistant.<|eot_id|>"))
        assertTrue(formatted.contains("<|start_header_id|>user<|end_header_id|>\n\nWhat is Galaxy AI?<|eot_id|>"))
        assertTrue(formatted.endsWith(suffix = "<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun formatsGemmaPromptCorrectly() {
        val messages = listOf(
            Pair("user", "Explain quantum computing in simple terms.")
        )
        val formatted = PromptFormatter.format(PromptTemplate.GEMMA, messages)
        assertTrue(formatted.contains("<start_of_turn>user\nExplain quantum computing in simple terms.<end_of_turn>"))
        assertTrue(formatted.endsWith(suffix = "<start_of_turn>model\n"))
    }

    @Test
    fun formatsAlpacaPromptCorrectly() {
        val messages = listOf(
            Pair("system", "You are an expert tutor."),
            Pair("user", "Teach me calculus.")
        )
        val formatted = PromptFormatter.format(PromptTemplate.ALPACA, messages)
        assertTrue(formatted.contains("You are an expert tutor."))
        assertTrue(formatted.contains("### Instruction:\nTeach me calculus."))
        assertTrue(formatted.endsWith(suffix = "\n\n### Response:\n"))
    }

    @Test
    fun detectsTemplateFromModelName() {
        assertEquals(PromptTemplate.LLAMA_3, PromptTemplate.detect("Meta-Llama-3-8B-Instruct.Q4_K_M.gguf"))
        assertEquals(PromptTemplate.LLAMA_3, PromptTemplate.detect("llama-3.2-1b-instruct.gguf"))
        assertEquals(PromptTemplate.GEMMA, PromptTemplate.detect("gemma-2-2b-it.Q4_K_M.gguf"))
        assertEquals(PromptTemplate.ALPACA, PromptTemplate.detect("alpaca-7b-q4.gguf"))
        assertEquals(PromptTemplate.CHAT_ML, PromptTemplate.detect("qwen2.5-coder-7b.gguf"))
        assertEquals(PromptTemplate.CHAT_ML, PromptTemplate.detect("generic-model.gguf"))
    }

    @Test
    fun templateStopTokensAreDefined() {
        assertTrue(PromptTemplate.CHAT_ML.stopTokens.contains("<|im_end|>"))
        assertTrue(PromptTemplate.LLAMA_3.stopTokens.contains("<|eot_id|>"))
        assertTrue(PromptTemplate.GEMMA.stopTokens.contains("<end_of_turn>"))
        assertTrue(PromptTemplate.ALPACA.stopTokens.contains("### Instruction:"))
    }

    @Test
    fun handlesEmptyMessagesGracefully() {
        val formatted = PromptFormatter.format(PromptTemplate.CHAT_ML, emptyList<Pair<String, String>>())
        assertEquals("<|im_start|>assistant\n", formatted)
    }

    @Test
    fun formatsMultiTurnConversationCorrectly() {
        val messages = listOf(
            Pair("user", "Hi"),
            Pair("assistant", "Hello! How can I help you?"),
            Pair("user", "Tell me a joke")
        )
        val formatted = PromptFormatter.format(PromptTemplate.CHAT_ML, messages)
        assertTrue(formatted.contains("<|im_start|>user\nHi<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>assistant\nHello! How can I help you?<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nTell me a joke<|im_end|>"))
        assertTrue(formatted.endsWith(suffix = "<|im_start|>assistant\n"))
    }
}
