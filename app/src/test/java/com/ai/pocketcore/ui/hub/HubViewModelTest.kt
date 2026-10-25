package com.ai.pocketcore.ui.hub

import com.ai.pocketcore.data.api.model.GgufFileInfo
import com.ai.pocketcore.data.api.model.HfFileTreeItem
import com.ai.pocketcore.data.api.model.HfModelSummary
import com.ai.pocketcore.data.downloader.DownloadManager
import com.ai.pocketcore.data.downloader.DownloadState
import com.ai.pocketcore.data.repository.HuggingFaceRepository
import com.ai.pocketcore.ui.hub.components.calculateEstimatedRam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepo: FakeHuggingFaceRepository
    private lateinit var fakeDownloadManager: FakeDownloadManager
    private lateinit var viewModel: HubViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeHuggingFaceRepository()
        fakeDownloadManager = FakeDownloadManager()
        viewModel = HubViewModel(fakeRepo, fakeDownloadManager, debounceMs = 0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialCuratedModelsContainsFlagshipRecommendations() {
        val curated = HubViewModel.getCuratedModels()
        assertTrue(curated.any { it.repoId.contains("Qwen2.5-7B") })
        assertTrue(curated.any { it.repoId.contains("Llama-3.2-3B") })
        assertTrue(curated.any { it.repoId.contains("DeepSeek-R1-Distill") })
        assertTrue(curated.any { it.repoId.contains("gemma-2-2b") })
        assertEquals(4, curated.size)
    }

    @Test
    fun curatedModelToHfModelSummaryConversionPreservesMetadata() {
        val curated = HubViewModel.getCuratedModels().first { it.repoId.contains("Qwen2.5-7B") }
        val summary = curated.toHfModelSummary()

        assertEquals(curated.repoId, summary.repoId)
        assertEquals(curated.name, summary.name)
        assertEquals(curated.author, summary.author)
        assertEquals(curated.description, summary.description)
        assertEquals(curated.downloads, summary.downloads)
        assertEquals(curated.likes, summary.likes)
    }

    @Test
    fun onQueryChangedUpdatesSearchQueryAndExecutesSearch() {
        val dummyModels = listOf(
            HfModelSummary(
                repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF",
                name = "Qwen2.5-7B-Instruct-GGUF",
                author = "Qwen",
                downloads = 50000L,
                likes = 800
            )
        )
        fakeRepo.searchResult = Result.success(dummyModels)

        viewModel.onQueryChanged("qwen")

        assertEquals("qwen", viewModel.searchQuery.value)
        assertEquals("qwen", fakeRepo.lastSearchQuery)
        assertFalse(viewModel.isSearching.value)
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("Qwen/Qwen2.5-7B-Instruct-GGUF", viewModel.searchResults.value[0].repoId)
        assertNull(viewModel.searchError.value)
    }

    @Test
    fun blankQueryClearsSearchResultsAndStopsSearching() {
        fakeRepo.searchResult = Result.success(
            listOf(
                HfModelSummary(
                    repoId = "meta-llama/Llama-3.2-3B-Instruct",
                    name = "Llama-3.2-3B-Instruct",
                    author = "meta-llama"
                )
            )
        )
        viewModel.onQueryChanged("llama")
        assertEquals(1, viewModel.searchResults.value.size)

        // Clear query
        viewModel.onQueryChanged("   ")
        assertEquals("   ", viewModel.searchQuery.value)
        assertTrue(viewModel.searchResults.value.isEmpty())
        assertFalse(viewModel.isSearching.value)
        assertNull(viewModel.searchError.value)
    }

    @Test
    fun searchErrorGracefullyUpdatesSearchErrorState() {
        fakeRepo.searchResult = Result.failure(RuntimeException("Hugging Face API Timeout"))

        viewModel.onQueryChanged("error-model")

        assertFalse(viewModel.isSearching.value)
        assertTrue(viewModel.searchResults.value.isEmpty())
        assertEquals("Hugging Face API Timeout", viewModel.searchError.value)
    }

    @Test
    fun selectModelLoadsQuantizationTreeSuccessfully() {
        val model = HfModelSummary(
            repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF",
            name = "Qwen2.5-7B-Instruct-GGUF",
            author = "Qwen"
        )
        val dummyFiles = listOf(
            GgufFileInfo(
                name = "qwen2.5-7b-instruct-q4_k_m.gguf",
                path = "qwen2.5-7b-instruct-q4_k_m.gguf",
                sizeBytes = 4680000000L,
                quantization = "Q4_K_M",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
                repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF"
            )
        )
        fakeRepo.treeResult = Result.success(dummyFiles)

        viewModel.selectModel(model)

        assertEquals(model, viewModel.selectedModelForTree.value)
        assertEquals("Qwen/Qwen2.5-7B-Instruct-GGUF", fakeRepo.lastTreeRepoId)
        assertFalse(viewModel.isLoadingTree.value)
        assertEquals(1, viewModel.quantizationFiles.value.size)
        assertEquals("Q4_K_M", viewModel.quantizationFiles.value[0].quantization)
        assertNull(viewModel.treeError.value)
    }

    @Test
    fun selectCuratedModelOverloadLoadsTree() {
        val curated = HubViewModel.getCuratedModels().first()
        fakeRepo.treeResult = Result.success(emptyList())

        viewModel.selectModel(curated)

        assertNotNull(viewModel.selectedModelForTree.value)
        assertEquals(curated.repoId, viewModel.selectedModelForTree.value?.repoId)
        assertEquals(curated.repoId, fakeRepo.lastTreeRepoId)
    }

    @Test
    fun treeFetchErrorHandlesGracefully() {
        val model = HfModelSummary(
            repoId = "invalid/repo",
            name = "invalid",
            author = "none"
        )
        fakeRepo.treeResult = Result.failure(RuntimeException("Repo not found"))

        viewModel.selectModel(model)

        assertFalse(viewModel.isLoadingTree.value)
        assertTrue(viewModel.quantizationFiles.value.isEmpty())
        assertEquals("Repo not found", viewModel.treeError.value)
    }

    @Test
    fun dismissTreeClearsSelectedModelAndQuantizations() {
        val model = HfModelSummary(repoId = "test/repo", name = "test", author = "author")
        fakeRepo.treeResult = Result.success(
            listOf(
                GgufFileInfo(
                    name = "model.gguf",
                    sizeBytes = 1000L,
                    quantization = "Q4_0",
                    downloadUrl = "http://example.com/model.gguf",
                    repoId = "test/repo"
                )
            )
        )

        viewModel.selectModel(model)
        assertNotNull(viewModel.selectedModelForTree.value)
        assertFalse(viewModel.quantizationFiles.value.isEmpty())

        viewModel.dismissTree()

        assertNull(viewModel.selectedModelForTree.value)
        assertTrue(viewModel.quantizationFiles.value.isEmpty())
        assertFalse(viewModel.isLoadingTree.value)
        assertNull(viewModel.treeError.value)
    }

    @Test
    fun downloadQuantizationTriggersDownloadManagerStart() {
        val file = GgufFileInfo(
            name = "qwen2.5-7b-instruct-q4_k_m.gguf",
            path = "qwen2.5-7b-instruct-q4_k_m.gguf",
            sizeBytes = 4680000000L,
            quantization = "Q4_K_M",
            downloadUrl = "https://huggingface.co/Qwen/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF"
        )

        viewModel.downloadQuantization(file)

        assertEquals(file.name, viewModel.activeDownloadFileName.value)
        assertEquals(file.downloadUrl, fakeDownloadManager.lastDownloadUrl)
        assertEquals(file.name, fakeDownloadManager.lastFileName)
    }

    @Test
    fun downloadManagerControlsDelegateCorrectly() {
        viewModel.pauseDownload()
        assertTrue(fakeDownloadManager.pauseCalled)

        viewModel.resumeDownload()
        assertTrue(fakeDownloadManager.resumeCalled)

        viewModel.cancelDownload()
        assertTrue(fakeDownloadManager.cancelCalled)
        assertNull(viewModel.activeDownloadFileName.value)
    }

    @Test
    fun isModelDownloadedReturnsStatusFromDownloadManager() {
        fakeDownloadManager.downloadedFiles.add("ready.gguf")

        assertTrue(viewModel.isModelDownloaded("ready.gguf"))
        assertFalse(viewModel.isModelDownloaded("missing.gguf"))
    }

    @Test
    fun calculateEstimatedRamFormatsCorrectly() {
        val fourGbBytes = 4L * 1024 * 1024 * 1024
        val estimated = calculateEstimatedRam(fourGbBytes)
        // 4GB * 1.2 = 4.8 GB RAM
        assertTrue(estimated.contains("4.8 GB RAM"))

        val zeroBytes = 0L
        assertEquals("~4.0 GB RAM", calculateEstimatedRam(zeroBytes))
    }

    @Test
    fun searchDebounceDelaysRepositoryCallUntilIntervalElapses() = kotlinx.coroutines.test.runTest {
        val debouncedViewModel = HubViewModel(fakeRepo, fakeDownloadManager, debounceMs = 300L)
        debouncedViewModel.onQueryChanged("q")
        debouncedViewModel.onQueryChanged("qw")
        debouncedViewModel.onQueryChanged("qwen")

        // Before advancing time, repo should not have been called yet
        assertNull(fakeRepo.lastSearchQuery)

        // Advance virtual time by 350ms
        testScheduler.advanceTimeBy(350L)

        // Now the repository search should be executed once with the final query
        assertEquals("qwen", fakeRepo.lastSearchQuery)
    }
}

