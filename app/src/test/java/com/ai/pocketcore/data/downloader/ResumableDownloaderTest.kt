package com.ai.pocketcore.data.downloader

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResumableDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var downloader: ResumableDownloader

    // Minimal valid GGUF header: magic 'GGUF' (4 bytes) + version 3 (4 bytes)
    private val validGgufBytes = byteArrayOf(
        0x47, 0x47, 0x55, 0x46, // 'G', 'G', 'U', 'F'
        0x03, 0x00, 0x00, 0x00, // version 3
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // dummy metadata
    )

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        client = OkHttpClient.Builder().build()
        downloader = ResumableDownloader(client = client, bufferSize = 1024)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun fullDownloadSuccessfulAndAtomicallyRenamed() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "test-model-q4.gguf"

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(validGgufBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        assertTrue("Result should be Completed, was: $result", result is DownloadState.Completed)
        val completed = result as DownloadState.Completed
        assertEquals(validGgufBytes.size.toLong(), completed.totalBytes)

        val targetFile = File(destDir, fileName)
        assertTrue(targetFile.exists())
        assertEquals(validGgufBytes.size.toLong(), targetFile.length())
        assertTrue(GgufValidator.isValidGgufHeader(targetFile))

        // Ensure temporary .part file was cleaned up / renamed
        val partFile = File(destDir, "$fileName.part")
        assertFalse(partFile.exists())
    }

    @Test
    fun resumableDownloadSendsRangeHeaderAndAppendsBytes() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "resumable-model.gguf"
        val partFile = File(destDir, "$fileName.part")

        // Pre-create partial file with first 4 magic bytes
        val initialBytes = byteArrayOf(0x47, 0x47, 0x55, 0x46)
        partFile.writeBytes(initialBytes)

        // Remaining 12 bytes
        val remainingBytes = byteArrayOf(
            0x03, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 4-15/16")
                .setBody(Buffer().write(remainingBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("bytes=4-", recordedRequest.getHeader("Range"))

        assertTrue("Result should be Completed, was: $result", result is DownloadState.Completed)
        val targetFile = File(destDir, fileName)
        assertTrue(targetFile.exists())
        assertEquals(16L, targetFile.length())
        assertTrue(GgufValidator.isValidGgufHeader(targetFile))
        assertFalse(partFile.exists())
    }

    @Test
    fun serverIgnoresRangeOverwritesFromByteZero() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "reset-model.gguf"
        val partFile = File(destDir, "$fileName.part")

        // Pre-create partial file with corrupt bytes
        partFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        // Server responds 200 OK with the entire fresh valid file
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(validGgufBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        assertTrue(result is DownloadState.Completed)
        val targetFile = File(destDir, fileName)
        assertTrue(targetFile.exists())
        assertEquals(validGgufBytes.size.toLong(), targetFile.length())
        assertTrue(GgufValidator.isValidGgufHeader(targetFile))
    }

    @Test
    fun corruptDownloadFailsGgufValidationAndDoesNotCreateTargetFile() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "corrupt-model.gguf"
        val corruptBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(corruptBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        assertTrue("Result should be Failed, was: $result", result is DownloadState.Failed)
        val failed = result as DownloadState.Failed
        assertTrue(failed.error.contains("corrupt GGUF magic header"))

        val targetFile = File(destDir, fileName)
        assertFalse(targetFile.exists())
    }

    @Test
    fun httpErrorEmitsFailedState() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "missing-model.gguf"

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("File Not Found")
        )

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        assertTrue(result is DownloadState.Failed)
        val failed = result as DownloadState.Failed
        assertTrue(failed.error.contains("404"))
    }

    @Test
    fun authTokenPassedInAuthorizationHeader() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "auth-model.gguf"

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(validGgufBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        downloader.download(url, fileName, destDir, authToken = "hf_token_secret_xyz")

        val recordedRequest = mockServer.takeRequest()
        assertEquals("Bearer hf_token_secret_xyz", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun alreadyDownloadedValidModelSkipsNetworkRequest() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "existing-valid.gguf"
        val targetFile = File(destDir, fileName)
        targetFile.writeBytes(validGgufBytes)

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        assertTrue(result is DownloadState.Completed)
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun directDownloadManagerCoordinatesLifecycle() = runBlocking {
        val modelsDir = tempFolder.newFolder("models_manager")
        val fileName = "manager-model.gguf"
        val manager = DirectDownloadManager(modelsDir, downloader)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(validGgufBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        manager.startDownload(url, fileName)

        // Wait for completion
        var attempts = 0
        while (attempts < 50 && manager.state.value !is DownloadState.Completed) {
            kotlinx.coroutines.delay(100)
            attempts++
        }

        assertTrue(manager.state.value is DownloadState.Completed)
        val downloaded = manager.getDownloadedFile(fileName)
        assertNotNull(downloaded)
        assertTrue(GgufValidator.isValidGgufHeader(downloaded!!))
    }

    @Test
    fun truncatedDownloadFailsWithPrematureEofGuard() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "truncated-model.gguf"

        // Server returns 206 declaring 100 total bytes, but stream ends after 16 bytes
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-15/100")
                .setBody(Buffer().write(validGgufBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        val result = downloader.download(url, fileName, destDir)

        assertTrue("Result should be Failed, was: $result", result is DownloadState.Failed)
        val failed = result as DownloadState.Failed
        assertTrue(failed.error.contains("Download ended prematurely"))

        val targetFile = File(destDir, fileName)
        assertFalse(targetFile.exists())
    }

    @Test
    fun cancelDuringDownloadEmitsIdleAndRemovesPartFile() = runBlocking {
        val destDir = tempFolder.newFolder("models")
        val fileName = "cancelled-model.gguf"
        val partFile = File(destDir, "$fileName.part")

        // Throttle body so download stays active while we cancel
        val largeBytes = ByteArray(100_000) { 0x47 }
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .throttleBody(1024, 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody(Buffer().write(largeBytes))
        )

        val url = mockServer.url("/download/$fileName").toString()
        val job = launch {
            downloader.download(url, fileName, destDir)
        }

        // Wait until download enters Downloading state
        var attempts = 0
        while (attempts < 50 && downloader.state.value !is DownloadState.Downloading) {
            kotlinx.coroutines.delay(20)
            attempts++
        }

        // Cancel
        downloader.cancel(partFile)
        job.join()

        assertEquals(DownloadState.Idle, downloader.state.value)
        assertFalse(partFile.exists())
    }
}
