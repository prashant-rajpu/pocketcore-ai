package com.ai.pocketcore.ui.vault

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.pocketcore.data.db.dao.ModelDao
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.data.downloader.GgufValidator
import com.ai.pocketcore.domain.model.InsufficientMemoryException
import com.ai.pocketcore.domain.usecase.LoadModelUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * Breakdown of device internal storage consumption:
 * models stored in vault, other applications & system data, and remaining free space.
 */
data class StorageBreakdown(
    val modelsBytes: Long = 0L,
    val otherAppsBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    val modelsRatio: Float
        get() = if (totalBytes > 0) (modelsBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val otherAppsRatio: Float
        get() = if (totalBytes > 0) (otherAppsBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val freeRatio: Float
        get() = if (totalBytes > 0) (freeBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    fun formattedModelsSize(): String = formatStorageSize(modelsBytes)
    fun formattedOtherAppsSize(): String = formatStorageSize(otherAppsBytes)
    fun formattedFreeSize(): String = formatStorageSize(freeBytes)
    fun formattedTotalSize(): String = formatStorageSize(totalBytes)

    companion object {
        fun formatStorageSize(bytes: Long): String {
            if (bytes <= 0L) return "0.00 GB"
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }
}

/**
 * ViewModel managing local model storage inventory, memory headroom safety checks,
 * model activation/deactivation via [LoadModelUseCase], model file deletion,
 * and importing custom external `.gguf` files via Android Storage Access Framework (SAF).
 */
class VaultViewModel(
    private val modelDao: ModelDao,
    private val loadModelUseCase: LoadModelUseCase,
    private val modelsDir: File = File("/tmp/models"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val getAvailableRamBytes: () -> Long = { DEFAULT_AVAILABLE_RAM_BYTES },
    private val getTotalRamBytes: () -> Long = { DEFAULT_TOTAL_RAM_BYTES },
    private val customStorageCalculator: ((modelsTotalBytes: Long) -> StorageBreakdown)? = null
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelEntity>>(emptyList())
    val models: StateFlow<List<ModelEntity>> = _models.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelEntity?>(null)
    val activeModel: StateFlow<ModelEntity?> = _activeModel.asStateFlow()

    private val _availableRamBytes = MutableStateFlow(getAvailableRamBytes())
    val availableRamBytes: StateFlow<Long> = _availableRamBytes.asStateFlow()

    private val _totalRamBytes = MutableStateFlow(getTotalRamBytes())
    val totalRamBytes: StateFlow<Long> = _totalRamBytes.asStateFlow()

    private val _storageBreakdown = MutableStateFlow(calculateStorage(0L))
    val storageBreakdown: StateFlow<StorageBreakdown> = _storageBreakdown.asStateFlow()

    private val _loadingModelId = MutableStateFlow<String?>(null)
    val loadingModelId: StateFlow<String?> = _loadingModelId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        viewModelScope.launch(ioDispatcher) {
            modelDao.getAllModels().collect { list ->
                _models.value = list
                val totalModelBytes = list.sumOf { it.fileSizeBytes }
                _storageBreakdown.value = calculateStorage(totalModelBytes)
            }
        }

        viewModelScope.launch(ioDispatcher) {
            modelDao.getActiveModel().collect { active ->
                _activeModel.value = active
            }
        }
    }

    /**
     * Refreshes the current RAM statistics snapshot.
     */
    fun refreshMemoryStats() {
        _availableRamBytes.value = getAvailableRamBytes()
        _totalRamBytes.value = getTotalRamBytes()
    }

    /**
     * Safely loads the specified model into active memory.
     *
     * @param modelId Unique ID of the model to load.
     * @param force If true, bypasses the RAM safety headroom threshold check.
     */
    fun loadModel(modelId: String, force: Boolean = false) {
        viewModelScope.launch(ioDispatcher) {
            _loadingModelId.value = modelId
            _errorMessage.value = null
            try {
                val ramToCheck = if (force) null else _availableRamBytes.value
                loadModelUseCase(modelId, availableRamBytes = ramToCheck)
            } catch (e: InsufficientMemoryException) {
                val reqGb = String.format(Locale.US, "%.1f", e.requiredBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
                val availGb = String.format(Locale.US, "%.1f", e.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
                _errorMessage.value = "Insufficient RAM: Model needs ~${reqGb}GB which exceeds 85% of available RAM (${availGb}GB)."
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to load model"
            } finally {
                _loadingModelId.value = null
            }
        }
    }

    /**
     * Unloads the currently active model from memory and clears the active flag in the database.
     */
    fun unloadActiveModel() {
        viewModelScope.launch(ioDispatcher) {
            try {
                loadModelUseCase.unloadActiveModel()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to unload model"
            }
        }
    }

    /**
     * Deletes a model from local storage and removes its entity from the database.
     * If the model is currently active, it is unloaded first.
     */
    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch(ioDispatcher) {
            try {
                if (model.isActive) {
                    loadModelUseCase.unloadActiveModel()
                }

                // Delete file from disk
                val file = File(model.localPath)
                if (file.exists()) {
                    file.delete()
                }

                // Delete from Room
                modelDao.deleteModelById(model.id)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to delete model"
            }
        }
    }

    /**
     * Imports an external model file into the vault, validating its GGUF header before
     * committing to the database.
     */
    suspend fun importModelFile(sourceFile: File): Result<ModelEntity> = withContext(ioDispatcher) {
        _isImporting.value = true
        _errorMessage.value = null
        try {
            if (!sourceFile.exists() || !sourceFile.isFile) {
                val err = "Source file does not exist: ${sourceFile.absolutePath}"
                _errorMessage.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            if (!GgufValidator.isValidGgufHeader(sourceFile)) {
                val err = "Invalid GGUF file format: missing or corrupted GGUF magic header"
                _errorMessage.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val destFile = if (sourceFile.parentFile?.absolutePath == modelsDir.absolutePath) {
                sourceFile
            } else {
                val target = File(modelsDir, sourceFile.name)
                sourceFile.copyTo(target, overwrite = true)
                target
            }

            val entity = createModelEntity(destFile)
            modelDao.insertModel(entity)
            Result.success(entity)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Failed to import model file"
            _errorMessage.value = msg
            Result.failure(e)
        } finally {
            _isImporting.value = false
        }
    }

    /**
     * Imports a model from an [InputStream] (e.g. from Android ContentResolver / SAF).
     */
    suspend fun importModelFromStream(
        fileName: String,
        inputStream: InputStream,
        expectedSizeBytes: Long = -1L
    ): Result<ModelEntity> = withContext(ioDispatcher) {
        _isImporting.value = true
        _errorMessage.value = null
        val tempFile = File(modelsDir, "$fileName.tmp_${UUID.randomUUID()}")
        val destFile = File(modelsDir, fileName)

        try {
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }

            if (!GgufValidator.isValidGgufHeader(tempFile)) {
                tempFile.delete()
                val err = "Invalid GGUF file format: missing or corrupted GGUF magic header"
                _errorMessage.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            val entity = createModelEntity(destFile)
            modelDao.insertModel(entity)
            Result.success(entity)
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            val msg = e.localizedMessage ?: "Failed to import model from stream"
            _errorMessage.value = msg
            Result.failure(e)
        } finally {
            _isImporting.value = false
        }
    }

    /**
     * Android SAF entry point: imports a user-selected GGUF file URI using [ContentResolver].
     */
    fun importModelFromUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(ioDispatcher) {
            val fileName = queryDisplayName(uri, contentResolver) ?: "custom_${System.currentTimeMillis()}.gguf"
            val stream = try {
                contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                _errorMessage.value = "Cannot open selected file: ${e.localizedMessage}"
                null
            } ?: return@launch

            importModelFromStream(fileName, stream)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun createModelEntity(file: File): ModelEntity {
        val fileName = file.name
        val quant = extractQuantization(fileName)
        val repoName = file.nameWithoutExtension
        return ModelEntity(
            id = UUID.randomUUID().toString(),
            repoId = "local/$repoName",
            fileName = fileName,
            localPath = file.absolutePath,
            fileSizeBytes = file.length(),
            quantization = quant,
            contextLength = 4096,
            architecture = "llama",
            downloadedAt = System.currentTimeMillis(),
            isActive = false
        )
    }

    private fun calculateStorage(modelsBytes: Long): StorageBreakdown {
        if (customStorageCalculator != null) {
            return customStorageCalculator.invoke(modelsBytes)
        }
        val total = modelsDir.totalSpace.takeIf { it > 0 } ?: DEFAULT_TOTAL_STORAGE_BYTES
        val free = modelsDir.usableSpace.takeIf { it > 0 } ?: (total - modelsBytes).coerceAtLeast(0L)
        val other = (total - free - modelsBytes).coerceAtLeast(0L)
        return StorageBreakdown(
            modelsBytes = modelsBytes,
            otherAppsBytes = other,
            freeBytes = free,
            totalBytes = total
        )
    }

    private fun queryDisplayName(uri: Uri, contentResolver: ContentResolver): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment
    }

    companion object {
        const val RAM_SAFETY_THRESHOLD: Double = LoadModelUseCase.RAM_SAFETY_THRESHOLD
        const val DEFAULT_AVAILABLE_RAM_BYTES: Long = 7_000_000_000L   // ~7.0 GB
        const val DEFAULT_TOTAL_RAM_BYTES: Long = 12_000_000_000L       // 12.0 GB (Galaxy S25)
        const val DEFAULT_TOTAL_STORAGE_BYTES: Long = 256_000_000_000L // 256 GB

        /**
         * Checks whether loading a model with [modelSizeBytes] exceeds the safe RAM headroom
         * given [availableRamBytes].
         */
        fun isMemoryRisky(modelSizeBytes: Long, availableRamBytes: Long): Boolean {
            return LoadModelUseCase.isMemoryRisky(modelSizeBytes, availableRamBytes)
        }

        /**
         * Extracts common quantization indicators from a GGUF filename (e.g. Q4_K_M, Q8_0, FP16).
         */
        fun extractQuantization(fileName: String): String {
            val upper = fileName.uppercase(Locale.US)
            val regex = Regex("(Q[0-9]_[A-Z0-9_]+|IQ[0-9]_[A-Z0-9_]+|FP16|BF16|FP32)")
            val match = regex.find(upper)
            return match?.value ?: "CUSTOM"
        }

        fun provideFactory(
            modelDao: ModelDao,
            loadModelUseCase: LoadModelUseCase,
            modelsDir: File = File("/data/data/com.ai.pocketcore/files/models"),
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
            availableRamBytes: Long = DEFAULT_AVAILABLE_RAM_BYTES,
            totalRamBytes: Long = DEFAULT_TOTAL_RAM_BYTES
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    modelDao = modelDao,
                    loadModelUseCase = loadModelUseCase,
                    modelsDir = modelsDir,
                    ioDispatcher = ioDispatcher,
                    getAvailableRamBytes = { availableRamBytes },
                    getTotalRamBytes = { totalRamBytes }
                ) as T
            }
        }
    }
}
