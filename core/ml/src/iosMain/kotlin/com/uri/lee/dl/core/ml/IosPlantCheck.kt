package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.moderation.PlantCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ML Kit's general labeller through the Swift bridge; its model is bundled with the app. */
internal class IosPlantCheck(private val labeler: NativeGeneralLabeler) : PlantCheck {
    override suspend fun showsPlant(image: LocalImage): Boolean? {
        val upright = withContext(Dispatchers.IO) { load(image)?.uprightScaled(maxDimension = 640.0) } ?: return null
        val labels = suspendCancellableCoroutine { continuation ->
            labeler.labels(upright, PLANT_LABEL_CONFIDENCE) { labels, error ->
                if (labels != null) continuation.resume(labels)
                else continuation.resumeWithException(IllegalStateException(error ?: "Labelling failed"))
            }
        }
        return labels.any { it in PLANT_LABELS }
    }
}
