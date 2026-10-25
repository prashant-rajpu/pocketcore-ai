package com.ai.pocketcore.domain.usecase

import com.ai.pocketcore.domain.engine.TextEngine
import com.ai.pocketcore.domain.model.ChatEvent
import com.ai.pocketcore.domain.model.InferenceParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case that orchestrates LLM chat completion streaming.
 * Emits individual tokens, real-time generation telemetry (tokens/s and TTFT),
 * and handles completion/error lifecycle states.
 */
class StreamChatUseCase(
    private val textEngine: TextEngine,
    private val telemetryIntervalMs: Long = 500L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Executes streaming inference for the given prompt and parameters.
     *
     * @param prompt User prompt formatted according to the model's chat template.
     * @param params Inference parameters (temperature, topP, etc.).
     * @param telemetryIntervalMs Milliseconds interval between intermediate telemetry events (0 to disable).
     * @param emitFinalTelemetry Whether to emit a final [ChatEvent.Telemetry] before [ChatEvent.Completed].
     * @return Flow emitting [ChatEvent]s.
     */
    operator fun invoke(
        prompt: String,
        params: InferenceParams = InferenceParams.DEFAULT,
        telemetryIntervalMs: Long = this.telemetryIntervalMs,
        emitFinalTelemetry: Boolean = false
    ): Flow<ChatEvent> = flow {
        if (!textEngine.isLoaded) {
            emit(ChatEvent.Error(IllegalStateException("Text engine is not loaded")))
            return@flow
        }

        val startTime = timeProvider()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        var lastTelemetryTime = startTime

        try {
            textEngine.streamChat(prompt, params).collect { token ->
                val now = timeProvider()
                tokenCount++
                if (firstTokenTime == null) {
                    firstTokenTime = now
                }

                emit(ChatEvent.Token(token))

                if (telemetryIntervalMs > 0 && (now - lastTelemetryTime >= telemetryIntervalMs)) {
                    val telemetry = calculateTelemetry(
                        tokenCount = tokenCount,
                        startTimeMs = startTime,
                        firstTokenTimeMs = firstTokenTime,
                        currentTimeMs = now
                    )
                    emit(telemetry)
                    lastTelemetryTime = now
                }
            }

            if (emitFinalTelemetry && tokenCount > 0) {
                val finalTelemetry = calculateTelemetry(
                    tokenCount = tokenCount,
                    startTimeMs = startTime,
                    firstTokenTimeMs = firstTokenTime,
                    currentTimeMs = timeProvider()
                )
                emit(finalTelemetry)
            }

            emit(ChatEvent.Completed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            emit(ChatEvent.Error(t))
        }
    }

    companion object {
        /**
         * Calculates inference telemetry: Time To First Token (TTFT) and decode throughput (tokens/sec).
         *
         * @param tokenCount Number of tokens generated so far.
         * @param startTimeMs Epoch time when prompt streaming was initiated.
         * @param firstTokenTimeMs Epoch time when the first token arrived (null if none yet).
         * @param currentTimeMs Current epoch time.
         * @return [ChatEvent.Telemetry] with TTFT in ms and tokens/sec.
         */
        fun calculateTelemetry(
            tokenCount: Int,
            startTimeMs: Long,
            firstTokenTimeMs: Long?,
            currentTimeMs: Long
        ): ChatEvent.Telemetry {
            val ttftMs = if (firstTokenTimeMs != null) {
                (firstTokenTimeMs - startTimeMs).coerceAtLeast(0L)
            } else {
                (currentTimeMs - startTimeMs).coerceAtLeast(0L)
            }

            val decodeMs = if (firstTokenTimeMs != null && currentTimeMs > firstTokenTimeMs) {
                currentTimeMs - firstTokenTimeMs
            } else {
                (currentTimeMs - startTimeMs).coerceAtLeast(0L)
            }

            val elapsedSec = decodeMs.toDouble() / 1000.0
            val tokensPerSec = if (elapsedSec > 0.0) {
                tokenCount.toDouble() / elapsedSec
            } else {
                0.0
            }

            return ChatEvent.Telemetry(
                tokensPerSec = tokensPerSec,
                ttftMs = ttftMs
            )
        }
    }
}
