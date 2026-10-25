package com.ai.pocketcore.data.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GgufValidatorTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun validGgufHeaderReturnsTrue() {
        val file = tempFolder.newFile("valid.gguf")
        // "GGUF" in ASCII is 0x47, 0x47, 0x55, 0x46 (little endian magic uint32: 0x46554747)
        // Followed by version 3: 0x03, 0x00, 0x00, 0x00
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        assertTrue(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun corruptFileReturnsFalse() {
        val file = tempFolder.newFile("corrupt.gguf")
        file.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        assertFalse(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun emptyFileReturnsFalse() {
        val file = tempFolder.newFile("empty.gguf")
        assertFalse(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun tooShortFileReturnsFalse() {
        val file = tempFolder.newFile("short.gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47))
        assertFalse(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun magicOnlyFourBytesReturnsFalseBecauseMissingVersion() {
        val file = tempFolder.newFile("magic_only.gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        assertFalse(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun nonExistentFileReturnsFalse() {
        val file = File(tempFolder.root, "does_not_exist.gguf")
        assertFalse(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun validGgufVersion2ReturnsTrue() {
        val file = tempFolder.newFile("v2.gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x02, 0x00, 0x00, 0x00))
        assertTrue(GgufValidator.isValidGgufHeader(file))
        assertEquals(2, GgufValidator.readGgufVersion(file))
    }

    @Test
    fun validGgufVersion3ReturnsCorrectVersion() {
        val file = tempFolder.newFile("v3.gguf")
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        assertEquals(3, GgufValidator.readGgufVersion(file))
    }

    @Test
    fun invalidHeaderReturnsNullVersion() {
        val file = tempFolder.newFile("bad.gguf")
        file.writeBytes(byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x01, 0x00, 0x00, 0x00))
        assertNull(GgufValidator.readGgufVersion(file))
    }
}
