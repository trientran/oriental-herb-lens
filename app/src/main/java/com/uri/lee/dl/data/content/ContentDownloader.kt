package com.uri.lee.dl.data.content

import com.uri.lee.dl.core.common.AppDispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ChecksumMismatchException(expected: String, actual: String) :
    IOException("Checksum mismatch: expected $expected, got $actual")

/** Streams a download to disk and verifies its SHA-256 as it goes. */
class ContentDownloader(
    private val client: OkHttpClient,
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
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                val body = response.body ?: throw IOException("Empty body for $url")
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
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
