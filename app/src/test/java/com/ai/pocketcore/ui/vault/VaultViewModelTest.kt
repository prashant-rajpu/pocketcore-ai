package com.ai.pocketcore.ui.vault

import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.data.downloader.GgufValidator
import com.ai.pocketcore.domain.engine.InferenceEngine
import com.ai.pocketcore.domain.usecase.LoadModelUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeDao: FakeModelDao
    private lateinit var fakeEngine: FakeEngine
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var modelsDir: File
    private lateinit var viewModel: VaultViewModel

    private val testAvailableRam = 7_000_000_000L
    private val testTotalRam = 12_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        modelsDir = tempFolder.newFolder("vault_models")
        fakeDao = FakeModelDao()
        fakeEngine = FakeEngine()
        loadModelUseCase = LoadModelUseCase(fakeDao, fakeEngine)

        viewModel = VaultViewModel(
            modelDao = fakeDao,
            loadModelUseCase = loadModelUseCase,
            modelsDir = modelsDir,
            ioDispatcher = testDispatcher,
            getAvailableRamBytes = { testAvailableRam },
            getTotalRamBytes = { testTotalRam }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun warnsWhenModelExceedsSafeRamThreshold() {
        val availableRamBytes = 7_000_000_000L
        val safeModelBytes = 4_500_000_000L
        val oversizedModelBytes = 8_000_000_000L

        assertFalse(VaultViewModel.isMemoryRisky(safeModelBytes, availableRamBytes))
        assertTrue(VaultViewModel.isMemoryRisky(oversizedModelBytes, availableRamBytes))
    }

    @Test
    fun extractQuantizationExtractsCommonGgufFormats() {
        assertEquals("Q4_K_M", VaultViewModel.extractQuantization("qwen2.5-7b-instruct-q4_k_m.gguf"))
        assertEquals("Q8_0", VaultViewModel.extractQuantization("Llama-3.2-3B-Q8_0.gguf"))
        assertEquals("IQ3_XXS", VaultViewModel.extractQuantization("deepseek-r1-iq3_xxs.gguf"))
        assertEquals("FP16", VaultViewModel.extractQuantization("gemma-2-2b-fp16.gguf"))
        assertEquals("BF16", VaultViewModel.extractQuantization("model-bf16.gguf"))
        assertEquals("CUSTOM", VaultViewModel.extractQuantization("my_custom_weights.gguf"))
    }

    @Test
    fun initialStateObservesDaoAndCalculatesStorage() {
        val model = ModelEntity(
            id = "m1",
            repoId = "meta-llama/Llama-3.2-3B",
            fileName = "llama-3.2-3b.gguf",
            localPath = File(modelsDir, "llama-3.2-3b.gguf").absolutePath,
            fileSizeBytes = 3_000_000_000L,
            quantization = "Q4_K_M",
            isActive = true
        )
        fakeDao.modelsFlow.value = listOf(model)

        assertEquals(1, viewModel.models.value.size)
        assertEquals("m1", viewModel.activeModel.value?.id)
        assertEquals(3_000_000_000L, viewModel.storageBreakdown.value.modelsBytes)
        assertEquals(testAvailableRam, viewModel.availableRamBytes.value)
        assertEquals(testTotalRam, viewModel.totalRamBytes.value)
    }

    @Test
    fun loadModelSafelyLoadsModelIntoActiveMemory() = runBlocking {
        val model = ModelEntity(
            id = "qwen-7b",
            repoId = "Qwen/Qwen2.5-7B",
            fileName = "qwen.gguf",
            localPath = File(modelsDir, "qwen.gguf").absolutePath,
            fileSizeBytes = 4_500_000_000L,
            quantization = "Q4_K_M",
            isActive = false
        )
        fakeDao.modelsFlow.value = listOf(model)

        viewModel.loadModel("qwen-7b", force = false)

        assertEquals("qwen-7b", fakeDao.activeModelId)
        assertNull(viewModel.errorMessage.value)
        assertNull(viewModel.loadingModelId.value)
    }

    @Test
    fun loadRiskyModelWithoutForceFailsWithInsufficientRamError() = runBlocking {
        val oversizedModel = ModelEntity(
            id = "oversized-14b",
            repoId = "Qwen/Qwen2.5-14B",
            fileName = "qwen-14b.gguf",
            localPath = File(modelsDir, "qwen-14b.gguf").absolutePath,
            fileSizeBytes = 8_000_000_000L,
            quantization = "Q4_K_M",
            isActive = false
        )
        fakeDao.modelsFlow.value = listOf(oversizedModel)

        viewModel.loadModel("oversized-14b", force = false)

        assertNotNull(viewModel.errorMessage.value)
        assertTrue(viewModel.errorMessage.value!!.contains("Insufficient RAM"))
        assertNull(fakeDao.activeModelId)
    }

    @Test
    fun loadRiskyModelWithForceBypassesSafetyHeadroom() = runBlocking {
        val oversizedModel = ModelEntity(
            id = "oversized-14b",
            repoId = "Qwen/Qwen2.5-14B",
            fileName = "qwen-14b.gguf",
            localPath = File(modelsDir, "qwen-14b.gguf").absolutePath,
            fileSizeBytes = 8_000_000_000L,
            quantization = "Q4_K_M",
            isActive = false
        )
        fakeDao.modelsFlow.value = listOf(oversizedModel)

        viewModel.loadModel("oversized-14b", force = true)

        assertEquals("oversized-14b", fakeDao.activeModelId)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun unloadActiveModelClearsActiveInUseCaseAndDao() = runBlocking {
        fakeDao.activeModelId = "active-1"
        fakeEngine.isLoaded = true

        viewModel.unloadActiveModel()

        assertNull(fakeDao.activeModelId)
        assertFalse(fakeEngine.isLoaded)
        assertEquals(1, fakeEngine.unloadCount)
    }

    @Test
    fun deleteModelDeletesFileFromDiskAndDao() = runBlocking {
        val modelFile = File(modelsDir, "delete_me.gguf")
        modelFile.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        assertTrue(modelFile.exists())

        val model = ModelEntity(
            id = "del-1",
            repoId = "local/delete_me",
            fileName = "delete_me.gguf",
            localPath = modelFile.absolutePath,
            fileSizeBytes = modelFile.length(),
            quantization = "CUSTOM",
            isActive = false
        )
        fakeDao.modelsFlow.value = listOf(model)

        viewModel.deleteModel(model)

        assertFalse(modelFile.exists())
        assertEquals(listOf("del-1"), fakeDao.deletedModelIds)
    }

    @Test
    fun deleteActiveModelUnloadsModelBeforeDeleting() = runBlocking {
        val modelFile = File(modelsDir, "active_delete.gguf")
        modelFile.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        fakeDao.activeModelId = "active-del"
        fakeEngine.isLoaded = true

        val model = ModelEntity(
            id = "active-del",
            repoId = "local/active_delete",
            fileName = "active_delete.gguf",
            localPath = modelFile.absolutePath,
            fileSizeBytes = modelFile.length(),
            quantization = "CUSTOM",
            isActive = true
        )
        fakeDao.modelsFlow.value = listOf(model)

        viewModel.deleteModel(model)

        assertFalse(modelFile.exists())
        assertNull(fakeDao.activeModelId)
        assertFalse(fakeEngine.isLoaded)
        assertEquals(listOf("active-del"), fakeDao.deletedModelIds)
    }

    @Test
    fun importModelFileValidatesGgufHeaderAndInsertsEntity() = runBlocking {
        val externalFolder = tempFolder.newFolder("external")
        val validFile = File(externalFolder, "qwen2.5-7b-q4_k_m.gguf")
        // Write valid GGUF header (magic 0x46554747 + version 3)
        validFile.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00, 0x01, 0x02))

        val result = viewModel.importModelFile(validFile)

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals("qwen2.5-7b-q4_k_m.gguf", entity?.fileName)
        assertEquals("Q4_K_M", entity?.quantization)

        val destFile = File(modelsDir, "qwen2.5-7b-q4_k_m.gguf")
        assertTrue(destFile.exists())
        assertTrue(GgufValidator.isValidGgufHeader(destFile))
        assertTrue(fakeDao.modelsFlow.value.any { it.fileName == "qwen2.5-7b-q4_k_m.gguf" })
    }

    @Test
    fun importCorruptFileFailsAndDoesNotInsertEntity() = runBlocking {
        val externalFolder = tempFolder.newFolder("external_bad")
        val corruptFile = File(externalFolder, "corrupt.gguf")
        corruptFile.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        val result = viewModel.importModelFile(corruptFile)

        assertTrue(result.isFailure)
        assertNotNull(viewModel.errorMessage.value)
        assertFalse(fakeDao.modelsFlow.value.any { it.fileName == "corrupt.gguf" })
    }

    @Test
    fun importModelFromStreamValidatesHeaderAndSavesEntity() = runBlocking {
        val validHeaderBytes = byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00, 0x10, 0x20)
        val stream = ByteArrayInputStream(validHeaderBytes)

        val result = viewModel.importModelFromStream("stream_model_q8_0.gguf", stream)

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        assertEquals("stream_model_q8_0.gguf", entity?.fileName)
        assertEquals("Q8_0", entity?.quantization)

        val file = File(modelsDir, "stream_model_q8_0.gguf")
        assertTrue(file.exists())
        assertTrue(fakeDao.modelsFlow.value.any { it.fileName == "stream_model_q8_0.gguf" })
    }

    @Test
    fun storageBreakdownCalculatesAccurateRatiosAndFormatting() {
        val breakdown = StorageBreakdown(
            modelsBytes = 25_000_000_000L,
            otherAppsBytes = 75_000_000_000L,
            freeBytes = 100_000_000_000L,
            totalBytes = 200_000_000_000L
        )

        assertEquals(0.125f, breakdown.modelsRatio, 0.001f)
        assertEquals(0.375f, breakdown.otherAppsRatio, 0.001f)
        assertEquals(0.500f, breakdown.freeRatio, 0.001f)

        assertEquals("23.28 GB", breakdown.formattedModelsSize())
        assertEquals("69.85 GB", breakdown.formattedOtherAppsSize())
        assertEquals("93.13 GB", breakdown.formattedFreeSize())
        assertEquals("186.26 GB", breakdown.formattedTotalSize())
    }

    @Test
    fun clearErrorResetsErrorMessage() {
        viewModel.loadModel("non-existent-id")
        assertNotNull(viewModel.errorMessage.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }
}

