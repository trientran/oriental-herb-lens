package com.uri.lee.dl.feature.training

import co.touchlab.kermit.Logger
import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.domain.sharing.CommunityModel
import com.uri.lee.dl.domain.sharing.CommunityModelRepository
import com.uri.lee.dl.domain.training.AppFiles
import com.uri.lee.dl.domain.upload.UploadQueue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where sharing a model is, by model id. */
sealed interface ShareProgress {
    data object Sharing : ShareProgress

    /** Couldn't finish yet (no connection, say); it'll try again. */
    data object Waiting : ShareProgress
    data class Done(val shared: CommunityModel) : ShareProgress
    data object Failed : ShareProgress
}

/**
 * Models being shared with everyone. [enqueue] saves the request (jobs/<model id>.txt in [files],
 * with the share's id, picked here so every try is the same share); [runPending] shares it, and
 * keeps it for the next run if it can't finish, up to [MAX_ATTEMPTS] runs.
 */
class ModelShareQueue(
    private val files: AppFiles,
    private val store: UserModelStore,
    private val community: CommunityModelRepository,
    private val notifier: UploadNotifier,
    private val analytics: Analytics,
) : UploadQueue {
    private val lock = Mutex()
    private val _progress = MutableStateFlow<Map<String, ShareProgress>>(emptyMap())

    /** By model id, until [acknowledge]d. */
    val progress: StateFlow<Map<String, ShareProgress>> = _progress.asStateFlow()

    @OptIn(ExperimentalUuidApi::class)
    suspend fun enqueue(modelId: String, offerToHuggingFace: Boolean) {
        files.write("jobs/$modelId.txt", "sharedId=${Uuid.random()}\nhuggingFace=$offerToHuggingFace\n".encodeToByteArray())
        _progress.update { it + (modelId to ShareProgress.Sharing) }
    }

    fun acknowledge(modelId: String) = _progress.update { it - modelId }

    override suspend fun hasPending() = files.list("jobs").isNotEmpty()

    override suspend fun runPending() = lock.withLock {
        for (name in files.list("jobs")) run(name.removeSuffix(".txt"))
    }

    private suspend fun run(modelId: String) {
        val text = files.read("jobs/$modelId.txt")?.decodeToString() ?: return
        files.append("jobs/$modelId.txt", "attempt=1\n")
        val values = text.lines().filter { '=' in it }.map { it.substringBefore('=') to it.substringAfter('=') }
        val sharedId = values.firstOrNull { it.first == "sharedId" }?.second ?: return files.delete("jobs/$modelId.txt")
        val offer = values.any { it == "huggingFace" to "true" }
        val attempts = values.count { it.first == "attempt" } + 1
        val model = store.load(modelId)
        val file = model?.let { store.tflite(it.id) }
        if (model == null || file == null) {
            // Deleted meanwhile: nothing to share
            files.delete("jobs/$modelId.txt")
            _progress.update { it - modelId }
            return
        }
        _progress.update { it + (modelId to ShareProgress.Sharing) }
        try {
            // Trained here, so the file carries what's needed to go on learning
            val shared = community.share(sharedId, model.name, model.trainedClasses, model.backbone, trainable = true, file = file, offerToHuggingFace = offer)
            store.save(model.copy(sharedId = shared.id))
            analytics.log(AnalyticsEvent.ModelShared(model.trainedClasses.size, to = "community", huggingFace = offer))
            notifier.modelShareFinished(model.name, shared = true)
            _progress.update { it + (modelId to ShareProgress.Done(shared)) }
            files.delete("jobs/$modelId.txt")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Sharing $modelId failed (try $attempts)" }
            if (attempts < MAX_ATTEMPTS) {
                _progress.update { it + (modelId to ShareProgress.Waiting) }
            } else {
                notifier.modelShareFinished(model.name, shared = false)
                _progress.update { it + (modelId to ShareProgress.Failed) }
                files.delete("jobs/$modelId.txt")
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 8
        private val log = Logger.withTag("ModelShare")
    }
}
