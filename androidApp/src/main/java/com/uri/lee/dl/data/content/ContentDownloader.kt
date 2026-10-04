package com.uri.lee.dl.data.content

import com.uri.lee.dl.core.common.AppDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ChecksumMismatchException(expected: String, actual: String) :
    IOException("Checksum mismatch: expected $expected, got $actual")

/** Streams a download to disk and verifies its SHA-256 as it goes. */
class ContentDownloader(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) {
    /**
     * Downloads [url] into [target] unless [target] already holds a file with [expectedSha256]
     * (left from a download that was verified but not yet activated). On any failure [target] is
     * deleted and the exception rethrown: [IOException] for network or checksum problems.
     */
    suspend fun download(url: String, expectedSha256: String, target: File) = withContext(dispatchers.io) {
        if (target.isFile && sha256Hex(target).equals(expectedSha256, ignoreCase = true)) return@withContext
        target.parentFile?.mkdirs()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value} for $url")
                val body = response.bodyAsChannel()
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = body.readAvailable(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            val actual = digest.digest().toHex()
            if (!actual.equals(expectedSha256, ignoreCase = true)) throw ChecksumMismatchException(expectedSha256, actual)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    companion object {
        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
