package dev.proxboss.aoestore.data

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ApkDownloader(
    private val client: OkHttpClient,
    context: Context,
) {

    private val downloadsDirectory = File(context.applicationContext.filesDir, "downloads")

    suspend fun download(
        url: String,
        expectedSha256: String,
        fileName: String,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        downloadsDirectory.mkdirs()
        val destination = File(downloadsDirectory, sanitize(fileName))
        val temporary = File(downloadsDirectory, "${sanitize(fileName)}.part")
        temporary.delete()

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(
                        response.code,
                        "Téléchargement refusé par le serveur (HTTP ${response.code})",
                    )
                }
                val body = response.body ?: throw IOException("Réponse vide du serveur")
                val total = body.contentLength()
                var bytesRead = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            onProgress(bytesRead, total)
                        }
                    }
                }
            }

            val actual = Sha256.digest(temporary)
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                throw ChecksumMismatchException(
                    "Le fichier téléchargé est corrompu : SHA-256 $actual au lieu de $expectedSha256",
                )
            }

            destination.delete()
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun sanitize(fileName: String): String {
        val cleaned = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.ifBlank { "application.apk" }
    }
}
