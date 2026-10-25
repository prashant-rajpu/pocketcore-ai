package com.ai.pocketcore.data.repository

import com.ai.pocketcore.data.api.model.GgufFileInfo
import com.ai.pocketcore.data.api.model.HfFileTreeItem
import com.ai.pocketcore.data.api.model.HfModelSummary

interface HuggingFaceRepository {
    suspend fun searchGgufModels(query: String = ""): Result<List<HfModelSummary>>
    suspend fun fetchGgufFiles(repoId: String): Result<List<GgufFileInfo>>
    fun filterGgufFiles(files: List<HfFileTreeItem>, repoId: String = ""): List<GgufFileInfo>
}
