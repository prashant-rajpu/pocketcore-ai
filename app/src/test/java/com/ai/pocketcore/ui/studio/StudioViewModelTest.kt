package com.ai.pocketcore.ui.studio

import com.ai.pocketcore.data.db.dao.ChatDao
import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ChatMessageEntity
import com.ai.pocketcore.data.db.entity.ChatRole
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.domain.engine.TextEngine
import com.ai.pocketcore.domain.model.InferenceParams
import com.ai.pocketcore.domain.usecase.StreamChatUseCase
import com.ai.pocketcore.ui.studio.components.MarkdownParser
import com.ai.pocketcore.ui.studio.components.MarkdownSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeChatDao: FakeChatDao
    private lateinit var fakeModelDao: FakeModelDao
    private lateinit var fakeTextEngine: FakeTextEngine
    private lateinit var streamChatUseCase: StreamChatUseCase
    private lateinit var viewModel: StudioViewModel

    private val activeModelEntity = ModelEntity(
        id = "meta-llama/Llama-3.2-3B-Instruct",
        repoId = "meta-llama/Llama-3.2-3B-Instruct",
        fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        localPath = "/data/local/models/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        fileSizeBytes = 3_000_000_000L,
        quantization = "Q4_K_M",
        isActive = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeChatDao = FakeChatDao()
        fakeModelDao = FakeModelDao()
        fakeTextEngine = FakeTextEngine(isLoaded = true)

        fakeModelDao.modelsFlow.value = listOf(activeModelEntity)

        streamChatUseCase = StreamChatUseCase(
            textEngine = fakeTextEngine,
            telemetryIntervalMs = 0L // immediate emission without time-delay in tests
        )

        viewModel = StudioViewModel(
            streamChatUseCase = streamChatUseCase,
            chatDao = fakeChatDao,
            modelDao = fakeModelDao,
            ioDispatcher = testDispatcher,
            getRamUsageBytes = { 4_000_000_000L },
            gpuBackendName = "Vulkan"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun accumulatesStreamingTokensIntoActiveMessage() = runBlocking {
        fakeTextEngine.tokenSequence = listOf("Deep", "Seek", " on", " S25")

        viewModel.sendMessage("Hello, world!")

        // After completion:
        assertEquals("", viewModel.activeStreamingText.value)
        assertFalse(viewModel.isGenerating.value)

        // The chat history should contain the user prompt and the accumulated assistant response
        val messages = fakeChatDao.messagesFlow.value
        assertEquals(2, messages.size)

        val userMsg = messages[0]
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("Hello, world!", userMsg.content)
        assertEquals(activeModelEntity.id, userMsg.modelIdUsed)

        val assistantMsg = messages[1]
        assertEquals(ChatRole.ASSISTANT, assistantMsg.role)
        assertEquals("DeepSeek on S25", assistantMsg.content)
        assertEquals(activeModelEntity.id, assistantMsg.modelIdUsed)
    }

    @Test
    fun stopActionHaltsGenerationAndPersistsPartialMessage() = runBlocking {
        val tokenFlow = MutableSharedFlow<String>()
        fakeTextEngine.customStreamFlow = tokenFlow

        viewModel.sendMessage("Compute something long")

        // First token emitted
        tokenFlow.emit("Starting ")
        assertEquals("Starting ", viewModel.activeStreamingText.value)
        assertTrue(viewModel.isGenerating.value)

        tokenFlow.emit("heavy calculation...")
        assertEquals("Starting heavy calculation...", viewModel.activeStreamingText.value)

        // User taps stop
        viewModel.stopGeneration()

        assertFalse(viewModel.isGenerating.value)
        assertEquals("", viewModel.activeStreamingText.value)

        // Partial message should be saved into ChatDao
        val messages = fakeChatDao.messagesFlow.value
        assertEquals(2, messages.size)
        assertEquals("Starting heavy calculation...", messages[1].content)
        assertEquals(ChatRole.ASSISTANT, messages[1].role)
    }

    @Test
    fun clearSessionDeletesMessagesAndResetsActiveState() = runBlocking {
        fakeChatDao.insertMessage(
            ChatMessageEntity(sessionId = "default_session", role = ChatRole.USER, content = "Old message")
        )
        fakeChatDao.insertMessage(
            ChatMessageEntity(sessionId = "default_session", role = ChatRole.ASSISTANT, content = "Old reply")
        )
        assertEquals(2, fakeChatDao.messagesFlow.value.size)

        viewModel.clearSession()

        assertEquals(0, fakeChatDao.messagesFlow.value.size)
        assertEquals("", viewModel.activeStreamingText.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun updateParamsUpdatesInferenceHyperparameters() {
        val newParams = InferenceParams(
            temperature = 0.2f,
            topP = 0.95f,
            contextLength = 4096,
            threads = 8,
            gpuLayers = 45,
            maxTokens = 1024
        )

        viewModel.updateParams(newParams)
        assertEquals(newParams, viewModel.params.value)
        assertEquals(0.2f, viewModel.params.value.temperature, 0.001f)
        assertEquals(45, viewModel.params.value.gpuLayers)
    }

    @Test
    fun setSystemPromptUpdatesSystemPromptState() {
        val customPrompt = "You are an expert Kotlin engineer."
        viewModel.setSystemPrompt(customPrompt)
        assertEquals(customPrompt, viewModel.systemPrompt.value)
    }

    @Test
    fun sendMessageIgnoredWhenPromptIsBlank() {
        viewModel.sendMessage("   ")
        assertEquals(0, fakeChatDao.messagesFlow.value.size)
        assertFalse(viewModel.isGenerating.value)
    }

    @Test
    fun sendMessageIgnoredWhenAlreadyGenerating() = runBlocking {
        val tokenFlow = MutableSharedFlow<String>()
        fakeTextEngine.customStreamFlow = tokenFlow

        viewModel.sendMessage("First prompt")
        assertTrue(viewModel.isGenerating.value)

        // Attempting to send a second prompt while generating must be ignored
        viewModel.sendMessage("Second prompt")

        val userMessages = fakeChatDao.messagesFlow.value.filter { it.role == ChatRole.USER }
        assertEquals(1, userMessages.size)
        assertEquals("First prompt", userMessages.first().content)
    }

    @Test
    fun errorMessageSetWhenNoActiveModelIsLoaded() {
        fakeModelDao.modelsFlow.value = emptyList() // No active model

        viewModel.sendMessage("Hello without model")

        assertNotNull(viewModel.errorMessage.value)
        assertFalse(viewModel.isGenerating.value)
        assertEquals(0, fakeChatDao.messagesFlow.value.size)
    }

    @Test
    fun streamChatErrorSetsErrorMessageAndResetsGeneratingState() = runBlocking {
        fakeTextEngine.throwErrorOnStream = RuntimeException("Vulkan out of device memory")

        viewModel.sendMessage("Run out of memory")

        assertFalse(viewModel.isGenerating.value)
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("Vulkan out of device memory"))
    }

    @Test
    fun promptFormattingDoesNotDuplicateUserTurnWhenRoomFlowEmits() = runBlocking {
        fakeTextEngine.tokenSequence = listOf("Response")

        // First turn
        viewModel.sendMessage("Question 1")
        assertEquals(2, fakeChatDao.messagesFlow.value.size)

        // Second turn: verifies that Question 2 does not appear twice in the prompt passed to streamChat
        viewModel.sendMessage("Question 2")
        val prompt = fakeTextEngine.lastPromptReceived
        assertNotNull(prompt)

        // Count occurrences of "Question 2" in the formatted prompt
        val occurrences = Regex("Question 2").findAll(prompt!!).count()
        assertEquals("User question must appear exactly once in the formatted prompt", 1, occurrences)
    }

    @Test
    fun sendMessageReturnsBooleanIndicatingSuccess() = runBlocking {
        val blankResult = viewModel.sendMessage("   ")
        assertFalse("Blank prompt should return false", blankResult)

        val tokenFlow = MutableSharedFlow<String>()
        fakeTextEngine.customStreamFlow = tokenFlow

        val successResult = viewModel.sendMessage("Valid prompt")
        assertTrue("Valid prompt with loaded model should return true", successResult)
        assertTrue(viewModel.isGenerating.value)

        val busyResult = viewModel.sendMessage("Prompt while generating")
        assertFalse("Prompt submitted while generating should return false", busyResult)

        viewModel.stopGeneration()
    }

    @Test
    fun telemetryStateInitializesAndTracksTelemetry() {
        val telemetry = viewModel.telemetryState.value
        assertEquals("Vulkan", telemetry.gpuBackend)
        assertEquals(4_000_000_000L.toDouble() / (1024.0 * 1024.0 * 1024.0), telemetry.ramUsageGb, 0.01)
        assertFalse(telemetry.isGenerating)
    }

    @Test
    fun clearErrorResetsActiveErrorMessage() {
        fakeModelDao.modelsFlow.value = emptyList()
        viewModel.sendMessage("Test")
        assertNotNull(viewModel.errorMessage.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun systemPromptPresetsContainFlagshipConfigurations() {
        val presets = StudioViewModel.SYSTEM_PROMPT_PRESETS
        assertEquals(4, presets.size)
        assertTrue(presets.any { it.name == "PocketCore Pro" })
        assertTrue(presets.any { it.name == "Code Architect" })
        assertTrue(presets.any { it.name == "Concise & Fast" })
        assertTrue(presets.any { it.name == "Creative Writer" })
    }

    @Test
    fun provideFactoryConstructsStudioViewModelInstance() {
        val factory = StudioViewModel.provideFactory(
            streamChatUseCase = streamChatUseCase,
            chatDao = fakeChatDao,
            modelDao = fakeModelDao
        )
        val vm = factory.create(StudioViewModel::class.java)
        assertNotNull(vm)
        assertEquals(StudioViewModel.DEFAULT_SESSION_ID, vm.sessionId)
    }

    @Test
    fun markdownParserExtractsFencedCodeBlocksWithLanguage() {
        val markdown = """
            Here is a Kotlin function:
            ```kotlin
            fun main() {
                println("Hello S25")
            }
            ```
            That was fast!
        """.trimIndent()

        val segments = MarkdownParser.parse(markdown)
        assertEquals(3, segments.size)

        assertTrue(segments[0] is MarkdownSegment.Text)
        assertEquals("Here is a Kotlin function:\n", (segments[0] as MarkdownSegment.Text).content)

        assertTrue(segments[1] is MarkdownSegment.Code)
        val codeSegment = segments[1] as MarkdownSegment.Code
        assertEquals("kotlin", codeSegment.language)
        assertTrue(codeSegment.code.contains("println(\"Hello S25\")"))

        assertTrue(segments[2] is MarkdownSegment.Text)
        assertEquals("\nThat was fast!", (segments[2] as MarkdownSegment.Text).content)
    }

    @Test
    fun markdownParserHandlesUnclosedFencedCodeBlocksDuringStreaming() {
        val inProgressMarkdown = "Analysis:\n```python\ndef compute(x):\n    return x * 2"
        val segments = MarkdownParser.parse(inProgressMarkdown)

        assertEquals(2, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Text)
        assertTrue(segments[1] is MarkdownSegment.Code)

        val codeSegment = segments[1] as MarkdownSegment.Code
        assertEquals("python", codeSegment.language)
        assertEquals("def compute(x):\n    return x * 2", codeSegment.code)
    }

    @Test
    fun markdownParserBuildsInlineAnnotatedString() {
        val raw = "This is **bold** text with `val x = 10` and *italic* styling."
        val annotated = MarkdownParser.buildInlineMarkdown(raw, isUserMessage = false)

        assertTrue(annotated.text.contains("bold"))
        assertTrue(annotated.text.contains("val x = 10"))
        assertTrue(annotated.text.contains("italic"))
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}

// Test Doubles
private class FakeChatDao : ChatDao {
    val messagesFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var idCounter = 1L

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> =
        messagesFlow.map { list -> list.filter { it.sessionId == sessionId } }

    override fun getLatestMessage(sessionId: String): Flow<ChatMessageEntity?> =
        messagesFlow.map { list -> list.filter { it.sessionId == sessionId }.lastOrNull() }

    override suspend fun insertMessage(message: ChatMessageEntity): Long {
        val id = if (message.id == 0L) idCounter++ else message.id
        val messageWithId = message.copy(id = id)
        messagesFlow.value = messagesFlow.value + messageWithId
        return id
    }

    override suspend fun insertMessages(messages: List<ChatMessageEntity>): List<Long> {
        val ids = mutableListOf<Long>()
        for (m in messages) {
            ids.add(insertMessage(m))
        }
        return ids
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

private class FakeTextEngine(override var isLoaded: Boolean = true) : TextEngine {
    var tokenSequence: List<String> = emptyList()
    var customStreamFlow: Flow<String>? = null
    var throwErrorOnStream: Throwable? = null
    var lastPromptReceived: String? = null

    override suspend fun unload() {
        isLoaded = false
    }

    override fun streamChat(prompt: String, params: InferenceParams): Flow<String> {
        lastPromptReceived = prompt
        throwErrorOnStream?.let { throw it }
        val custom = customStreamFlow
        if (custom != null) {
            return custom
        }
        return flow {
            tokenSequence.forEach { emit(it) }
        }
    }
}
