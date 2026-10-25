package com.ai.pocketcore.data.api.model

/**
 * Hugging Face Model DTO returned from /api/models
 */
data class HfModelDto(
    val id: String,
    val author: String? = null,
    val downloads: Long = 0L,
    val likes: Int = 0,
    val isPrivate: Boolean = false,
    val isGated: Boolean = false,
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val lastModified: String? = null,
    val description: String? = null
)

/**
 * Domain summary representation of a Hugging Face Model for Hub UI
 */
data class HfModelSummary(
    val repoId: String,
    val name: String,
    val author: String,
    val downloads: Long = 0L,
    val likes: Int = 0,
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val lastModified: String? = null,
    val isGated: Boolean = false,
    val isPrivate: Boolean = false,
    val description: String? = null
) {
    val id: String get() = repoId
}

fun HfModelDto.toSummary(): HfModelSummary {
    val authorName = author?.takeIf { it.isNotBlank() }
        ?: if (id.contains('/')) id.substringBefore('/') else ""
    val modelName = if (id.contains('/')) id.substringAfter('/') else id
    return HfModelSummary(
        repoId = id,
        name = modelName,
        author = authorName,
        downloads = downloads,
        likes = likes,
        tags = tags,
        pipelineTag = pipelineTag,
        lastModified = lastModified,
        isGated = isGated,
        isPrivate = isPrivate,
        description = description
    )
}
