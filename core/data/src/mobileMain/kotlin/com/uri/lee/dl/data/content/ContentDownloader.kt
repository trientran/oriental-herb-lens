package com.uri.lee.dl.data.content

import com.uri.lee.dl.core.common.AppDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

/** The download arrived intact but isn't the file that was published. Retrying won't help. */
internal class ChecksumMismatchException(expected: String, actual: String) :
    Exception("Checksum mismatch: expected $expected, got $actual")

/** A network problem: worth retrying later. */
internal class DownloadException(message: String) : Exception(message)

/** Streams a download to disk and verifies its SHA-256 as it goes. */
internal class ContentDownloader(
    private val client: HttpClient,
    private val fileSystem: FileSystem,
    private val dispatchers: AppDispatchers,
) {
    /**
     * Downloads [url] into [target] unless [target] already holds a file with [expectedSha256]
     * (left from a download that was verified but not yet activated). On any failure [target] is
     * deleted and the exception rethrown.
     */
    suspend fun download(url: String, expectedSha256: String, target: Path) = withContext(dispatchers.io) {
        if (fileSystem.exists(target) && sha256Hex(target).equals(expectedSha256, ignoreCase = true)) return@withContext
        target.parent?.let(fileSystem::createDirectories)
        try {
            val hashing = HashingSink.sha256(fileSystem.sink(target))
            hashing.buffer().use { output ->
                client.prepareGet(url).execute { response ->
                    if (!response.status.isSuccess()) throw DownloadException("HTTP ${response.status.value} for $url")
                    val body = response.bodyAsChannel()
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = body.readAvailable(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            val actual = hashing.hash.hex()
            if (!actual.equals(expectedSha256, ignoreCase = true)) throw ChecksumMismatchException(expectedSha256, actual)
        } catch (e: Exception) {
            fileSystem.delete(target)
            throw e
        }
    }

    private fun sha256Hex(file: Path): String {
        val hashing = HashingSink.sha256(blackholeSink())
        fileSystem.source(file).buffer().use { it.readAll(hashing) }
        return hashing.hash.hex()
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
