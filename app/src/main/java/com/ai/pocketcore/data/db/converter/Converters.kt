package com.ai.pocketcore.data.db.converter

import androidx.room.TypeConverter
import com.ai.pocketcore.data.db.entity.ChatRole

class Converters {
    @TypeConverter
    fun fromChatRole(role: ChatRole?): String? {
        return role?.name
    }

    @TypeConverter
    fun toChatRole(value: String?): ChatRole? {
        return value?.let {
            runCatching { ChatRole.valueOf(it.trim().uppercase()) }.getOrDefault(ChatRole.USER)
        }
    }
}
