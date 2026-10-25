package com.ai.pocketcore.domain.model

/**
 * Domain stream events emitted during model inference.
 */
sealed interface ChatEvent {

    /**
     * Incremental token produced by the language model.
     */
    data class Token(val text: String) : ChatEvent

    /**
     * Real-time telemetry snapshot containing generation throughput and time to first token.
     *
     * @property tokensPerSec Generated tokens per second during the decode phase.
     * @property ttftMs Time to first token in milliseconds from prompt submission.
     */
    data class Telemetry(
        val tokensPerSec: Double,
        val ttftMs: Long
    ) : ChatEvent {
        /**
         * Alias for [tokensPerSec] for concise UI telemetry HUD bindings.
         */
        val tokPerSec: Double get() = tokensPerSec
    }

    /**
     * Signals that generation reached completion (EOS or maxTokens limit).
     */
    data object Completed : ChatEvent

    /**
     * Emitted if inference failed or encountered an unrecoverable engine error.
     */
    data class Error(
        val throwable: Throwable,
        val message: String = throwable.message ?: "Inference engine error"
    ) : ChatEvent
}
