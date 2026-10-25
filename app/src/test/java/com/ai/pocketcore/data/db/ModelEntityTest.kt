package com.ai.pocketcore.data.db

import com.ai.pocketcore.data.db.converter.Converters
import com.ai.pocketcore.data.db.entity.ChatMessageEntity
import com.ai.pocketcore.data.db.entity.ChatRole
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.data.db.entity.parseChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEntityTest {

    @Test
    fun formatFileSizeToGbString() {
        val model = ModelEntity(
            id = "qwen-2.5-7b-q4",
            repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF",
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            localPath = "/data/local/models/qwen.gguf",
            fileSizeBytes = 4_920_000_000L,
            quantization = "Q4_K_M",
            isActive = true
        )
        val formatted = model.formattedSizeGb()
        assertEquals("4.58 GB", formatted)
    }

    @Test
    fun formatFileSizeEdgeCases() {
        val zeroModel = ModelEntity(
            id = "test-zero",
            repoId = "test/repo",
            fileName = "model.gguf",
            localPath = "/path/zero.gguf",
            fileSizeBytes = 0L,
            quantization = "Q4_0"
        )
        assertEquals("0.00 GB", zeroModel.formattedSizeGb())

        val oneGbModel = ModelEntity(
            id = "test-one-gb",
            repoId = "test/repo",
            fileName = "model.gguf",
            localPath = "/path/one.gguf",
            fileSizeBytes = 1024L * 1024L * 1024L,
            quantization = "Q8_0"
        )
        assertEquals("1.00 GB", oneGbModel.formattedSizeGb())
    }

    @Test
    fun modelEntityDefaultValues() {
        val before = System.currentTimeMillis()
        val model = ModelEntity(
            id = "llama-3.2-3b-q4",
            repoId = "meta-llama/Llama-3.2-3B-Instruct",
            fileName = "llama3.2-3b-q4.gguf",
            localPath = "/data/models/llama3.2.gguf",
            fileSizeBytes = 2_150_000_000L,
            quantization = "Q4_K_M"
        )
        val after = System.currentTimeMillis()

        assertEquals("llama", model.architecture)
        assertEquals(4096, model.contextLength)
        assertFalse(model.isActive)
        assertTrue(model.downloadedAt in before..after)
    }

    @Test
    fun chatMessageEntityCreationAndRoleHelpers() {
        val userMsg = ChatMessageEntity(
            sessionId = "session-123",
            role = ChatRole.USER,
            content = "Explain quantum computing"
        )
        assertEquals(0L, userMsg.id)
        assertEquals("session-123", userMsg.sessionId)
        assertEquals(ChatRole.USER, userMsg.role)
        assertEquals("Explain quantum computing", userMsg.content)
        assertTrue(userMsg.isUser())
        assertFalse(userMsg.isAssistant())
        assertFalse(userMsg.isSystem())
        assertEquals(0.0, userMsg.tokensPerSec, 0.001)
        assertEquals(0L, userMsg.ttftMs)
        assertNull(userMsg.modelIdUsed)

        val assistantMsg = ChatMessageEntity(
            id = 42L,
            sessionId = "session-123",
            role = ChatRole.ASSISTANT,
            content = "Quantum computing uses qubits...",
            timestamp = 1700000000000L,
            tokensPerSec = 42.5,
            ttftMs = 320L,
            modelIdUsed = "qwen-2.5-7b-q4"
        )
        assertEquals(42L, assistantMsg.id)
        assertTrue(assistantMsg.isAssistant())
        assertFalse(assistantMsg.isUser())
        assertEquals(42.5, assistantMsg.tokensPerSec, 0.001)
        assertEquals(320L, assistantMsg.ttftMs)
        assertEquals("qwen-2.5-7b-q4", assistantMsg.modelIdUsed)

        val systemMsg = ChatMessageEntity(
            sessionId = "session-123",
            role = "SYSTEM",
            content = "You are an AI assistant."
        )
        assertTrue(systemMsg.isSystem())
        assertEquals(ChatRole.SYSTEM, systemMsg.role)
    }

    @Test
    fun chatRoleParsingRobustness() {
        assertEquals(ChatRole.USER, parseChatRole("user"))
        assertEquals(ChatRole.USER, parseChatRole("USER"))
        assertEquals(ChatRole.ASSISTANT, parseChatRole(" assistant "))
        assertEquals(ChatRole.SYSTEM, parseChatRole("System"))
        assertEquals(ChatRole.USER, parseChatRole("unknown_role"))
        assertEquals(ChatRole.USER, parseChatRole(""))
    }

    @Test
    fun typeConvertersSerialization() {
        val converters = Converters()
        assertEquals("USER", converters.fromChatRole(ChatRole.USER))
        assertEquals("ASSISTANT", converters.fromChatRole(ChatRole.ASSISTANT))
        assertEquals("SYSTEM", converters.fromChatRole(ChatRole.SYSTEM))
        assertNull(converters.fromChatRole(null))

        assertEquals(ChatRole.USER, converters.toChatRole("USER"))
        assertEquals(ChatRole.ASSISTANT, converters.toChatRole("ASSISTANT"))
        assertEquals(ChatRole.SYSTEM, converters.toChatRole("SYSTEM"))
        assertEquals(ChatRole.USER, converters.toChatRole("invalid_role"))
        assertNull(converters.toChatRole(null))
    }

    @Test
    fun databaseImplementationAndDaoMethods() {
        val dbClass = AppDatabase::class.java
        // Verify abstract DAO getter methods on AppDatabase
        val modelDaoMethod = dbClass.getMethod("modelDao")
        assertNotNull("AppDatabase must declare modelDao()", modelDaoMethod)
        assertEquals(com.ai.pocketcore.data.db.dao.ModelDao::class.java, modelDaoMethod.returnType)

        val chatDaoMethod = dbClass.getMethod("chatDao")
        assertNotNull("AppDatabase must declare chatDao()", chatDaoMethod)
        assertEquals(com.ai.pocketcore.data.db.dao.ChatDao::class.java, chatDaoMethod.returnType)

        // Verify Room code generator created AppDatabase_Impl
        val implClass = Class.forName("com.ai.pocketcore.data.db.AppDatabase_Impl")
        assertNotNull("Room must generate AppDatabase_Impl", implClass)
        assertTrue("AppDatabase_Impl must extend AppDatabase", AppDatabase::class.java.isAssignableFrom(implClass))

        // Verify Room generated ModelDao_Impl and ChatDao_Impl
        val modelDaoImpl = Class.forName("com.ai.pocketcore.data.db.dao.ModelDao_Impl")
        assertTrue(com.ai.pocketcore.data.db.dao.ModelDao::class.java.isAssignableFrom(modelDaoImpl))

        val chatDaoImpl = Class.forName("com.ai.pocketcore.data.db.dao.ChatDao_Impl")
        assertTrue(com.ai.pocketcore.data.db.dao.ChatDao::class.java.isAssignableFrom(chatDaoImpl))
    }
}
