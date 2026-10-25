package com.ai.pocketcore.data.downloader

import java.io.File

/**
 * State of a model download operation.
 */
sealed interface DownloadState {

    /**
     * No active download in progress.
     */
    data object Idle : DownloadState

    /**
     * Download is in progress.
     *
     * @property progress Value from 0.0f to 1.0f.
     * @property speedMbS Download throughput in megabytes per second (MB/s).
     * @property downloadedBytes Bytes downloaded so far.
     * @property totalBytes Total file size in bytes, or -1 if unknown.
     * @property etaSeconds Estimated time remaining in seconds, or -1 if unknown.
     */
    data class Downloading(
        val progress: Float,
        val speedMbS: Double = 0.0,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val etaSeconds: Long = -1L
    ) : DownloadState {
        val progressPercent: Int
            get() = (progress * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Download has been paused. Temporary `.part` file remains on disk.
     *
     * @property progress Value from 0.0f to 1.0f.
     * @property downloadedBytes Bytes saved so far.
     * @property totalBytes Total expected bytes, or -1 if unknown.
     */
    data class Paused(
        val progress: Float = 0f,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L
    ) : DownloadState {
        val progressPercent: Int
            get() = (progress * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Download has finished, GGUF magic header has been validated,
     * and the temporary file has been atomically renamed to the final destination.
     *
     * @property file Final validated .gguf file.
     * @property totalBytes Size of the validated model file.
     */
    data class Completed(
        val file: File,
        val totalBytes: Long
    ) : DownloadState

    /**
     * Download failed due to network error, HTTP error, corrupted GGUF header,
     * or disk I/O error.
     *
     * @property error Human-readable error description.
     * @property throwable Optional underlying cause.
     */
    data class Failed(
        val error: String,
        val throwable: Throwable? = null
    ) : DownloadState
}
