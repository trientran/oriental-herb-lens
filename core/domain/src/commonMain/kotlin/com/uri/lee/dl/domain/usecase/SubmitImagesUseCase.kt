package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.ImageHost
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NotSignedInException : IllegalStateException("Sign in to contribute")

sealed interface SubmitProgress {
    data class Uploading(val uploaded: Int, val total: Int) : SubmitProgress

    /** All images were attempted; [failed] couldn't be read or uploaded. */
    data class Finished(val uploaded: Int, val failed: Int) : SubmitProgress
}

/**
 * Uploads photos of a herb one at a time and attaches every successful one to it. An image that
 * fails is skipped rather than stopping the rest.
 */
class SubmitImagesUseCase(
    private val compressor: ImageCompressor,
    private val host: ImageHost,
    private val contributions: ContributionRepository,
    private val auth: AuthRepository,
) {
    operator fun invoke(herbId: Long, images: List<LocalImage>, location: GeoLocation): Flow<SubmitProgress> = flow {
        val uploaderId = auth.currentUserId ?: throw NotSignedInException()
        val uploaded = mutableListOf<UploadedImage>()
        var failed = 0
        emit(SubmitProgress.Uploading(uploaded = 0, total = images.size))
        for (image in images) {
            try {
                val jpeg = compressor.compress(image)
                if (jpeg == null) {
                    failed++
                } else {
                    uploaded += UploadedImage(host.upload(herbId, jpeg), uploaderId, location)
                    emit(SubmitProgress.Uploading(uploaded.size, images.size))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        if (uploaded.isNotEmpty()) contributions.addImages(herbId, uploaded)
        emit(SubmitProgress.Finished(uploaded = uploaded.size, failed = failed))
    }
}
