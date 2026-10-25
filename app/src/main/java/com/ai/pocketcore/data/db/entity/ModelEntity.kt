package com.ai.pocketcore.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String,
    val repoId: String,
    val fileName: String,
    val localPath: String,
    val fileSizeBytes: Long,
    val quantization: String,
    val contextLength: Int = 4096,
    val architecture: String = "llama",
    val downloadedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
) {
    fun formattedSizeGb(): String {
        val gb = fileSizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
