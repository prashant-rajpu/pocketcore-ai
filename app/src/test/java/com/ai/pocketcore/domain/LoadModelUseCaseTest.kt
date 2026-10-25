package com.ai.pocketcore.domain

import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.domain.engine.InferenceEngine
import com.ai.pocketcore.domain.model.InsufficientMemoryException
import com.ai.pocketcore.domain.usecase.LoadModelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoadModelUseCaseTest {

    private class FakeModelDao(var storedModel: ModelEntity? = null) : ModelDao {
        var activeModelId: String? = null
        var clearActiveModelCalled = false

        override fun getAllModels(): Flow<List<ModelEntity>> = flowOf(listOfNotNull(storedModel))
        override fun getActiveModel(): Flow<ModelEntity?> = flowOf(storedModel?.takeIf { it.id == activeModelId })
        override suspend fun getModelById(id: String): ModelEntity? = storedModel?.takeIf { it.id == id }
        override suspend fun insertModel(model: ModelEntity) { storedModel = model }
        override suspend fun insertModels(models: List<ModelEntity>) {}
        override suspend fun updateModel(model: ModelEntity) { storedModel = model }
        override suspend fun setActiveModel(modelId: String) { activeModelId = modelId }
        override suspend fun clearActiveModel() {
            activeModelId = null
            clearActiveModelCalled = true
        }
        override suspend fun deleteModelById(id: String) {}
        override suspend fun deleteAllModels() {}
        override fun getModelCount(): Flow<Int> = flowOf(if (storedModel != null) 1 else 0)
    }

    private class FakeEngine(override var isLoaded: Boolean = false) : InferenceEngine {
        var unloadCount = 0
        override suspend fun unload() {
            isLoaded = false
            unloadCount++
        }
    }

    @Test
    fun activatesModelWhenRamHeadroomIsSafe() = runBlocking {
        val model = ModelEntity(
            id = "qwen-7b",
            repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF",
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            localPath = "/data/local/models/qwen.gguf",
            fileSizeBytes = 4_500_000_000L,
            quantization = "Q4_K_M"
        )
        val dao = FakeModelDao(model)
        val engine = FakeEngine(isLoaded = true)
        val useCase = LoadModelUseCase(dao, engine)

        val availableRam = 7_000_000_000L // 4.5 GB < 7.0 * 0.85 (5.95 GB)
        val loaded = useCase("qwen-7b", availableRam)

        assertTrue(loaded.isActive)
        assertEquals("qwen-7b", dao.activeModelId)
        assertEquals(1, engine.unloadCount)
    }

    @Test
    fun throwsInsufficientMemoryExceptionWhenModelExceeds85Percent() = runBlocking {
        val model = ModelEntity(
            id = "oversized-14b",
            repoId = "Qwen/Qwen2.5-14B-Instruct-GGUF",
            fileName = "qwen2.5-14b.gguf",
            localPath = "/data/local/models/qwen14b.gguf",
            fileSizeBytes = 8_000_000_000L,
            quantization = "Q4_K_M"
        )
        val dao = FakeModelDao(model)
        val useCase = LoadModelUseCase(dao)

        val availableRam = 7_000_000_000L // 8.0 GB > 7.0 * 0.85 (5.95 GB)
        try {
            useCase(model, availableRam)
            fail("Expected InsufficientMemoryException was not thrown")
        } catch (e: InsufficientMemoryException) {
            assertEquals(8_000_000_000L, e.requiredBytes)
            assertEquals(7_000_000_000L, e.availableBytes)
        }
        assertEquals(null, dao.activeModelId)
    }

    @Test
    fun isMemoryRiskyEvaluates85PercentThresholdAccurately() {
        val availableRamBytes = 7_000_000_000L
        val safeModelBytes = 4_500_000_000L
        val oversizedModelBytes = 8_000_000_000L

        assertFalse(LoadModelUseCase.isMemoryRisky(safeModelBytes, availableRamBytes))
        assertTrue(LoadModelUseCase.isMemoryRisky(oversizedModelBytes, availableRamBytes))
    }

    @Test
    fun unloadActiveModelUnloadsEngineAndClearsDao() = runBlocking {
        val dao = FakeModelDao()
        dao.activeModelId = "active-model"
        val engine = FakeEngine(isLoaded = true)
        val useCase = LoadModelUseCase(dao, engine)

        useCase.unloadActiveModel()

        assertEquals(1, engine.unloadCount)
        assertFalse(engine.isLoaded)
        assertTrue(dao.clearActiveModelCalled)
        assertEquals(null, dao.activeModelId)
    }
}
