package dev.proxboss.aoestore.data

import java.io.File
import java.security.MessageDigest

object Sha256 {

    fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(file: File, expected: String): Boolean {
        val normalized = expected.trim()
        if (normalized.isEmpty()) return false
        return digest(file).equals(normalized, ignoreCase = true)
    }
}
