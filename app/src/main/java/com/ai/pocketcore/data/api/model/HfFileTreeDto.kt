package com.ai.pocketcore.data.api.model

/**
 * LFS metadata for a Git LFS file on Hugging Face
 */
data class HfLfsInfo(
    val oid: String = "",
    val size: Long = 0L,
    val pointerSize: Long = 0L
)

/**
 * Tree item representation returned from /api/models/{repo_id}/tree/{revision}
 */
data class HfFileTreeItem(
    val path: String,
    val size: Long = 0L,
    val type: String = "file",
    val oid: String = "",
    val lfs: HfLfsInfo? = null
) {
    val name: String get() = path.substringAfterLast('/')
    val effectiveSize: Long get() = lfs?.size?.takeIf { it > 0L } ?: size
}

/**
 * Parsed GGUF file information for download and inspection
 */
data class GgufFileInfo(
    val name: String,
    val path: String = name,
    val sizeBytes: Long = 0L,
    val quantization: String = "",
    val downloadUrl: String = "",
    val repoId: String = ""
) {
    val size: Long get() = sizeBytes
    val filename: String get() = name

    /**
     * Human-readable formatted size string (e.g. "4.47 GB", "850 MB")
     */
    val formattedSize: String
        get() {
            if (sizeBytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups >= units.size) digitGroups = units.size - 1
            val value = sizeBytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(java.util.Locale.US, "%.2f %s", value, units[digitGroups])
        }
}
