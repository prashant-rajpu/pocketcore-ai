package com.ai.pocketcore.domain

import com.ai.pocketcore.domain.engine.TextEngine
import com.ai.pocketcore.domain.model.ChatEvent
import com.ai.pocketcore.domain.model.InferenceParams
import com.ai.pocketcore.domain.usecase.StreamChatUseCase
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamChatUseCaseTest {

    @Test
    fun useCaseEmitsTokensAndCompletionEvent() = runBlocking {
        val mockEngine = object : TextEngine {
            override val isLoaded = true
            override suspend fun unload() {}
            override fun streamChat(prompt: String, params: InferenceParams) = flowOf("Hello", " world")
        }
        val useCase = StreamChatUseCase(mockEngine)
        val events = useCase("Hi", InferenceParams.DEFAULT).toList()
        assertEquals(3, events.size) // 2 tokens + 1 Completed
        assertEquals(ChatEvent.Token("Hello"), events[0])
        assertEquals(ChatEvent.Token(" world"), events[1])
        assertEquals(ChatEvent.Completed, events[2])
    }

    @Test
    fun useCaseEmitsTelemetryWhenIntervalElapsed() = runBlocking {
        var currentTime = 1000L
        val mockEngine = object : TextEngine {
            override val isLoaded = true
            override suspend fun unload() {}
            override fun streamChat(prompt: String, params: InferenceParams) = flow {
                currentTime = 1150L // 150ms TTFT
                emit("Deep")
                currentTime = 1700L // 550ms later (>= 500ms interval)
                emit("Seek")
            }
        }

        val useCase = StreamChatUseCase(
            textEngine = mockEngine,
            telemetryIntervalMs = 500L,
            timeProvider = { currentTime }
        )

        val events = useCase("Tell me", InferenceParams.DEFAULT).toList()
        val telemetryEvents = events.filterIsInstance<ChatEvent.Telemetry>()

        assertTrue("Telemetry event should be emitted when interval elapses", telemetryEvents.isNotEmpty())
        val telemetry = telemetryEvents.first()
        assertEquals(150L, telemetry.ttftMs)
        assertTrue("Tokens/sec should be positive", telemetry.tokensPerSec > 0.0)
        assertEquals(telemetry.tokensPerSec, telemetry.tokPerSec, 0.001)
    }

    @Test
    fun useCaseEmitsErrorWhenEngineThrows() = runBlocking {
        val mockEngine = object : TextEngine {
            override val isLoaded = true
            override suspend fun unload() {}
            override fun streamChat(prompt: String, params: InferenceParams) = flow<String> {
                throw RuntimeException("Vulkan compute failure")
            }
        }
        val useCase = StreamChatUseCase(mockEngine)
        val events = useCase("Hi", InferenceParams.DEFAULT).toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is ChatEvent.Error)
        val error = events[0] as ChatEvent.Error
        assertEquals("Vulkan compute failure", error.throwable.message)
    }

    @Test
    fun useCaseEmitsErrorWhenEngineNotLoaded() = runBlocking {
        val mockEngine = object : TextEngine {
            override val isLoaded = false
            override suspend fun unload() {}
            override fun streamChat(prompt: String, params: InferenceParams) = flowOf("Never")
        }
        val useCase = StreamChatUseCase(mockEngine)
        val events = useCase("Hi", InferenceParams.DEFAULT).toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is ChatEvent.Error)
        val error = events[0] as ChatEvent.Error
        assertTrue(error.throwable is IllegalStateException)
    }

    @Test
    fun calculateTelemetryComputesSpeedAndTtftAccurately() {
        val telemetry = StreamChatUseCase.calculateTelemetry(
            tokenCount = 10,
            startTimeMs = 1000L,
            firstTokenTimeMs = 1200L,
            currentTimeMs = 2200L
        )

        assertEquals(200L, telemetry.ttftMs)
        assertEquals(10.0, telemetry.tokensPerSec, 0.001)
        assertEquals(10.0, telemetry.tokPerSec, 0.001)
    }
}
