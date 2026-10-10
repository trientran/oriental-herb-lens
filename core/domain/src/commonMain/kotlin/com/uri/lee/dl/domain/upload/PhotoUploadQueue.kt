package com.uri.lee.dl.domain.upload

import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.ImageHost
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.notification.UploadNotifier
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.domain.training.AppFiles
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotSignedInException : IllegalStateException("Sign in to contribute")

sealed interface SubmitProgress {
    data class Uploading(val uploaded: Int, val total: Int) : SubmitProgress

    /** All photos were attempted; [failed] couldn't be read or uploaded. */
    data class Finished(val uploaded: Int, val failed: Int) : SubmitProgress
}

/**
 * Photos shared of a herb. [enqueue] compresses them into the app's files (in [files]: one folder
 * per job under jobs/), then [runPending] uploads them one at a time, noting each that's done, and
 * attaches them all to the herb. A job that can't finish (no connection) stays for the next run;
 * after [MAX_ATTEMPTS] runs, what was uploaded is attached and the rest counted as failed.
 */
class PhotoUploadQueue(
    private val files: AppFiles,
    private val compressor: ImageCompressor,
    private val host: ImageHost,
    private val contributions: ContributionRepository,
    private val auth: AuthRepository,
    private val notifier: UploadNotifier,
    private val analytics: Analytics,
) : UploadQueue {
    private val lock = Mutex()
    private val _progress = MutableStateFlow<Map<String, SubmitProgress>>(emptyMap())

    /** Progress of each job by id, until [acknowledge]d; finished ones stay so a screen can show them. */
    val progress: StateFlow<Map<String, SubmitProgress>> = _progress.asStateFlow()

    /** Saves the photos (compressed) and returns the job's id; nothing is uploaded yet. */
    suspend fun enqueue(herbId: Long, speciesName: String?, images: List<LocalImage>, location: GeoLocation): String {
        val uploader = auth.currentUserId ?: throw NotSignedInException()
        val id = "p" + Random.nextLong(1, Long.MAX_VALUE).toString(36)
        var saved = 0
        for (image in images) {
            val jpeg = try {
                compressor.compress(image)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (jpeg != null) files.write("jobs/$id/$saved.jpg", jpeg).also { saved++ }
        }
        // Written last: a job counts once its photos are all saved
        files.write(
            "jobs/$id/job.txt",
            JobFile.encode(
                listOf(
                    "herbId" to herbId, "speciesName" to speciesName, "latitude" to location.latitude,
                    "longitude" to location.longitude, "uploader" to uploader, "photos" to saved, "unreadable" to images.size - saved,
                ),
            ).encodeToByteArray(),
        )
        _progress.update { it + (id to SubmitProgress.Uploading(0, images.size)) }
        return id
    }

    fun acknowledge(id: String) = _progress.update { it - id }

    override suspend fun hasPending() = files.list("jobs").isNotEmpty()

    override suspend fun runPending() = lock.withLock {
        for (id in files.list("jobs")) run(id)
    }

    private suspend fun run(id: String) {
        val job = files.read("jobs/$id/job.txt")?.decodeToString()?.let(::JobFile)
        if (job == null) {
            // Never finished saving (the app closed meanwhile): nothing to send
            files.delete("jobs/$id")
            return
        }
        files.append("jobs/$id/job.txt", "attempt=1\n")
        val attempts = job.all("attempt").size + 1
        val herbId = job["herbId"]?.toLongOrNull() ?: return files.delete("jobs/$id")
        val photos = job["photos"]?.toIntOrNull() ?: 0
        val total = photos + (job["unreadable"]?.toIntOrNull() ?: 0)
        val uploader = job["uploader"].orEmpty()
        val location = GeoLocation(job["latitude"]?.toDoubleOrNull() ?: 0.0, job["longitude"]?.toDoubleOrNull() ?: 0.0)
        val done = job.all("uploaded").associate { it.substringBefore(' ').toInt() to it.substringAfter(' ') }.toMutableMap()
        val giveUp = attempts >= MAX_ATTEMPTS
        _progress.update { it + (id to SubmitProgress.Uploading(done.size, total)) }

        // Uploads count as the person who shared them: another account signed in waits
        if (auth.currentUserId == uploader) {
            for (index in 0 until photos) {
                if (index in done) continue
                val jpeg = files.read("jobs/$id/$index.jpg") ?: continue
                val url = try {
                    host.upload(herbId, jpeg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (giveUp) continue else return
                }
                files.append("jobs/$id/job.txt", "uploaded=$index $url\n")
                done[index] = url
                _progress.update { it + (id to SubmitProgress.Uploading(done.size, total)) }
            }
        } else if (!giveUp) {
            return
        }

        val images = done.values.map { UploadedImage(it, uploader, location) }
        if (images.isNotEmpty() && job["listed"] == null) {
            try {
                contributions.addImages(herbId, images)
                files.append("jobs/$id/job.txt", "listed=1\n")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!giveUp) return
            }
        }
        if (images.isNotEmpty()) analytics.log(AnalyticsEvent.PhotosShared(herbId, images.size))
        notifier.uploadFinished(herbId, job["speciesName"], images.size, total - images.size)
        _progress.update { it + (id to SubmitProgress.Finished(images.size, total - images.size)) }
        files.delete("jobs/$id")
    }

    companion object {
        /** Runs before a job gives up: launches, reconnections and WorkManager retries. */
        const val MAX_ATTEMPTS = 8
    }
}
