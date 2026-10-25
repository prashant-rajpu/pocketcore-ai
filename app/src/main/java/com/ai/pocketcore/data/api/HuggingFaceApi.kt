package com.ai.pocketcore.data.api

import com.ai.pocketcore.data.api.model.HfFileTreeItem
import com.ai.pocketcore.data.api.model.HfLfsInfo
import com.ai.pocketcore.data.api.model.HfModelDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

class HuggingFaceApiException(
    val code: Int,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class HuggingFaceApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: (() -> String?)? = null
) {
    constructor(client: OkHttpClient, token: String?) : this(
        client = client,
        tokenProvider = { token }
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://huggingface.co"
        private const val USER_AGENT = "PocketCore-Android/1.0"
    }

    private fun newRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)

        val token = tokenProvider?.invoke()?.trim()
        if (!token.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }

        return builder
    }

    suspend fun searchModels(
        query: String = "",
        filter: String = "gguf",
        sort: String = "downloads",
        direction: Int = -1,
        limit: Int = 30
    ): Result<List<HfModelDto>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = (baseUrl.trimEnd('/') + "/api/models").toHttpUrl().newBuilder()
            if (query.isNotBlank()) {
                urlBuilder.addQueryParameter("search", query.trim())
            }
            if (filter.isNotBlank()) {
                urlBuilder.addQueryParameter("filter", filter.trim())
            }
            if (sort.isNotBlank()) {
                urlBuilder.addQueryParameter("sort", sort.trim())
            }
            urlBuilder.addQueryParameter("direction", direction.toString())
            urlBuilder.addQueryParameter("limit", limit.toString())

            val request = newRequestBuilder(urlBuilder.build().toString()).get().build()
            val response = client.newCall(request).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorMsg = resp.body?.string() ?: resp.message
                    return@withContext Result.failure(
                        HuggingFaceApiException(resp.code, "HTTP ${resp.code}: $errorMsg")
                    )
                }

                val bodyString = resp.body?.string() ?: "[]"
                val models = parseModelsJson(bodyString)
                Result.success(models)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModelTree(
        repoId: String,
        revision: String = "main"
    ): Result<List<HfFileTreeItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanRepoId = repoId.trim().trim('/')
            val cleanRevision = revision.trim()
            val url = "${baseUrl.trimEnd('/')}/api/models/$cleanRepoId/tree/$cleanRevision"

            val request = newRequestBuilder(url).get().build()
            val response = client.newCall(request).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorMsg = resp.body?.string() ?: resp.message
                    return@withContext Result.failure(
                        HuggingFaceApiException(resp.code, "HTTP ${resp.code}: $errorMsg")
                    )
                }

                val bodyString = resp.body?.string() ?: "[]"
                val files = parseFileTreeJson(bodyString)
                Result.success(files)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseModelsJson(jsonString: String): List<HfModelDto> {
        val list = mutableListOf<HfModelDto>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = if (obj.has("id")) {
                obj.getString("id")
            } else if (obj.has("modelId")) {
                obj.getString("modelId")
            } else if (obj.has("_id")) {
                obj.getString("_id")
            } else {
                ""
            }
            if (id.isEmpty()) continue

            val author = if (obj.has("author") && !obj.isNull("author")) {
                obj.getString("author")
            } else {
                null
            }

            val downloads = obj.optLong("downloads", 0L)
            val likes = obj.optInt("likes", 0)
            val isPrivate = obj.optBoolean("private", false)
            val isGated = when {
                obj.has("gated") && !obj.isNull("gated") -> {
                    val gatedVal = obj.get("gated")
                    when (gatedVal) {
                        is Boolean -> gatedVal
                        is String -> gatedVal.isNotEmpty() && gatedVal != "false"
                        else -> false
                    }
                }
                else -> false
            }

            val tagsList = mutableListOf<String>()
            val tagsArr = obj.optJSONArray("tags")
            if (tagsArr != null) {
                for (j in 0 until tagsArr.length()) {
                    tagsList.add(tagsArr.getString(j))
                }
            }

            val pipelineTag = if (obj.has("pipeline_tag") && !obj.isNull("pipeline_tag")) {
                obj.getString("pipeline_tag")
            } else {
                null
            }

            val lastModified = if (obj.has("lastModified") && !obj.isNull("lastModified")) {
                obj.getString("lastModified")
            } else {
                null
            }

            val description = if (obj.has("description") && !obj.isNull("description")) {
                obj.getString("description")
            } else {
                null
            }

            list.add(
                HfModelDto(
                    id = id,
                    author = author,
                    downloads = downloads,
                    likes = likes,
                    isPrivate = isPrivate,
                    isGated = isGated,
                    tags = tagsList,
                    pipelineTag = pipelineTag,
                    lastModified = lastModified,
                    description = description
                )
            )
        }
        return list
    }

    fun parseFileTreeJson(jsonString: String): List<HfFileTreeItem> {
        val list = mutableListOf<HfFileTreeItem>()
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val path = obj.optString("path", "")
            if (path.isEmpty()) continue

            val type = obj.optString("type", "file")
            val oid = obj.optString("oid", "")
            val size = obj.optLong("size", 0L)

            val lfs = if (obj.has("lfs") && !obj.isNull("lfs")) {
                val lfsObj = obj.getJSONObject("lfs")
                HfLfsInfo(
                    oid = lfsObj.optString("oid", ""),
                    size = lfsObj.optLong("size", 0L),
                    pointerSize = lfsObj.optLong("pointerSize", 0L)
                )
            } else {
                null
            }

            list.add(
                HfFileTreeItem(
                    path = path,
                    size = size,
                    type = type,
                    oid = oid,
                    lfs = lfs
                )
            )
        }
        return list
    }
}