// Test Doubles
private class FakeModelDao : ModelDao {
    val modelsFlow = MutableStateFlow<List<ModelEntity>>(emptyList())
    var activeModelId: String? = null
    val deletedModelIds = mutableListOf<String>()

    override fun getAllModels(): Flow<List<ModelEntity>> = modelsFlow

    override fun getActiveModel(): Flow<ModelEntity?> = modelsFlow.map { list ->
        list.find { it.isActive || it.id == activeModelId }
    }

    override suspend fun getModelById(id: String): ModelEntity? {
        return modelsFlow.value.find { it.id == id }
    }

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
        activeModelId = modelId
        modelsFlow.value = modelsFlow.value.map {
            it.copy(isActive = it.id == modelId)
        }
    }

    override suspend fun clearActiveModel() {
        activeModelId = null
        modelsFlow.value = modelsFlow.value.map {
            it.copy(isActive = false)
        }
    }

    override suspend fun deleteModelById(id: String) {
        deletedModelIds.add(id)
        modelsFlow.value = modelsFlow.value.filter { it.id != id }
    }

    override suspend fun deleteAllModels() {
        modelsFlow.value = emptyList()
    }

    override fun getModelCount(): Flow<Int> = modelsFlow.map { it.size }
}

private class FakeEngine(override var isLoaded: Boolean = false) : InferenceEngine {
    var unloadCount = 0

    override suspend fun unload() {
        isLoaded = false
        unloadCount++
    }
}
