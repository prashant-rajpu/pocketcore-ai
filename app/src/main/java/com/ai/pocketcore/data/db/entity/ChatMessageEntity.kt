package com.ai.pocketcore.data.db.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

fun parseChatRole(role: String): ChatRole {
    return try {
        ChatRole.valueOf(role.trim().uppercase())
    } catch (_: Exception) {
        ChatRole.USER
    }
}

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensPerSec: Double = 0.0,
    val ttftMs: Long = 0L,
    val modelIdUsed: String? = null
) {
    @Ignore
    constructor(
        sessionId: String,
        role: ChatRole,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        tokensPerSec: Double = 0.0,
        ttftMs: Long = 0L,
        modelIdUsed: String? = null
    ) : this(
        id = 0L,
        sessionId = sessionId,
        role = role,
        content = content,
        timestamp = timestamp,
        tokensPerSec = tokensPerSec,
        ttftMs = ttftMs,
        modelIdUsed = modelIdUsed
    )

    @Ignore
    constructor(
        id: Long = 0L,
        sessionId: String,
        role: String,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        tokensPerSec: Double = 0.0,
        ttftMs: Long = 0L,
        modelIdUsed: String? = null
    ) : this(
        id = id,
        sessionId = sessionId,
        role = parseChatRole(role),
        content = content,
        timestamp = timestamp,
        tokensPerSec = tokensPerSec,
        ttftMs = ttftMs,
        modelIdUsed = modelIdUsed
    )

    fun isUser(): Boolean = role == ChatRole.USER
    fun isAssistant(): Boolean = role == ChatRole.ASSISTANT
    fun isSystem(): Boolean = role == ChatRole.SYSTEM
}
