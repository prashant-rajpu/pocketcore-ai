package com.ai.pocketcore.data.repository

import com.ai.pocketcore.data.api.HuggingFaceApi
import com.ai.pocketcore.data.api.model.GgufFileInfo
import com.ai.pocketcore.data.api.model.HfFileTreeItem
import com.ai.pocketcore.data.api.model.HfModelSummary
import com.ai.pocketcore.data.api.model.toSummary
import okhttp3.OkHttpClient

class HuggingFaceRepositoryImpl(
    private val api: HuggingFaceApi
) : HuggingFaceRepository {

    constructor(
        client: OkHttpClient = OkHttpClient(),
        tokenProvider: (() -> String?)? = null
    ) : this(HuggingFaceApi(client = client, tokenProvider = tokenProvider))

    constructor(
        client: OkHttpClient,
        token: String?
    ) : this(HuggingFaceApi(client = client, token = token))

    override suspend fun searchGgufModels(query: String): Result<List<HfModelSummary>> {
        return api.searchModels(query = query, filter = "gguf").map { dtos ->
            dtos.map { it.toSummary() }
        }
    }

    override suspend fun fetchGgufFiles(repoId: String): Result<List<GgufFileInfo>> {
        return api.getModelTree(repoId).map { files ->
            filterGgufFiles(files, repoId)
        }
    }

    override fun filterGgufFiles(files: List<HfFileTreeItem>, repoId: String): List<GgufFileInfo> {
        val cleanRepoId = repoId.trim().trim('/')
        return files
            .filter { it.type != "directory" && it.path.endsWith(".gguf", ignoreCase = true) }
            .map { item ->
                val fileName = item.path.substringAfterLast('/')
                val size = if (item.lfs != null && item.lfs.size > 0L) {
                    item.lfs.size
                } else {
                    item.size
                }
                val quant = extractQuantization(fileName)
                val downloadUrl = if (cleanRepoId.isNotEmpty()) {
                    "https://huggingface.co/$cleanRepoId/resolve/main/${item.path}"
                } else {
                    "https://huggingface.co/resolve/main/${item.path}"
                }
                GgufFileInfo(
                    name = fileName,
                    path = item.path,
                    sizeBytes = size,
                    quantization = quant,
                    downloadUrl = downloadUrl,
                    repoId = cleanRepoId
                )
            }
    }

    companion object {
        private val QUANT_REGEX = Regex(
            """(?i)(?:^|[-._])(IQ[1-4]_[A-Z0-9_]+|Q[1-8]_[A-Z0-9_]+|Q[1-8]_\d+|F16|BF16|F32|FP16|FP32)(?:[-._]|$)"""
        )

        fun extractQuantization(fileName: String): String {
            val baseName = fileName.removeSuffix(".gguf").removeSuffix(".GGUF")
            val match = QUANT_REGEX.find(baseName)
            return match?.groupValues?.get(1)?.uppercase() ?: "UNKNOWN"
        }
    }
}
