package com.uri.lee.dl.data.upload

import com.uri.lee.dl.domain.media.ImageHost
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.usecase.NotSignedInException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.io.IOException

/**
 * Uploads photos to R2 through the photo-upload Worker (workers/photo-upload), which checks the
 * user's Firebase ID token. The app never holds R2 credentials.
 */
internal class R2PhotoHost(
    private val client: HttpClient,
    private val auth: AuthRepository,
    private val workerUrl: String,
) : ImageHost {

    @Serializable
    private data class Uploaded(val url: String)

    override suspend fun upload(speciesId: Long, jpeg: ByteArray): String {
        if (workerUrl.isBlank()) throw IOException("PHOTO_UPLOAD_URL isn't configured for this build")
        val token = auth.idToken() ?: throw NotSignedInException()
        val response = client.post("${workerUrl.trimEnd('/')}/photos") {
            parameter("speciesKey", speciesId)
            bearerAuth(token)
            contentType(ContentType.Image.JPEG)
            setBody(jpeg)
        }
        if (!response.status.isSuccess()) throw IOException("Photo upload failed: HTTP ${response.status.value}")
        return response.body<Uploaded>().url
    }
}