// Test Doubles
private class FakeHuggingFaceRepository(
    var searchResult: Result<List<HfModelSummary>> = Result.success(emptyList()),
    var treeResult: Result<List<GgufFileInfo>> = Result.success(emptyList())
) : HuggingFaceRepository {
    var lastSearchQuery: String? = null
    var lastTreeRepoId: String? = null

    override suspend fun searchGgufModels(query: String): Result<List<HfModelSummary>> {
        lastSearchQuery = query
        return searchResult
    }

    override suspend fun fetchGgufFiles(repoId: String): Result<List<GgufFileInfo>> {
        lastTreeRepoId = repoId
        return treeResult
    }

    override fun filterGgufFiles(files: List<HfFileTreeItem>, repoId: String): List<GgufFileInfo> {
        return emptyList()
    }
}

private class FakeDownloadManager(
    initialState: DownloadState = DownloadState.Idle
) : DownloadManager {
    private val _state = MutableStateFlow<DownloadState>(initialState)
    override val state: StateFlow<DownloadState> = _state

    var lastDownloadUrl: String? = null
    var lastFileName: String? = null
    var pauseCalled = false
    var resumeCalled = false
    var cancelCalled = false
    val downloadedFiles = mutableSetOf<String>()

    override fun startDownload(url: String, fileName: String, destinationDir: File?, authToken: String?) {
        lastDownloadUrl = url
        lastFileName = fileName
        _state.value = DownloadState.Downloading(progress = 0.1f)
    }

    override fun pauseDownload() {
        pauseCalled = true
        _state.value = DownloadState.Paused(progress = 0.5f)
    }

    override fun resumeDownload() {
        resumeCalled = true
        _state.value = DownloadState.Downloading(progress = 0.5f)
    }

    override fun cancelDownload() {
        cancelCalled = true
        _state.value = DownloadState.Idle
    }

    override fun getDownloadedFile(fileName: String): File? {
        return if (downloadedFiles.contains(fileName)) File("/tmp/$fileName") else null
    }

    override fun getModelsDirectory(): File = File("/tmp/models")
}
