package com.ai.pocketcore.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.pocketcore.data.db.dao.ChatDao
import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ChatMessageEntity
import com.ai.pocketcore.data.db.entity.ChatRole
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.data.native.PromptFormatter
import com.ai.pocketcore.data.native.PromptTemplate
import com.ai.pocketcore.domain.model.ChatEvent
import com.ai.pocketcore.domain.model.InferenceParams
import com.ai.pocketcore.domain.usecase.StreamChatUseCase
import com.ai.pocketcore.ui.components.TelemetryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * System prompt preset option for quick switching.
 */
data class SystemPromptPreset(
    val name: String,
    val description: String,
    val prompt: String
)

/**
 * ViewModel managing the on-device AI Studio:
 * - Conversational message stream and Room persistence via [ChatDao]
 * - Active streaming tokens accumulation and generation lifecycle
 * - Real-time hardware telemetry HUD updates ([TelemetryState])
 * - Model hyperparameters ([InferenceParams]) and System Prompt customization
 */
class StudioViewModel(
    private val streamChatUseCase: StreamChatUseCase,
    private val chatDao: ChatDao,
    private val modelDao: ModelDao,
    val sessionId: String = DEFAULT_SESSION_ID,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val getRamUsageBytes: () -> Long = { DEFAULT_RAM_BYTES },
    private val gpuBackendName: String = "Vulkan"
) : ViewModel() {

    private val _activeModel = MutableStateFlow<ModelEntity?>(null)
    val activeModel: StateFlow<ModelEntity?> = _activeModel.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    private val _activeStreamingText = MutableStateFlow("")
    val activeStreamingText: StateFlow<String> = _activeStreamingText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _telemetryState = MutableStateFlow(
        TelemetryState(
            tokensPerSec = 0.0,
            ttftMs = 0L,
            ramUsageGb = getRamGb(),
            gpuBackend = gpuBackendName,
            isGenerating = false
        )
    )
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    private val _params = MutableStateFlow(InferenceParams.DEFAULT)
    val params: StateFlow<InferenceParams> = _params.asStateFlow()

    private val _systemPrompt = MutableStateFlow(DEFAULT_SYSTEM_PROMPT)
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Observe active model from local database
        viewModelScope.launch(ioDispatcher) {
            modelDao.getActiveModel().collect { model ->
                _activeModel.value = model
            }
        }

        // Observe chat history for current session
        viewModelScope.launch(ioDispatcher) {
            chatDao.getMessagesForSession(sessionId).collect { list ->
                _messages.value = list
            }
        }
    }

    private fun getRamGb(): Double {
        return getRamUsageBytes().toDouble() / (1024.0 * 1024.0 * 1024.0)
    }

    /**
     * Sends user prompt for streaming inference turn.
     * Returns true if prompt was accepted and streaming began, false otherwise.
     */
    fun sendMessage(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _isGenerating.value) {
            return false
        }

        val model = _activeModel.value
        if (model == null) {
            _errorMessage.value = "No active model loaded. Please load a model from the Vault."
            return false
        }

        // Capture prior conversation history turn snapshot before inserting new user message
        // to prevent Room Flow asynchronous updates from duplicating turns in formattedPrompt
        val priorHistory = _messages.value

        viewModelScope.launch {
            // Save user message to database
            val userMessage = ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.USER,
                content = trimmed,
                timestamp = System.currentTimeMillis(),
                modelIdUsed = model.id
            )
            withContext(ioDispatcher) {
                chatDao.insertMessage(userMessage)
            }

            // Build formatted chat prompt turns from immutable priorHistory snapshot
            val template = PromptTemplate.detect(model.fileName.ifBlank { model.id })
            val turnPairs = mutableListOf<Pair<String, String>>()
            if (_systemPrompt.value.isNotBlank()) {
                turnPairs.add("system" to _systemPrompt.value)
            }
            for (msg in priorHistory) {
                turnPairs.add(msg.role.name.lowercase() to msg.content)
            }
            turnPairs.add("user" to trimmed)
            val formattedPrompt = PromptFormatter.format(template, turnPairs)

            // Reset streaming state
            _errorMessage.value = null
            _activeStreamingText.value = ""
            _isGenerating.value = true
            _telemetryState.value = _telemetryState.value.copy(
                isGenerating = true,
                ramUsageGb = getRamGb()
            )

            // Stream response
            generationJob = launch(ioDispatcher) {
                var lastTokensPerSec = 0.0
                var lastTtftMs = 0L

                try {
                    streamChatUseCase(
                        prompt = formattedPrompt,
                        params = _params.value,
                        emitFinalTelemetry = true
                    ).collect { event ->
                        when (event) {
                            is ChatEvent.Token -> {
                                _activeStreamingText.value += event.text
                            }
                            is ChatEvent.Telemetry -> {
                                lastTokensPerSec = event.tokensPerSec
                                lastTtftMs = event.ttftMs
                                _telemetryState.value = _telemetryState.value.copy(
                                    tokensPerSec = event.tokensPerSec,
                                    ttftMs = event.ttftMs,
                                    isGenerating = true,
                                    ramUsageGb = getRamGb()
                                )
                            }
                            is ChatEvent.Completed -> {
                                val completedContent = _activeStreamingText.value
                                val assistantMessage = ChatMessageEntity(
                                    sessionId = sessionId,
                                    role = ChatRole.ASSISTANT,
                                    content = completedContent,
                                    timestamp = System.currentTimeMillis(),
                                    tokensPerSec = lastTokensPerSec,
                                    ttftMs = lastTtftMs,
                                    modelIdUsed = model.id
                                )
                                chatDao.insertMessage(assistantMessage)
                                _activeStreamingText.value = ""
                                _isGenerating.value = false
                                _telemetryState.value = _telemetryState.value.copy(isGenerating = false)
                            }
                            is ChatEvent.Error -> {
                                _errorMessage.value = event.message
                                _activeStreamingText.value = ""
                                _isGenerating.value = false
                                _telemetryState.value = _telemetryState.value.copy(isGenerating = false)
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _errorMessage.value = t.message ?: "Inference engine error"
                    _activeStreamingText.value = ""
                    _isGenerating.value = false
                    _telemetryState.value = _telemetryState.value.copy(isGenerating = false)
                }
            }
        }
        return true
    }

    /**
     * Halts ongoing inference stream, saving any partial tokens generated so far into history.
     */
    fun stopGeneration() {
        if (!_isGenerating.value) return

        val partialContent = _activeStreamingText.value
        val lastTokPerSec = _telemetryState.value.tokensPerSec
        val lastTtft = _telemetryState.value.ttftMs
        val modelId = _activeModel.value?.id

        generationJob?.cancel()
        generationJob = null

        if (partialContent.isNotBlank()) {
            viewModelScope.launch(ioDispatcher) {
                val partialMessage = ChatMessageEntity(
                    sessionId = sessionId,
                    role = ChatRole.ASSISTANT,
                    content = partialContent,
                    timestamp = System.currentTimeMillis(),
                    tokensPerSec = lastTokPerSec,
                    ttftMs = lastTtft,
                    modelIdUsed = modelId
                )
                chatDao.insertMessage(partialMessage)
            }
        }

        _activeStreamingText.value = ""
        _isGenerating.value = false
        _telemetryState.value = _telemetryState.value.copy(isGenerating = false)
    }

    /**
     * Clears all messages in the active session and resets generation state.
     */
    fun clearSession() {
        if (_isGenerating.value) {
            generationJob?.cancel()
            generationJob = null
            _isGenerating.value = false
            _telemetryState.value = _telemetryState.value.copy(isGenerating = false)
        }
        _activeStreamingText.value = ""
        _errorMessage.value = null

        viewModelScope.launch(ioDispatcher) {
            chatDao.clearSession(sessionId)
        }
    }

    /**
     * Updates model inference hyperparameters.
     */
    fun updateParams(newParams: InferenceParams) {
        _params.value = newParams
    }

    /**
     * Updates system prompt for the chat session.
     */
    fun setSystemPrompt(prompt: String) {
        _systemPrompt.value = prompt
    }

    /**
     * Clears any active error message banner.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        const val DEFAULT_SESSION_ID = "default_session"
        const val DEFAULT_RAM_BYTES = 4_000_000_000L

        const val DEFAULT_SYSTEM_PROMPT =
            "You are PocketCore AI, a high-performance offline AI assistant running locally on Samsung Galaxy S25."

        val SYSTEM_PROMPT_PRESETS: List<SystemPromptPreset> = listOf(
            SystemPromptPreset(
                name = "PocketCore Pro",
                description = "Balanced, versatile everyday flagship assistant",
                prompt = "You are PocketCore AI, a high-performance offline AI assistant running locally on Samsung Galaxy S25."
            ),
            SystemPromptPreset(
                name = "Code Architect",
                description = "Deep programming, clean code, and architecture",
                prompt = "You are an expert senior software engineer. Provide clean, production-grade code with concise technical explanations and optimal algorithmic complexity."
            ),
            SystemPromptPreset(
                name = "Concise & Fast",
                description = "Direct, minimal answers with zero fluff",
                prompt = "You are an ultra-fast on-device assistant. Answer questions directly, accurately, and with minimal words. No conversational filler."
            ),
            SystemPromptPreset(
                name = "Creative Writer",
                description = "Imaginative storytelling and literary prose",
                prompt = "You are an imaginative storyteller and creative writer. Use expressive imagery, rich vocabulary, and captivating pacing."
            )
        )

        fun provideFactory(
            streamChatUseCase: StreamChatUseCase,
            chatDao: ChatDao,
            modelDao: ModelDao,
            sessionId: String = DEFAULT_SESSION_ID,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
            getRamUsageBytes: () -> Long = { DEFAULT_RAM_BYTES },
            gpuBackendName: String = "Vulkan"
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudioViewModel(
                    streamChatUseCase = streamChatUseCase,
                    chatDao = chatDao,
                    modelDao = modelDao,
                    sessionId = sessionId,
                    ioDispatcher = ioDispatcher,
                    getRamUsageBytes = getRamUsageBytes,
                    gpuBackendName = gpuBackendName
                ) as T
            }
        }
    }
}
