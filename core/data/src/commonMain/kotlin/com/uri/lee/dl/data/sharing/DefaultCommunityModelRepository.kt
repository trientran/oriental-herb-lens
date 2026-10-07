package com.uri.lee.dl.data.sharing

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.datastore.KeyValueStore
import com.uri.lee.dl.core.datastore.stringSetKey
import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.core.firebase.SharedModelRecord
import com.uri.lee.dl.data.upload.sendWithinRateLimit
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.sharing.CommunityModel
import com.uri.lee.dl.domain.sharing.CommunityModelRepository
import com.uri.lee.dl.domain.sharing.HuggingFaceStatus
import com.uri.lee.dl.domain.sharing.ModelReportReason
import com.uri.lee.dl.domain.sharing.SharingRules
import com.uri.lee.dl.domain.upload.NotSignedInException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import okio.ByteString.Companion.toByteString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

/**
 * Shared models: the files go to R2 through the upload Worker (workers/photo-upload, `/models`),
 * which checks the user's ID token; the list is `sharedModels` in Firestore, whose rules check the
 * entry. Hidden models and sharers stay on the device, with hidden photo contributors: hiding a
 * person hides both.
 */
internal class DefaultCommunityModelRepository(
    private val firestore: FirestoreClient,
    private val client: HttpClient,
    private val auth: AuthRepository,
    private val prefs: KeyValueStore,
    private val workerUrl: String,
) : CommunityModelRepository {

    @Serializable
    private data class Uploaded(val id: String, val url: String, val size: Int)

    @Serializable
    private data class Published(val url: String)

    @Serializable
    private data class PublishRequest(val sha256: String)

    override fun observe(): Flow<List<CommunityModel>> =
        combine(firestore.observeSharedModels(LIMIT), prefs.data) { records, stored ->
            val hiddenModels = stored[HIDDEN_MODELS].orEmpty()
            val hiddenPeople = stored[HIDDEN_CONTRIBUTORS].orEmpty()
            records.filter { it.id !in hiddenModels && it.uploaderId !in hiddenPeople }.map { it.toModel() }
        }

    override suspend fun share(
        id: String,
        name: String,
        species: List<String>,
        backbone: String,
        trainable: Boolean,
        file: ByteArray,
        offerToHuggingFace: Boolean,
    ): CommunityModel {
        if (workerUrl.isBlank()) throw IOException("PHOTO_UPLOAD_URL isn't configured for this build")
        val uid = auth.currentUserId ?: throw NotSignedInException()
        val token = auth.idToken() ?: throw NotSignedInException()
        val response = sendWithinRateLimit {
            client.post("${workerUrl.trimEnd('/')}/models") {
                parameter("id", id)
                bearerAuth(token)
                contentType(ContentType.Application.OctetStream)
                setBody(file)
            }
        }
        if (!response.status.isSuccess()) throw IOException("Model upload failed: HTTP ${response.status.value}")
        val uploaded = response.body<Uploaded>()
        val record = SharedModelRecord(
            uploaded.id, name, species, backbone, trainable, uploaded.url, uploaded.size, uid, SharingRules.LICENSE,
            huggingFace = if (offerToHuggingFace) "requested" else "none",
        )
        try {
            // Listed by an earlier try that didn't get to finish: keep that entry (the rules don't allow rewriting it)
            if (!firestore.sharedModelExists(record.id)) firestore.addSharedModel(record)
        } catch (e: Exception) {
            // Not listed: don't leave the file behind
            if (e !is CancellationException) runCatching { deleteFile(uploaded.id, token) }
            throw e
        }
        val shared = record.toModel()
        return if (offerToHuggingFace) publishToHuggingFace(shared, file, token) else shared
    }

    /**
     * Asks the Worker to publish the model on Hugging Face (it holds the token). If that fails, the
     * model stays shared here and asked for; the administrator can publish it later.
     */
    private suspend fun publishToHuggingFace(model: CommunityModel, file: ByteArray, token: String): CommunityModel = try {
        val response = sendWithinRateLimit {
            client.post("${workerUrl.trimEnd('/')}/models/${model.id}/huggingface") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                // Hashed here: the Worker has little computing time, and Hugging Face checks the upload
                setBody(PublishRequest(file.toByteString().sha256().hex()))
            }
        }
        if (response.status.isSuccess()) {
            model.copy(huggingFace = HuggingFaceStatus.PUBLISHED, huggingFaceUrl = response.body<Published>().url)
        } else {
            log.w { "Not published on Hugging Face: HTTP ${response.status.value}" }
            model
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.w(e) { "Not published on Hugging Face" }
        model
    }

    override suspend fun remove(model: CommunityModel) {
        val token = auth.idToken() ?: throw NotSignedInException()
        // The Worker removes the file, its Hugging Face copy and the listing; the listing goes here
        // too in case it couldn't (off the list is what matters most)
        runCatching { deleteFile(model.id, token) }.onFailure { log.w(it) { "Shared model file not removed" } }
        firestore.deleteSharedModel(model.id)
    }

    override suspend fun download(model: CommunityModel): ByteArray {
        val response = client.get(model.url)
        if (!response.status.isSuccess()) throw IOException("Model download failed: HTTP ${response.status.value}")
        return response.readRawBytes()
    }

    override suspend fun report(model: CommunityModel, reason: ModelReportReason) {
        val reporter = auth.currentUserId ?: throw NotSignedInException()
        // Hidden first: the user sees the effect even if the report can't be sent (offline)
        prefs.edit { it[HIDDEN_MODELS] = it[HIDDEN_MODELS].orEmpty() + model.id }
        firestore.addModelReport(model.id, model.uploaderId, reason.name, reporter)
    }

    override suspend fun hideUploader(uploaderId: String) {
        prefs.edit { it[HIDDEN_CONTRIBUTORS] = it[HIDDEN_CONTRIBUTORS].orEmpty() + uploaderId }
    }

    private suspend fun deleteFile(id: String, token: String) {
        val response = client.delete("${workerUrl.trimEnd('/')}/models/$id") { bearerAuth(token) }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NotFound) {
            throw IOException("Model file not removed: HTTP ${response.status.value}")
        }
    }

    private fun SharedModelRecord.toModel() = CommunityModel(
        id, name, species, backbone, trainable, url, size, uploaderId,
        huggingFace = when (huggingFace) {
            "requested" -> HuggingFaceStatus.REQUESTED
            "published" -> HuggingFaceStatus.PUBLISHED
            "declined" -> HuggingFaceStatus.DECLINED
            else -> HuggingFaceStatus.NONE
        },
        huggingFaceUrl = huggingFaceUrl?.takeIf { it.startsWith("https://huggingface.co/") },
    )

    private companion object {
        const val LIMIT = 200
        val HIDDEN_MODELS = stringSetKey("hidden_shared_models")
        // Shared with DefaultModerationRepository: hiding a person hides their photos and models
        val HIDDEN_CONTRIBUTORS = stringSetKey("hidden_contributors")
        val log = Logger.withTag("CommunityModels")
    }
}
