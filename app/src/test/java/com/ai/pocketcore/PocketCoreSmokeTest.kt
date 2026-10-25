package com.ai.pocketcore

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * JVM Companion End-to-End Smoke Verification for PocketCore AI.
 *
 * Runs headless in CI and standard developer workstations via `./gradlew testDebugUnitTest`.
 * Verifies the full pipeline end-to-end:
 * Room DB Persistence -> Model Loading & 12GB RAM Headroom -> Token Streaming -> Telemetry Calculation -> Message Storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PocketCoreSmokeTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeModelDao: FakeModelDao
    private lateinit var fakeChatDao: FakeChatDao

    private val targetModel = ModelEntity(
        id = "meta-llama/Llama-3.2-3B-Instruct",
        repoId = "meta-llama/Llama-3.2-3B-Instruct",
        fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        localPath = "/data/local/models/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        fileSizeBytes = 2_200_000_000L,
        quantization = "Q4_K_M",
        isActive = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeModelDao = FakeModelDao()
        fakeChatDao = FakeChatDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun endToEndPipeline_ModelLoad_Streaming_Telemetry_Persistence() = runBlocking {
        // Step 1: Model Registration in Room
        fakeModelDao.insertModel(targetModel)
        val initialModel = fakeModelDao.getModelById(targetModel.id)
        assertNotNull("Model should be stored in Room", initialModel)
        assertFalse("Model starts inactive", initialModel!!.isActive)

        // Step 2: Model Loading & Galaxy S25 12GB RAM Headroom Validation
        val galaxyS25RamBytes = 12_000_000_000L
        val priorEngine = SmokeTestTextEngine(emptyList())
        priorEngine.isLoaded = true

        val loadModelUseCase = LoadModelUseCase(fakeModelDao, priorEngine)
        val loadedModel = loadModelUseCase(targetModel.id, availableRamBytes = galaxyS25RamBytes)

        assertTrue("Model must be active after loading", loadedModel.isActive)
        assertFalse("Prior engine should be safely unloaded", priorEngine.isLoaded)

        val activeInDb = fakeModelDao.getActiveModel().first()
        assertEquals(targetModel.id, activeInDb?.id)

        // Initialize and load text engine for newly activated model
        val mockTextEngine = SmokeTestTextEngine(
            tokens = listOf("PocketCore", " offline", " AI", " on", " Snapdragon", " 8", " Elite", "!")
        )
        mockTextEngine.isLoaded = true

        // Step 3: Stream Chat Completion with Telemetry
        var virtualClock = 2000L
        val streamChatUseCase = StreamChatUseCase(
            textEngine = mockTextEngine,
            telemetryIntervalMs = 50L,
            timeProvider = { virtualClock }
        )

        val prompt = "Why run models locally?"
        val events = mutableListOf<ChatEvent>()
        val accumulatedText = StringBuilder()

        streamChatUseCase(prompt, InferenceParams.DEFAULT).collect { event ->
            events.add(event)
            when (event) {
                is ChatEvent.Token -> {
                    accumulatedText.append(event.text)
                    virtualClock += 30L
                }
                is ChatEvent.Telemetry -> {
                    assertTrue("TTFT must be recorded", event.ttftMs >= 0L)
                    assertTrue("Tokens/sec must be calculated", event.tokensPerSec >= 0.0)
                }
                is ChatEvent.Completed -> {}
                is ChatEvent.Error -> fail("Unexpected stream error: ${event.message}")
            }
        }

        // Step 4: Validate Stream Output and Telemetry
        val tokenEvents = events.filterIsInstance<ChatEvent.Token>()
        val telemetryEvents = events.filterIsInstance<ChatEvent.Telemetry>()
        val completedEvents = events.filterIsInstance<ChatEvent.Completed>()

        assertEquals(8, tokenEvents.size)
        assertEquals("PocketCore offline AI on Snapdragon 8 Elite!", accumulatedText.toString())
        assertTrue("Telemetry events must be emitted", telemetryEvents.isNotEmpty())
        assertEquals(1, completedEvents.size)

        val finalTelemetry = telemetryEvents.last()
        assertTrue("Tokens/sec must be > 0", finalTelemetry.tokensPerSec > 0.0)

        // Step 5: Message Storage in ChatDao
        val sessionId = "jvm-smoke-session-1"
        val userTurn = ChatMessageEntity(
            sessionId = sessionId,
            role = ChatRole.USER,
            content = prompt,
            timestamp = 2000L,
            modelIdUsed = activeInDb?.id
        )
        val assistantTurn = ChatMessageEntity(
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = accumulatedText.toString(),
            timestamp = virtualClock,
            tokensPerSec = finalTelemetry.tokensPerSec,
            ttftMs = finalTelemetry.ttftMs,
            modelIdUsed = activeInDb?.id
        )

        chatDaoInsert(userTurn)
        chatDaoInsert(assistantTurn)

        // Step 6: Verify Persistence Round-Trip
        val history = fakeChatDao.getMessagesForSession(sessionId).first()
        assertEquals(2, history.size)

        val retrievedUser = history[0]
        assertEquals(ChatRole.USER, retrievedUser.role)
        assertEquals(prompt, retrievedUser.content)
        assertEquals(targetModel.id, retrievedUser.modelIdUsed)

        val retrievedAssistant = history[1]
        assertEquals(ChatRole.ASSISTANT, retrievedAssistant.role)
        assertEquals("PocketCore offline AI on Snapdragon 8 Elite!", retrievedAssistant.content)
        assertEquals(finalTelemetry.ttftMs, retrievedAssistant.ttftMs)
        assertEquals(finalTelemetry.tokensPerSec, retrievedAssistant.tokensPerSec, 0.001)
        assertEquals(targetModel.id, retrievedAssistant.modelIdUsed)
    }

    @Test
    fun studioViewModel_orchestratesFullInteractivePipeline() = runBlocking {
        fakeModelDao.insertModel(targetModel.copy(isActive = true))

        val engine = SmokeTestTextEngine(
            tokens = listOf("40-60", " tokens", " per", " second", " on", " Vulkan")
        )
        val streamChatUseCase = StreamChatUseCase(
            textEngine = engine,
            telemetryIntervalMs = 0L
        )

        val viewModel = StudioViewModel(
            streamChatUseCase = streamChatUseCase,
            chatDao = fakeChatDao,
            modelDao = fakeModelDao,
            ioDispatcher = testDispatcher,
            getRamUsageBytes = { 4_200_000_000L },
            gpuBackendName = "Vulkan"
        )

        // Submit prompt
        viewModel.sendMessage("What is the inference speed on S25?")

        // Validate final state
        assertFalse("Generating state reset after completion", viewModel.isGenerating.value)
        assertEquals("", viewModel.activeStreamingText.value)

        // Validate messages stored in Room
        val messages = fakeChatDao.getMessagesForSession(viewModel.sessionId).first()
        assertEquals(2, messages.size)
        assertEquals("What is the inference speed on S25?", messages[0].content)
        assertEquals("40-60 tokens per second on Vulkan", messages[1].content)
        assertEquals(targetModel.id, messages[1].modelIdUsed)
    }

    @Test
    fun ramSafetyThreshold_rejectsOversizedModelsOnGalaxyS25() = runBlocking {
        // 11GB model on 12GB device exceeds 85% headroom safety (10.2GB maximum limit)
        val oversizedModel = ModelEntity(
            id = "deepseek-ai/DeepSeek-R1-Distill-Llama-70B",
            repoId = "deepseek-ai/DeepSeek-R1-Distill-Llama-70B",
            fileName = "DeepSeek-R1-Distill-Llama-70B-Q4_K_M.gguf",
            localPath = "/data/local/models/oversized.gguf",
            fileSizeBytes = 11_000_000_000L,
            quantization = "Q4_K_M",
            isActive = false
        )
        fakeModelDao.insertModel(oversizedModel)

        val engine = SmokeTestTextEngine(emptyList<String>())
        val loadModelUseCase = LoadModelUseCase(fakeModelDao, engine)

        try {
            loadModelUseCase(oversizedModel.id, availableRamBytes = 12_000_000_000L)
            fail("Expected InsufficientMemoryException")
        } catch (e: InsufficientMemoryException) {
            assertEquals(11_000_000_000L, e.requiredBytes)
            assertEquals(12_000_000_000L, e.availableBytes)
        }

        // Verify model was not set to active
        val persisted = fakeModelDao.getModelById(oversizedModel.id)
        assertFalse("Oversized model must remain inactive", persisted!!.isActive)
        val active = fakeModelDao.getActiveModel().first()
        assertNull("No model should be active", active)
    }

    @Test
    fun studioViewModel_stopGenerationMidFlight_persistsPartialResponse() = runBlocking {
        fakeModelDao.insertModel(targetModel.copy(isActive = true))

        var emitCount = 0
        val engine = object : TextEngine {
            override var isLoaded = true
            override suspend fun unload() { isLoaded = false }
            override fun streamChat(prompt: String, params: InferenceParams): Flow<String> = flow {
                emit("Part 1")
                emitCount++
                emit(" Part 2")
                emitCount++
                // Simulate waiting for subsequent tokens
                kotlinx.coroutines.delay(10_000L)
                emit(" Part 3")
            }
        }

        val streamChatUseCase = StreamChatUseCase(textEngine = engine, telemetryIntervalMs = 0L)
        val viewModel = StudioViewModel(
            streamChatUseCase = streamChatUseCase,
            chatDao = fakeChatDao,
            modelDao = fakeModelDao,
            ioDispatcher = testDispatcher,
            getRamUsageBytes = { 3_800_000_000L }
        )

        // Launch generation
        viewModel.sendMessage("Interrupted generation test")
        // Trigger manual abort mid-stream
        viewModel.stopGeneration()

        // Generation should be stopped and active text cleared
        assertFalse("Generating should be false after stop", viewModel.isGenerating.value)
        assertEquals("", viewModel.activeStreamingText.value)

        // History in Room should contain the partial response
        val messages = fakeChatDao.getMessagesForSession(viewModel.sessionId).first()
        assertEquals(2, messages.size)
        assertEquals(ChatRole.USER, messages[0].role)
        assertEquals("Interrupted generation test", messages[0].content)
        assertEquals(ChatRole.ASSISTANT, messages[1].role)
        assertTrue("Assistant turn contains partial text", messages[1].content.startsWith("Part 1 Part 2"))
    }

    @Test
    fun multiTurnPersistence_maintainsChronologicalOrder() = runBlocking {
        fakeModelDao.insertModel(targetModel.copy(isActive = true))
        val sessionId = "multi-turn-jvm-session"

        val t1User = ChatMessageEntity(sessionId = sessionId, role = ChatRole.USER, content = "Hi", timestamp = 1000L)
        val t1Assistant = ChatMessageEntity(sessionId = sessionId, role = ChatRole.ASSISTANT, content = "Hello! How can I assist?", timestamp = 1050L)
        val t2User = ChatMessageEntity(sessionId = sessionId, role = ChatRole.USER, content = "Explain quantum superposition", timestamp = 2000L)
        val t2Assistant = ChatMessageEntity(sessionId = sessionId, role = ChatRole.ASSISTANT, content = "Superposition is a principle of quantum mechanics...", timestamp = 2100L)

        fakeChatDao.insertMessages(listOf(t1User, t1Assistant, t2User, t2Assistant))

        val retrieved = fakeChatDao.getMessagesForSession(sessionId).first()
        assertEquals(4, retrieved.size)
        assertEquals("Hi", retrieved[0].content)
        assertEquals("Hello! How can I assist?", retrieved[1].content)
        assertEquals("Explain quantum superposition", retrieved[2].content)
        assertEquals("Superposition is a principle of quantum mechanics...", retrieved[3].content)
    }

    private suspend fun chatDaoInsert(message: ChatMessageEntity) {
        fakeChatDao.insertMessage(message)
    }

    /**
     * Test [TextEngine] simulating token generation without native library dependencies.
     */
    private class SmokeTestTextEngine(
        private val tokens: List<String>
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

    /**
     * In-memory implementation of [ModelDao] for pure JVM headless verification.
     */
    private class FakeModelDao : ModelDao {
        val modelsFlow = MutableStateFlow<List<ModelEntity>>(emptyList())

        override fun getAllModels(): Flow<List<ModelEntity>> = modelsFlow

        override fun getActiveModel(): Flow<ModelEntity?> = modelsFlow.map { list ->
            list.find { it.isActive }
        }

        override suspend fun getModelById(id: String): ModelEntity? =
            modelsFlow.value.find { it.id == id }

        override suspend fun insertModel(model: ModelEntity) {
            val current = modelsFlow.value.filter { it.id != model.id }
            modelsFlow.value = current + model
        }

        override suspend fun insertModels(models: List<ModelEntity>) {
            modelsFlow.value = models
        }

        override suspend fun updateModel(model: ModelEntity) {
            insertModel(model)
        }

        override suspend fun setActiveModel(modelId: String) {
            modelsFlow.value = modelsFlow.value.map {
                it.copy(isActive = it.id == modelId)
            }
        }

        override suspend fun clearActiveModel() {
            modelsFlow.value = modelsFlow.value.map {
                it.copy(isActive = false)
            }
        }

        override suspend fun deleteModelById(id: String) {
            modelsFlow.value = modelsFlow.value.filter { it.id != id }
        }

        override suspend fun deleteAllModels() {
            modelsFlow.value = emptyList()
        }

        override fun getModelCount(): Flow<Int> = modelsFlow.map { it.size }
    }

    /**
     * In-memory implementation of [ChatDao] for pure JVM headless verification.
     */
    private class FakeChatDao : ChatDao {
        val messagesFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
        private var nextId = 1L

        override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
            messagesFlow.map { list ->
                list.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.timestamp }, { it.id }))
            }

        override fun getLatestMessage(sessionId: String): Flow<ChatMessageEntity?> =
            messagesFlow.map { list ->
                list.filter { it.sessionId == sessionId }
                    .maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
            }

        override suspend fun insertMessage(message: ChatMessageEntity): Long {
            val assigned = if (message.id == 0L) message.copy(id = nextId++) else message
            messagesFlow.value = messagesFlow.value + assigned
            return assigned.id
        }

        override suspend fun insertMessages(messages: List<ChatMessageEntity>): List<Long> {
            return messages.map { insertMessage(it) }
        }

        override suspend fun updateMessage(message: ChatMessageEntity) {
            messagesFlow.value = messagesFlow.value.map {
                if (it.id == message.id) message else it
            }
        }

        override suspend fun clearSession(sessionId: String) {
            messagesFlow.value = messagesFlow.value.filter { it.sessionId != sessionId }
        }

        override suspend fun deleteMessageById(id: Long) {
            messagesFlow.value = messagesFlow.value.filter { it.id != id }
        }

        override suspend fun clearAll() {
            messagesFlow.value = emptyList()
        }

        override fun getAllSessionIds(): Flow<List<String>> =
            messagesFlow.map { list -> list.map { it.sessionId }.distinct() }
    }
}
