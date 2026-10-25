package com.ai.pocketcore.data

import com.ai.pocketcore.data.api.HuggingFaceApi
import com.ai.pocketcore.data.api.HuggingFaceApiException
import com.ai.pocketcore.data.api.model.HfFileTreeItem
import com.ai.pocketcore.data.api.model.HfLfsInfo
import com.ai.pocketcore.data.repository.HuggingFaceRepositoryImpl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HuggingFaceApiTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        httpClient = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun mockHttpClient(): OkHttpClient {
        return httpClient
    }

    @Test
    fun parseTreeResponseFiltersOnlyGgufFiles() {
        val sampleFiles = listOf(
            HfFileTreeItem(path = "README.md", size = 2048),
            HfFileTreeItem(path = "config.json", size = 512),
            HfFileTreeItem(path = "directory.gguf", size = 0, type = "directory"),
            HfFileTreeItem(path = "model-q4_k_m.gguf", size = 4_800_000_000L),
            HfFileTreeItem(path = "model-q8_0.gguf", size = 8_200_000_000L)
        )
        val repo = HuggingFaceRepositoryImpl(mockHttpClient())
        val ggufList = repo.filterGgufFiles(sampleFiles)
        assertEquals(2, ggufList.size)
        assertTrue(ggufList.any { it.name == "model-q4_k_m.gguf" })
        assertTrue(ggufList.any { it.name == "model-q8_0.gguf" })
    }

    @Test
    fun quantizationExtractionVariousNamingConventions() {
        assertEquals("Q4_K_M", HuggingFaceRepositoryImpl.extractQuantization("model-q4_k_m.gguf"))
        assertEquals("Q8_0", HuggingFaceRepositoryImpl.extractQuantization("model-q8_0.gguf"))
        assertEquals("Q5_K_S", HuggingFaceRepositoryImpl.extractQuantization("Qwen2.5-7B-Instruct-Q5_K_S.gguf"))
        assertEquals("IQ3_M", HuggingFaceRepositoryImpl.extractQuantization("deepseek-r1-distill-qwen-1.5b-IQ3_M.gguf"))
        assertEquals("IQ2_XXS", HuggingFaceRepositoryImpl.extractQuantization("llama-3.2-1b-IQ2_XXS.gguf"))
        assertEquals("F16", HuggingFaceRepositoryImpl.extractQuantization("model-f16.gguf"))
        assertEquals("FP16", HuggingFaceRepositoryImpl.extractQuantization("model-fp16.gguf"))
        assertEquals("BF16", HuggingFaceRepositoryImpl.extractQuantization("model.BF16.gguf"))
        assertEquals("F32", HuggingFaceRepositoryImpl.extractQuantization("model-f32.gguf"))
        assertEquals("FP32", HuggingFaceRepositoryImpl.extractQuantization("model.FP32.gguf"))
        assertEquals("Q4_0", HuggingFaceRepositoryImpl.extractQuantization("model-q4_0.gguf"))
        assertEquals("UNKNOWN", HuggingFaceRepositoryImpl.extractQuantization("custom-weights.gguf"))
    }

    @Test
    fun effectiveSizeUsesLfsWhenAvailable() {
        val itemWithLfs = HfFileTreeItem(
            path = "model-q4_k_m.gguf",
            size = 134, // pointer file size
            lfs = HfLfsInfo(oid = "abc123", size = 4_800_000_000L, pointerSize = 134)
        )
        val itemWithoutLfs = HfFileTreeItem(
            path = "model-small.gguf",
            size = 500_000_000L,
            lfs = null
        )

        val repo = HuggingFaceRepositoryImpl(mockHttpClient())
        val result = repo.filterGgufFiles(listOf(itemWithLfs, itemWithoutLfs), "bartowski/model-gguf")

        assertEquals(2, result.size)
        val lfsResult = result.first { it.name == "model-q4_k_m.gguf" }
        assertEquals(4_800_000_000L, lfsResult.sizeBytes)
        assertEquals("https://huggingface.co/bartowski/model-gguf/resolve/main/model-q4_k_m.gguf", lfsResult.downloadUrl)

        val nonLfsResult = result.first { it.name == "model-small.gguf" }
        assertEquals(500_000_000L, nonLfsResult.sizeBytes)
    }

    @Test
    fun searchModelsQueryParametersAndHeadersSentProperly() = runBlocking {
        val mockJson = """
            [
              {
                "id": "bartowski/Llama-3.2-3B-Instruct-GGUF",
                "author": "bartowski",
                "downloads": 125000,
                "likes": 420,
                "private": false,
                "gated": false,
                "tags": ["gguf", "llama", "nlp"],
                "pipeline_tag": "text-generation"
              }
            ]
        """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val token = "hf_test_secret_token_12345"
        val api = HuggingFaceApi(
            client = httpClient,
            baseUrl = mockServer.url("/").toString(),
            tokenProvider = { token }
        )

        val result = api.searchModels(query = "Llama-3.2")
        assertTrue(result.isSuccess)
        val models = result.getOrThrow()
        assertEquals(1, models.size)
        assertEquals("bartowski/Llama-3.2-3B-Instruct-GGUF", models[0].id)
        assertEquals("bartowski", models[0].author)
        assertEquals(125000L, models[0].downloads)
        assertEquals(420, models[0].likes)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        val authHeader = recordedRequest.getHeader("Authorization")
        assertEquals("Bearer $token", authHeader)
        val requestUrl = recordedRequest.requestUrl.toString()
        assertTrue(requestUrl.contains("search=Llama-3.2"))
        assertTrue(requestUrl.contains("filter=gguf"))
        assertTrue(requestUrl.contains("sort=downloads"))
    }

    @Test
    fun getModelTreeParsesJsonResponseProperly() = runBlocking {
        val mockTreeJson = """
            [
              {
                "type": "file",
                "oid": "111",
                "size": 1024,
                "path": "README.md"
              },
              {
                "type": "file",
                "oid": "222",
                "size": 134,
                "path": "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                "lfs": {
                  "oid": "333",
                  "size": 2040000000,
                  "pointerSize": 134
                }
              }
            ]
        """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mockTreeJson))

        val api = HuggingFaceApi(
            client = httpClient,
            baseUrl = mockServer.url("/").toString()
        )

        val repo = HuggingFaceRepositoryImpl(api)
        val result = repo.fetchGgufFiles("bartowski/Llama-3.2-3B-Instruct-GGUF")

        assertTrue(result.isSuccess)
        val files = result.getOrThrow()
        assertEquals(1, files.size)
        val file = files[0]
        assertEquals("Llama-3.2-3B-Instruct-Q4_K_M.gguf", file.name)
        assertEquals("Q4_K_M", file.quantization)
        assertEquals(2040000000L, file.sizeBytes)
        assertEquals("https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf", file.downloadUrl)

        val recordedRequest = mockServer.takeRequest()
        assertTrue(recordedRequest.path!!.contains("/api/models/bartowski/Llama-3.2-3B-Instruct-GGUF/tree/main"))
    }

    @Test
    fun apiHandlesHttp401Unauthorized() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "Invalid credentials"}""")
        )

        val api = HuggingFaceApi(
            client = httpClient,
            baseUrl = mockServer.url("/").toString()
        )

        val result = api.searchModels(query = "gated-model")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is HuggingFaceApiException)
        assertEquals(401, (exception as HuggingFaceApiException).code)
    }

    @Test
    fun apiHandlesHttp404NotFound() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error": "Repository not found"}""")
        )

        val api = HuggingFaceApi(
            client = httpClient,
            baseUrl = mockServer.url("/").toString()
        )

        val repo = HuggingFaceRepositoryImpl(api)
        val result = repo.fetchGgufFiles("nonexistent/repo")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is HuggingFaceApiException)
        assertEquals(404, (exception as HuggingFaceApiException).code)
    }

    @Test
    fun repositorySearchGgufModelsMapsToSummaries() = runBlocking {
        val mockJson = """
            [
              {
                "id": "Qwen/Qwen2.5-7B-Instruct-GGUF",
                "author": "Qwen",
                "downloads": 88000,
                "likes": 650,
                "private": false,
                "gated": false,
                "tags": ["gguf", "chat"],
                "pipeline_tag": "text-generation"
              }
            ]
        """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val api = HuggingFaceApi(
            client = httpClient,
            baseUrl = mockServer.url("/").toString()
        )
        val repo = HuggingFaceRepositoryImpl(api)

        val result = repo.searchGgufModels("Qwen")
        assertTrue(result.isSuccess)
        val summaries = result.getOrThrow()
        assertEquals(1, summaries.size)
        val summary = summaries[0]
        assertEquals("Qwen/Qwen2.5-7B-Instruct-GGUF", summary.repoId)
        assertEquals("Qwen2.5-7B-Instruct-GGUF", summary.name)
        assertEquals("Qwen", summary.author)
        assertEquals(88000L, summary.downloads)
        assertEquals(650, summary.likes)
        assertFalse(summary.isGated)
    }
}
