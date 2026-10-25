package com.ai.pocketcore.data.downloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance resumable chunked downloader with HTTP Range header support,
 * streaming in 64KB chunks, real-time throughput/ETA calculation, and GGUF header validation.
 */
class ResumableDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)

    @Volatile
    private var activeCall: okhttp3.Call? = null

    /**
     * Pauses the active download. Preserves the partial file on disk.
     */
    fun pause() {
        isPaused.set(true)
        activeCall?.cancel()
    }

    /**
     * Cancels the active download and removes any partial `.part` file.
     */
    fun cancel(partFile: File? = null) {
        isCancelled.set(true)
        activeCall?.cancel()
        partFile?.let { if (it.exists()) it.delete() }
        _state.value = DownloadState.Idle
    }

    /**
     * Downloads a file from [downloadUrl] into [destinationDir]/[fileName].
     * Supports HTTP Range resumption using [destinationDir]/[fileName].part.
     * Validates GGUF magic bytes before atomically renaming to [fileName].
     */
    suspend fun download(
        downloadUrl: String,
        fileName: String,
        destinationDir: File,
        authToken: String? = null
    ): DownloadState = withContext(Dispatchers.IO) {
        isPaused.set(false)
        isCancelled.set(false)

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val targetFile = File(destinationDir, fileName)
        val partFile = File(destinationDir, "$fileName.part")

        // If target file already exists and has a valid GGUF header, complete immediately
        if (targetFile.exists() && GgufValidator.isValidGgufHeader(targetFile)) {
            val completed = DownloadState.Completed(targetFile, targetFile.length())
            _state.value = completed
            return@withContext completed
        }

        var existingBytes = if (partFile.exists()) partFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "PocketCore-Downloader/1.0")

        if (!authToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $authToken")
        }

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        val call = client.newCall(request)
        activeCall = call

        try {
            val response: Response = call.execute()

            if (response.code == 416) {
                // Range Not Satisfiable: partial file might already be complete or corrupt
                response.close()
                if (partFile.exists() && GgufValidator.isValidGgufHeader(partFile)) {
                    if (atomicRename(partFile, targetFile)) {
                        val completed = DownloadState.Completed(targetFile, targetFile.length())
                        _state.value = completed
                        return@withContext completed
                    }
                }
                // Corrupt partial file, delete and restart
                partFile.delete()
                existingBytes = 0L
                return@withContext download(downloadUrl, fileName, destinationDir, authToken)
            }

            if (!response.isSuccessful) {
                val errorMsg = "HTTP download failed with code ${response.code}: ${response.message}"
                response.close()
                val failed = DownloadState.Failed(errorMsg)
                _state.value = failed
                return@withContext failed
            }

            val responseBody = response.body
            if (responseBody == null) {
                response.close()
                val failed = DownloadState.Failed("Response body was empty.")
                _state.value = failed
                return@withContext failed
            }

            val isPartial = response.code == 206
            val totalBytes: Long = if (isPartial) {
                val contentRange = response.header("Content-Range")
                parseTotalBytesFromContentRange(contentRange) ?: (existingBytes + responseBody.contentLength())
            } else {
                // Server does not support Range or started from byte 0
                existingBytes = 0L
                responseBody.contentLength()
            }

            val append = isPartial && existingBytes > 0L
            var downloadedBytes = existingBytes

            // Initial state
            val initialProgress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
            _state.value = DownloadState.Downloading(
                progress = initialProgress,
                speedMbS = 0.0,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                etaSeconds = -1L
            )

            var lastReportTime = System.currentTimeMillis()
            var lastReportBytes = downloadedBytes
            var smoothedSpeed = 0.0

            val buffer = ByteArray(bufferSize)

            response.use {
                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(partFile, append).use { outputStream ->
                        while (currentCoroutineContext().isActive) {
                            if (isCancelled.get()) {
                                outputStream.flush()
                                if (partFile.exists()) partFile.delete()
                                _state.value = DownloadState.Idle
                                return@withContext DownloadState.Idle
                            }

                            if (isPaused.get()) {
                                outputStream.flush()
                                val pausedProgress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                } else 0f
                                val paused = DownloadState.Paused(pausedProgress, downloadedBytes, totalBytes)
                                _state.value = paused
                                return@withContext paused
                            }

                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break

                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDeltaMs = now - lastReportTime
                            if (timeDeltaMs >= PROGRESS_UPDATE_INTERVAL_MS) {
                                val bytesDelta = downloadedBytes - lastReportBytes
                                val seconds = timeDeltaMs / 1000.0
                                val instantSpeedMbS = if (seconds > 0.0) {
                                    (bytesDelta.toDouble() / (1024.0 * 1024.0)) / seconds
                                } else 0.0

                                smoothedSpeed = if (smoothedSpeed <= 0.0) {
                                    instantSpeedMbS
                                } else {
                                    smoothedSpeed * 0.7 + instantSpeedMbS * 0.3
                                }

                                val etaSeconds = if (smoothedSpeed > 0.001 && totalBytes > downloadedBytes) {
                                    val remainingMb = (totalBytes - downloadedBytes).toDouble() / (1024.0 * 1024.0)
                                    (remainingMb / smoothedSpeed).toLong().coerceAtLeast(0L)
                                } else {
                                    -1L
                                }

                                val progress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                } else 0f

                                _state.value = DownloadState.Downloading(
                                    progress = progress,
                                    speedMbS = smoothedSpeed,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    etaSeconds = etaSeconds
                                )

                                lastReportTime = now
                                lastReportBytes = downloadedBytes
                            }
                        }
                        outputStream.flush()
                    }
                }
            }

            if (isCancelled.get()) {
                if (partFile.exists()) partFile.delete()
                _state.value = DownloadState.Idle
                return@withContext DownloadState.Idle
            }

            if (isPaused.get()) {
                val pausedProgress = if (totalBytes > 0L) {
                    (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                } else 0f
                val paused = DownloadState.Paused(pausedProgress, downloadedBytes, totalBytes)
                _state.value = paused
                return@withContext paused
            }

            // Guard against premature EOF / truncated stream
            if (totalBytes > 0L && downloadedBytes < totalBytes) {
                val failed = DownloadState.Failed(
                    "Download ended prematurely ($downloadedBytes / $totalBytes bytes)."
                )
                _state.value = failed
                return@withContext failed
            }

            // Download completed, validate GGUF header
            if (!GgufValidator.isValidGgufHeader(partFile)) {
                val failed = DownloadState.Failed(
                    "Download verification failed: corrupt GGUF magic header in '$fileName'."
                )
                _state.value = failed
                return@withContext failed
            }

            // Atomically rename .part file to final .gguf file
            val renamed = atomicRename(partFile, targetFile)
            if (!renamed) {
                val failed = DownloadState.Failed("Failed to atomically rename .part file to '$fileName'.")
                _state.value = failed
                return@withContext failed
            }

            val completed = DownloadState.Completed(targetFile, targetFile.length())
            _state.value = completed
            completed
        } catch (e: CancellationException) {
            if (isCancelled.get()) {
                if (partFile.exists()) partFile.delete()
                _state.value = DownloadState.Idle
                DownloadState.Idle
            } else if (isPaused.get()) {
                val paused = DownloadState.Paused(
                    progress = if (partFile.exists()) (partFile.length().toFloat() / (partFile.length() + 1)).coerceIn(0f, 1f) else 0f,
                    downloadedBytes = if (partFile.exists()) partFile.length() else 0L
                )
                _state.value = paused
                paused
            } else {
                if (partFile.exists()) partFile.delete()
                _state.value = DownloadState.Idle
                DownloadState.Idle
            }
        } catch (e: IOException) {
            if (isCancelled.get()) {
                if (partFile.exists()) partFile.delete()
                _state.value = DownloadState.Idle
                DownloadState.Idle
            } else if (isPaused.get()) {
                val paused = DownloadState.Paused(
                    downloadedBytes = if (partFile.exists()) partFile.length() else 0L
                )
                _state.value = paused
                paused
            } else {
                val failed = DownloadState.Failed("Network or I/O error: ${e.message}", e)
                _state.value = failed
                failed
            }
        } catch (e: Exception) {
            if (isCancelled.get()) {
                if (partFile.exists()) partFile.delete()
                _state.value = DownloadState.Idle
                DownloadState.Idle
            } else {
                val failed = DownloadState.Failed("Unexpected download error: ${e.message}", e)
                _state.value = failed
                failed
            }
        } finally {
            activeCall = null
        }
    }

    private fun atomicRename(source: File, target: File): Boolean {
        if (!source.exists()) return false
        if (target.exists()) {
            target.delete()
        }
        return try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (e: Exception) {
            // Fallback for filesystems that do not support ATOMIC_MOVE
            source.renameTo(target)
        }
    }

    private fun parseTotalBytesFromContentRange(contentRange: String?): Long? {
        if (contentRange == null) return null
        // Expected format: bytes <start>-<end>/<total>
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex != -1 && slashIndex + 1 < contentRange.length) {
            val totalStr = contentRange.substring(slashIndex + 1).trim()
            return totalStr.toLongOrNull()
        }
        return null
    }

    companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024 // 64 KB
        const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}
