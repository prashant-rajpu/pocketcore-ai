package com.ai.pocketcore.data.downloader

import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Validates GGUF model files according to the GGUF specification.
 * GGUF files start with a 4-byte magic number: 0x46554747 ('G', 'G', 'U', 'F' in little-endian)
 * followed by a 4-byte unsigned integer version (e.g. 1, 2, or 3).
 */
object GgufValidator {

    /** GGUF magic bytes: 'G', 'G', 'U', 'F' */
    val GGUF_MAGIC_BYTES = byteArrayOf(0x47.toByte(), 0x47.toByte(), 0x55.toByte(), 0x46.toByte())
    const val GGUF_MAGIC_INT: Long = 0x46554747L

    /**
     * Checks if the given file exists and contains a valid GGUF header magic and version.
     * A valid GGUF file must be at least 8 bytes (4-byte magic + 4-byte version uint32).
     */
    fun isValidGgufHeader(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 8L) {
            return false
        }

        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(8)
                val bytesRead = input.read(header)
                if (bytesRead < 8) {
                    return false
                }

                // Check magic bytes: 0x47 ('G'), 0x47 ('G'), 0x55 ('U'), 0x46 ('F')
                val magicMatches = header[0] == GGUF_MAGIC_BYTES[0] &&
                        header[1] == GGUF_MAGIC_BYTES[1] &&
                        header[2] == GGUF_MAGIC_BYTES[2] &&
                        header[3] == GGUF_MAGIC_BYTES[3]

                if (!magicMatches) {
                    return false
                }

                val version = parseLittleEndianUint32(header, 4)
                version in 1L..100L // Versions 1, 2, 3 (and future reasonable versions)
            }
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Reads the GGUF version from the header, or returns null if invalid.
     */
    fun readGgufVersion(file: File): Int? {
        if (!isValidGgufHeader(file) || file.length() < 8L) {
            return null
        }

        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                if (read >= 8) {
                    parseLittleEndianUint32(header, 4).toInt()
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun parseLittleEndianUint32(bytes: ByteArray, offset: Int): Long {
        val b0 = bytes[offset].toLong() and 0xFFL
        val b1 = bytes[offset + 1].toLong() and 0xFFL
        val b2 = bytes[offset + 2].toLong() and 0xFFL
        val b3 = bytes[offset + 3].toLong() and 0xFFL
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
