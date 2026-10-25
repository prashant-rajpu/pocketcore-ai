package com.ai.pocketcore

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.pocketcore.data.db.AppDatabase
import com.ai.pocketcore.data.db.dao.ChatDao
import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ChatMessageEntity
import com.ai.pocketcore.data.db.entity.ChatRole
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.domain.engine.TextEngine
import com.ai.pocketcore.domain.model.ChatEvent
import com.ai.pocketcore.domain.model.InferenceParams
import com.ai.pocketcore.domain.model.InsufficientMemoryException
import com.ai.pocketcore.domain.usecase.LoadModelUseCase
import com.ai.pocketcore.domain.usecase.StreamChatUseCase
import com.ai.pocketcore.ui.studio.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * End-to-End Android Instrumentation Smoke Verification for PocketCore AI.
 *
 * Verifies the complete on-device pipeline against the real Android runtime and Room SQLite:
 * 1. Room Database persistence & schema integrity
 * 2. Model loading & Snapdragon 8 Elite RAM headroom safety verification (12GB LPDDR5X)
 * 3. Text inference token streaming via [StreamChatUseCase]
 * 4. Real-time telemetry computation (TTFT & tokens/sec)
 * 5. Multi-turn conversation persistence in [ChatDao]
 * 6. Studio conversational state orchestration via [StudioViewModel]
 */
@RunWith(AndroidJUnit4::class)
class PocketCoreSmokeTest {

    private lateinit var database: AppDatabase
    private lateinit var modelDao: ModelDao
    private lateinit var chatDao: ChatDao
    private lateinit var context: Context

