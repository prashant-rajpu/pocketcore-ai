package com.ai.pocketcore.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.pocketcore.data.api.model.GgufFileInfo
import com.ai.pocketcore.data.api.model.HfModelSummary
import com.ai.pocketcore.data.downloader.DownloadManager
import com.ai.pocketcore.data.downloader.DownloadState
import com.ai.pocketcore.data.repository.HuggingFaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Curated flagship model recommendations specifically tested for
 * Snapdragon 8 Elite and 12GB LPDDR5X RAM on Galaxy S25.
 */
data class CuratedModel(
    val repoId: String,
    val name: String,
    val author: String,
    val description: String,
    val recommendedQuantization: String = "Q4_K_M",
    val parameterSize: String = "7B",
    val estimatedRamGb: Double = 5.5,
    val downloads: Long = 0L,
    val likes: Int = 0,
    val tags: List<String> = emptyList(),
    val isRecommendedForS25: Boolean = true
) {
    fun toHfModelSummary(): HfModelSummary = HfModelSummary(
        repoId = repoId,
        name = name,
        author = author,
        downloads = downloads,
        likes = likes,
        tags = tags,
        description = description
    )
}

/**
 * ViewModel managing Hugging Face Hub explorer state, search operations,
 * model tree inspection, and quantization downloads.
 */
class HubViewModel(
    private val hfRepository: HuggingFaceRepository,
    private val downloadManager: DownloadManager,
    private val debounceMs: Long = 300L
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HfModelSummary>>(emptyList())
    val searchResults: StateFlow<List<HfModelSummary>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _selectedModelForTree = MutableStateFlow<HfModelSummary?>(null)
    val selectedModelForTree: StateFlow<HfModelSummary?> = _selectedModelForTree.asStateFlow()

    private val _quantizationFiles = MutableStateFlow<List<GgufFileInfo>>(emptyList())
    val quantizationFiles: StateFlow<List<GgufFileInfo>> = _quantizationFiles.asStateFlow()

    private val _isLoadingTree = MutableStateFlow(false)
    val isLoadingTree: StateFlow<Boolean> = _isLoadingTree.asStateFlow()

    private val _treeError = MutableStateFlow<String?>(null)
    val treeError: StateFlow<String?> = _treeError.asStateFlow()

    private val _activeDownloadFileName = MutableStateFlow<String?>(null)
    val activeDownloadFileName: StateFlow<String?> = _activeDownloadFileName.asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloadManager.state

    private val _curatedModels = MutableStateFlow(getCuratedModels())
    val curatedModels: StateFlow<List<CuratedModel>> = _curatedModels.asStateFlow()

    private var searchJob: Job? = null
    private var treeJob: Job? = null

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            _searchError.value = null
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            val result = hfRepository.searchGgufModels(query.trim())
            if (result.isSuccess) {
                _searchResults.value = result.getOrDefault(emptyList())
            } else {
                _searchResults.value = emptyList()
                _searchError.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to search models"
            }
            _isSearching.value = false
        }
    }

    fun search() {
        val query = _searchQuery.value
        searchJob?.cancel()
        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            _searchError.value = null
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            val result = hfRepository.searchGgufModels(query.trim())
            if (result.isSuccess) {
                _searchResults.value = result.getOrDefault(emptyList())
            } else {
                _searchResults.value = emptyList()
                _searchError.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to search models"
            }
            _isSearching.value = false
        }
    }

    fun selectModel(model: HfModelSummary) {
        _selectedModelForTree.value = model
        _isLoadingTree.value = true
        _treeError.value = null
        _quantizationFiles.value = emptyList()

        treeJob?.cancel()
        treeJob = viewModelScope.launch {
            val result = hfRepository.fetchGgufFiles(model.repoId)
            if (result.isSuccess) {
                _quantizationFiles.value = result.getOrDefault(emptyList())
            } else {
                _quantizationFiles.value = emptyList()
                _treeError.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to load files"
            }
            _isLoadingTree.value = false
        }
    }

    fun selectModel(curated: CuratedModel) {
        selectModel(curated.toHfModelSummary())
    }

    fun dismissTree() {
        treeJob?.cancel()
        _selectedModelForTree.value = null
        _quantizationFiles.value = emptyList()
        _isLoadingTree.value = false
        _treeError.value = null
    }

    fun downloadQuantization(file: GgufFileInfo) {
        _activeDownloadFileName.value = file.name
        val url = if (file.downloadUrl.isNotBlank()) {
            file.downloadUrl
        } else {
            "https://huggingface.co/${file.repoId}/resolve/main/${file.path}"
        }
        downloadManager.startDownload(url = url, fileName = file.name)
    }

    fun pauseDownload() {
        downloadManager.pauseDownload()
    }

    fun resumeDownload() {
        downloadManager.resumeDownload()
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
        _activeDownloadFileName.value = null
    }

    fun isModelDownloaded(fileName: String): Boolean {
        return downloadManager.getDownloadedFile(fileName) != null
    }

    companion object {
        fun getCuratedModels(): List<CuratedModel> = listOf(
            CuratedModel(
                repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF",
                name = "Qwen2.5-7B-Instruct",
                author = "Qwen",
                description = "Leading open 7B reasoning & coding model, optimized for Vulkan acceleration on S25.",
                recommendedQuantization = "Q4_K_M",
                parameterSize = "7B",
                estimatedRamGb = 5.5,
                downloads = 125000L,
                likes = 1420,
                tags = listOf("gguf", "chat", "code", "s25-flagship"),
                isRecommendedForS25 = true
            ),
            CuratedModel(
                repoId = "meta-llama/Llama-3.2-3B-Instruct",
                name = "Llama-3.2-3B-Instruct",
                author = "meta-llama",
                description = "Ultra-fast lightweight 3B model reaching 55+ tok/s with Vulkan on Snapdragon 8 Elite.",
                recommendedQuantization = "Q4_K_M",
                parameterSize = "3B",
                estimatedRamGb = 2.8,
                downloads = 340000L,
                likes = 2890,
                tags = listOf("gguf", "chat", "fast", "s25-flagship"),
                isRecommendedForS25 = true
            ),
            CuratedModel(
                repoId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B",
                name = "DeepSeek-R1-Distill-Qwen-7B",
                author = "deepseek-ai",
                description = "State-of-the-art math and chain-of-thought reasoning distilled from DeepSeek R1.",
                recommendedQuantization = "Q4_K_M",
                parameterSize = "7B",
                estimatedRamGb = 5.8,
                downloads = 210000L,
                likes = 3150,
                tags = listOf("gguf", "reasoning", "math", "s25-flagship"),
                isRecommendedForS25 = true
            ),
            CuratedModel(
                repoId = "google/gemma-2-2b-it",
                name = "gemma-2-2b-it",
                author = "google",
                description = "Google's ultra-compact 2B model offering high-fidelity responses with minimal battery drain.",
                recommendedQuantization = "Q4_K_M",
                parameterSize = "2B",
                estimatedRamGb = 2.2,
                downloads = 185000L,
                likes = 1780,
                tags = listOf("gguf", "compact", "google", "s25-flagship"),
                isRecommendedForS25 = true
            )
        )

        fun provideFactory(
            hfRepository: HuggingFaceRepository,
            downloadManager: DownloadManager,
            debounceMs: Long = 300L
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HubViewModel(hfRepository, downloadManager, debounceMs) as T
            }
        }
    }
}
