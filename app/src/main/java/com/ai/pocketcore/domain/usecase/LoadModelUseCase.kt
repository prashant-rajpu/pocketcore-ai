package com.ai.pocketcore.domain.usecase

import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.domain.engine.InferenceEngine
import com.ai.pocketcore.domain.model.InsufficientMemoryException

/**
 * Use case for loading a model into active memory.
 * Validates RAM headroom safety against physical memory to avoid OOM terminations,
 * unloads any previously loaded engine, and marks the selected model active in the database.
 */
class LoadModelUseCase(
    private val modelDao: ModelDao,
    private val engine: InferenceEngine? = null
) {

    /**
     * Loads the model specified by [modelId].
     *
     * @param modelId Unique identifier of the model entity.
     * @param availableRamBytes Current available system RAM in bytes, or null to bypass check.
     * @return The activated [ModelEntity].
     * @throws InsufficientMemoryException if the model footprint exceeds 85% of available RAM.
     * @throws IllegalArgumentException if the model ID cannot be found.
     */
    suspend operator fun invoke(
        modelId: String,
        availableRamBytes: Long? = null
    ): ModelEntity {
        val model = modelDao.getModelById(modelId)
            ?: throw IllegalArgumentException("Model not found in vault: $modelId")
        return loadModel(model, availableRamBytes)
    }

    /**
     * Loads the provided [model].
     *
     * @param model The model entity to activate.
     * @param availableRamBytes Current available system RAM in bytes, or null to bypass check.
     * @return The activated [ModelEntity].
     * @throws InsufficientMemoryException if the model footprint exceeds 85% of available RAM.
     */
    suspend operator fun invoke(
        model: ModelEntity,
        availableRamBytes: Long? = null
    ): ModelEntity {
        return loadModel(model, availableRamBytes)
    }

    private suspend fun loadModel(
        model: ModelEntity,
        availableRamBytes: Long?
    ): ModelEntity {
        if (availableRamBytes != null && isMemoryRisky(model.fileSizeBytes, availableRamBytes)) {
            throw InsufficientMemoryException(
                requiredBytes = model.fileSizeBytes,
                availableBytes = availableRamBytes
            )
        }

        if (engine != null && engine.isLoaded) {
            engine.unload()
        }

        modelDao.setActiveModel(model.id)
        return model.copy(isActive = true)
    }

    /**
     * Unloads the currently active model from the engine and clears the active flag in the database.
     */
    suspend fun unloadActiveModel() {
        if (engine != null && engine.isLoaded) {
            engine.unload()
        }
        modelDao.clearActiveModel()
    }

    companion object {
        /**
         * Maximum proportion of available RAM a model may occupy before triggering an OOM risk warning (85%).
         */
        const val RAM_SAFETY_THRESHOLD = 0.85

        /**
         * Checks whether loading a model with [modelSizeBytes] poses an out-of-memory risk
         * given [availableRamBytes].
         */
        fun isMemoryRisky(modelSizeBytes: Long, availableRamBytes: Long): Boolean {
            return modelSizeBytes > (availableRamBytes * RAM_SAFETY_THRESHOLD)
        }
    }
}