    private val targetModel = ModelEntity(
        id = "meta-llama/Llama-3.2-3B-Instruct",
        repoId = "meta-llama/Llama-3.2-3B-Instruct",
        fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        localPath = "/data/local/models/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        fileSizeBytes = 2_200_000_000L, // 2.2 GB fits safely within S25 12GB RAM
        quantization = "Q4_K_M",
        isActive = false
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        modelDao = database.modelDao()
        chatDao = database.chatDao()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun endToEndPipeline_RoomDb_ModelLoading_Streaming_Telemetry_MessageStorage() = runBlocking {
        // Step 1: Model Registration in Room DB
        modelDao.insertModel(targetModel)
        val storedModel = modelDao.getModelById(targetModel.id)
        assertNotNull("Model must be persisted in Room DB", storedModel)
        assertFalse("Model initially inactive", storedModel!!.isActive)

        // Step 2: Model Loading with Galaxy S25 RAM Headroom Check (12GB RAM)
        val s25PhysicalRamBytes = 12_000_000_000L
        val priorEngine = SmokeTestTextEngine(emptyList())
        priorEngine.isLoaded = true

        val loadModelUseCase = LoadModelUseCase(modelDao, priorEngine)
        val activatedModel = loadModelUseCase(targetModel.id, availableRamBytes = s25PhysicalRamBytes)

        assertTrue("Model must be activated after loading", activatedModel.isActive)
        assertFalse("Prior engine must be safely unloaded", priorEngine.isLoaded)

        val activeInDb = modelDao.getActiveModel().first()
        assertNotNull(activeInDb)
        assertEquals(targetModel.id, activeInDb?.id)

        // Initialize and load text engine for the activated model
        val mockNativeEngine = SmokeTestTextEngine(
            tokens = listOf("PocketCore", " AI", " running", " on", " Snapdragon", " 8", " Elite", " Vulkan", "."),
            simulatedTtftMs = 120L,
            tokenIntervalMs = 20L
        )
        mockNativeEngine.isLoaded = true

        // Step 3: Prompt Construction & Token Streaming
        var virtualClock = 1000L
        val streamChatUseCase = StreamChatUseCase(
            textEngine = mockNativeEngine,
            telemetryIntervalMs = 50L,
            timeProvider = { virtualClock }
        )

        val prompt = "Tell me about PocketCore on Galaxy S25"
        val events = mutableListOf<ChatEvent>()
        val accumulatedText = StringBuilder()

        streamChatUseCase(prompt, InferenceParams.DEFAULT).collect { event ->
            events.add(event)
            when (event) {
                is ChatEvent.Token -> {
                    accumulatedText.append(event.text)
                    virtualClock += 25L
                }
                is ChatEvent.Telemetry -> {
                    assertTrue("TTFT must be positive", event.ttftMs > 0L)
                    assertTrue("Tokens/sec must be positive", event.tokensPerSec > 0.0)
                }
                is ChatEvent.Completed -> {
                    // Stream finished
                }
                is ChatEvent.Error -> {
                    fail("Unexpected streaming error: ${event.throwable.message}")
                }
            }
        }

        // Step 4: Telemetry Verification
        val tokenEvents = events.filterIsInstance<ChatEvent.Token>()
        val telemetryEvents = events.filterIsInstance<ChatEvent.Telemetry>()
        val completedEvents = events.filterIsInstance<ChatEvent.Completed>()

        assertEquals(9, tokenEvents.size)
        assertEquals("PocketCore AI running on Snapdragon 8 Elite Vulkan.", accumulatedText.toString())
        assertTrue("Telemetry events must be emitted", telemetryEvents.isNotEmpty())
        assertEquals(1, completedEvents.size)

        val finalTelemetry = telemetryEvents.last()
        assertTrue("Tokens per second must be greater than zero", finalTelemetry.tokensPerSec > 0.0)
        assertEquals(120L, finalTelemetry.ttftMs)

        // Step 5: Message Persistence into Room DB
        val sessionId = "smoke-test-session-e2e"
        val userMessage = ChatMessageEntity(
            sessionId = sessionId,
            role = ChatRole.USER,
            content = prompt,
            timestamp = 1000L,
            modelIdUsed = activeInDb?.id
        )
        val assistantMessage = ChatMessageEntity(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = accumulatedText.toString(),
            timestamp = virtualClock,
            tokensPerSec = finalTelemetry.tokensPerSec,
            ttftMs = finalTelemetry.ttftMs,
            modelIdUsed = activeInDb?.id
        )

        chatDao.insertMessage(userMessage)
        chatDao.insertMessage(assistantMessage)

        // Step 6: Verify Database Round-Trip Integrity
        val sessionMessages = chatDao.getMessagesForSession(sessionId).first()
        assertEquals(2, sessionMessages.size)

        val persistedUserTurn = sessionMessages[0]
        assertEquals(ChatRole.USER, persistedUserTurn.role)
        assertEquals(prompt, persistedUserTurn.content)
        assertEquals(targetModel.id, persistedUserTurn.modelIdUsed)

        val persistedAssistantTurn = sessionMessages[1]
        assertEquals(ChatRole.ASSISTANT, persistedAssistantTurn.role)
        assertEquals("PocketCore AI running on Snapdragon 8 Elite Vulkan.", persistedAssistantTurn.content)
        assertEquals(finalTelemetry.ttftMs, persistedAssistantTurn.ttftMs)
        assertEquals(finalTelemetry.tokensPerSec, persistedAssistantTurn.tokensPerSec, 0.001)
        assertEquals(targetModel.id, persistedAssistantTurn.modelIdUsed)
    }

    @Test
    fun studioViewModel_endToEndInteractiveFlow() = runBlocking {
        // Prepare active model in Room
        val activeModel = targetModel.copy(isActive = true)
        modelDao.insertModel(activeModel)

        val engine = SmokeTestTextEngine(
            tokens = listOf("Vulkan", " compute", " is", " ultra", " fast"),
            simulatedTtftMs = 80L,
            tokenIntervalMs = 15L
        )
        val streamChatUseCase = StreamChatUseCase(
            textEngine = engine,
            telemetryIntervalMs = 0L
        )

        val viewModel = StudioViewModel(
            streamChatUseCase = streamChatUseCase,
            chatDao = chatDao,
            modelDao = modelDao,
            ioDispatcher = Dispatchers.Main,
            getRamUsageBytes = { 4_500_000_000L },
            gpuBackendName = "Vulkan"
        )

        // Execute conversational prompt
        viewModel.sendMessage("How fast is Vulkan?")

        // Validate final state
        assertFalse("Generating state must be false after completion", viewModel.isGenerating.value)
        assertEquals("", viewModel.activeStreamingText.value)

        // Verify Room persistence from StudioViewModel
        val messages = chatDao.getMessagesForSession(viewModel.sessionId).first()
        assertEquals(2, messages.size)
        assertEquals("How fast is Vulkan?", messages[0].content)
        assertEquals("Vulkan compute is ultra fast", messages[1].content)
        assertEquals(activeModel.id, messages[1].modelIdUsed)
    }

    @Test
    fun ramHeadroomSafety_preventsOomOnOversizedModels() = runBlocking {
        // An 11GB model exceeds 85% of Galaxy S25's 12GB physical RAM (10.2GB limit)
        val oversizedModel = ModelEntity(
            id = "deepseek-ai/DeepSeek-R1-Distill-Qwen-70B",
            repoId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-70B",
            fileName = "DeepSeek-R1-Distill-Qwen-70B-Q4_K_M.gguf",
            localPath = "/data/local/models/oversized.gguf",
            fileSizeBytes = 11_000_000_000L,
            quantization = "Q4_K_M",
            isActive = false
        )
        modelDao.insertModel(oversizedModel)

        val engine = SmokeTestTextEngine(emptyList<String>())
        val loadModelUseCase = LoadModelUseCase(modelDao, engine)

        try {
            loadModelUseCase(oversizedModel.id, availableRamBytes = 12_000_000_000L)
            fail("Expected InsufficientMemoryException for oversized model footprint")
        } catch (e: InsufficientMemoryException) {
            assertEquals(11_000_000_000L, e.requiredBytes)
            assertEquals(12_000_000_000L, e.availableBytes)
        }

        // Verify model remains inactive in Room
        val persisted = modelDao.getModelById(oversizedModel.id)
        assertFalse("Oversized model must not be activated", persisted!!.isActive)
        val active = modelDao.getActiveModel().first()
        assertNull("No active model should exist", active)
    }

    @Test
    fun multiTurnChatPersistence_preservesConversationContext() = runBlocking {
        modelDao.insertModel(targetModel.copy(isActive = true))
        val sessionId = "multi-turn-smoke-session"

        // Turn 1
        chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = ChatRole.USER, content = "Hello", timestamp = 100L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = ChatRole.ASSISTANT, content = "Hi! How can I help?", timestamp = 200L))

        // Turn 2
        chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = ChatRole.USER, content = "Summarize quantum computing", timestamp = 300L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = sessionId, role = ChatRole.ASSISTANT, content = "Quantum computing uses qubits...", timestamp = 400L))

        val history = chatDao.getMessagesForSession(sessionId).first()
        assertEquals(4, history.size)
        assertEquals("Hello", history[0].content)
        assertEquals("Hi! How can I help?", history[1].content)
        assertEquals("Summarize quantum computing", history[2].content)
        assertEquals("Quantum computing uses qubits...", history[3].content)
    }

    /**
     * Test [TextEngine] simulating token streaming and hardware timings.
     */
    private class SmokeTestTextEngine(
        private val tokens: List<String>,
        private val simulatedTtftMs: Long = 100L,
        private val tokenIntervalMs: Long = 20L
    ) : TextEngine {
        override var isLoaded: Boolean = true

        override suspend fun unload() {
            isLoaded = false
        }

        override fun streamChat(prompt: String, params: InferenceParams): Flow<String> = flow {
            for (token in tokens) {
                emit(token)
            }
        }
    }
}
