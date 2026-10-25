package com.ai.pocketcore.data.downloader

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * High-level manager coordinating GGUF model downloads across the application.
 * Exposes a reactive [state] stream emitting:
 * [DownloadState.Idle], [DownloadState.Downloading], [DownloadState.Paused],
 * [DownloadState.Completed], and [DownloadState.Failed].
 */
interface DownloadManager {

    /**
     * Reactive stream of download progress and status.
     */
    val state: StateFlow<DownloadState>

    /**
     * Starts or queues a model download.
     *
     * @param url Remote HTTP / HTTPS download URL.
     * @param fileName Target GGUF file name (e.g. "qwen2.5-7b-instruct-q4_k_m.gguf").
     * @param destinationDir Optional custom directory for storage.
     * @param authToken Optional Hugging Face User Access Token for gated/private models.
     */
    fun startDownload(
        url: String,
        fileName: String,
        destinationDir: File? = null,
        authToken: String? = null
    )

    /**
     * Pauses the active download and retains partial bytes in `.gguf.part`.
     */
    fun pauseDownload()

    /**
     * Resumes a paused download sending an HTTP Range header.
     */
    fun resumeDownload()

    /**
     * Cancels the active download and removes any partial `.part` file.
     */
    fun cancelDownload()

    /**
     * Checks if a model file is already downloaded and verified.
     */
    fun getDownloadedFile(fileName: String): File?

    /**
     * Returns the base directory where GGUF model files are stored.
     */
    fun getModelsDirectory(): File

    companion object {
        internal val _sharedState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val sharedState: StateFlow<DownloadState> = _sharedState.asStateFlow()

        fun updateSharedState(newState: DownloadState) {
            _sharedState.value = newState
        }

        fun create(context: Context): DownloadManager {
            return DownloadManagerImpl(context.applicationContext)
        }
    }
}

/**
 * Standard implementation delegating to [DownloadForegroundService].
 */
class DownloadManagerImpl(
    private val context: Context,
    private val downloader: ResumableDownloader = DownloadForegroundService.sharedDownloader
) : DownloadManager {

    override val state: StateFlow<DownloadState>
        get() = DownloadManager.sharedState

    override fun startDownload(
        url: String,
        fileName: String,
        destinationDir: File?,
        authToken: String?
    ) {
        val destPath = destinationDir?.absolutePath ?: getModelsDirectory().absolutePath
        DownloadForegroundService.start(
            context = context,
            url = url,
            fileName = fileName,
            destinationDir = destPath,
            authToken = authToken
        )
    }

    override fun pauseDownload() {
        DownloadForegroundService.pause(context)
    }

    override fun resumeDownload() {
        DownloadForegroundService.resume(context)
    }

    override fun cancelDownload() {
        DownloadForegroundService.cancel(context)
    }

    override fun getDownloadedFile(fileName: String): File? {
        val file = File(getModelsDirectory(), fileName)
        return if (file.exists() && GgufValidator.isValidGgufHeader(file)) {
            file
        } else {
            null
        }
    }

    override fun getModelsDirectory(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}

/**
 * Standalone direct implementation suitable for testing or background execution without Android Services.
 */
class DirectDownloadManager(
    private val modelsDir: File,
    val downloader: ResumableDownloader = ResumableDownloader()
) : DownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    private var currentUrl: String = ""
    private var currentFileName: String = ""
    private var currentAuthToken: String? = null

    override val state: StateFlow<DownloadState> = downloader.state

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }

    override fun startDownload(
        url: String,
        fileName: String,
        destinationDir: File?,
        authToken: String?
    ) {
        currentUrl = url
        currentFileName = fileName
        currentAuthToken = authToken
        val targetDir = destinationDir ?: modelsDir

        downloadJob?.cancel()
        downloadJob = scope.launch {
            downloader.download(url, fileName, targetDir, authToken)
        }
    }

    override fun pauseDownload() {
        downloader.pause()
    }

    override fun resumeDownload() {
        if (currentUrl.isNotBlank() && currentFileName.isNotBlank()) {
            startDownload(currentUrl, currentFileName, modelsDir, currentAuthToken)
        }
    }

    override fun cancelDownload() {
        val partFile = File(modelsDir, "$currentFileName.part")
        downloader.cancel(partFile)
        downloadJob?.cancel()
    }

    override fun getDownloadedFile(fileName: String): File? {
        val file = File(modelsDir, fileName)
        return if (file.exists() && GgufValidator.isValidGgufHeader(file)) {
            file
        } else {
            null
        }
    }

    override fun getModelsDirectory(): File = modelsDir
}
