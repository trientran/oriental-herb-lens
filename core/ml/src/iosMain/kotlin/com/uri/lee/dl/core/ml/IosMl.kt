package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.Classification
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class IosHerbClassifier(
    private val labeler: NativeHerbLabeler,
    private val models: HerbModelLocator,
) : HerbClassifier {

    override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> {
        val path = when (val model = models.current()) {
            is HerbModelFile.Installed -> model.path
            is HerbModelFile.Bundled -> NSBundle.mainBundle.pathForResource(model.name.substringBeforeLast('.'), model.name.substringAfterLast('.'))
                ?: error("${model.name} is missing from the app bundle")
        }
        return suspendCancellableCoroutine { continuation ->
            labeler.label((image as IosClassifierImage).image, path, minConfidence, maxResults) { labels, error ->
                if (labels != null) continuation.resume(labels.map { Classification(it.text, it.confidence) })
                else continuation.resumeWithException(IllegalStateException(error ?: "Labelling failed"))
            }
        }
    }
}

/** Crops each object (with a small margin) so the herb model sees just that plant, as on Android. */
internal class IosObjectFinder(private val detector: NativeObjectDetector) : ObjectFinder {

    override suspend fun find(image: ClassifierImage, fromCamera: Boolean): List<FoundObject> {
        val upright = (image as IosClassifierImage).image
        val (width, height) = upright.pixelSize()
        val found = suspendCancellableCoroutine { continuation ->
            detector.detect(upright, fromCamera) { objects, error ->
                if (objects != null) continuation.resume(objects)
                else continuation.resumeWithException(IllegalStateException(error ?: "Detection failed"))
            }
        }
        return found.mapNotNull { box ->
            val marginX = (box.right - box.left) / 10
            val marginY = (box.bottom - box.top) / 10
            val left = (box.left - marginX).coerceIn(0.0, width - 1.0)
            val top = (box.top - marginY).coerceIn(0.0, height - 1.0)
            val right = (box.right + marginX).coerceIn(left + 1, width.toDouble())
            val bottom = (box.bottom + marginY).coerceIn(top + 1, height.toDouble())
            if (right - left < MIN_SIDE || bottom - top < MIN_SIDE) return@mapNotNull null
            val crop = upright.cropped(left, top, right - left, bottom - top) ?: return@mapNotNull null
            FoundObject(
                region = Region(
                    (box.left / width).toFloat(), (box.top / height).toFloat(),
                    (box.right / width).toFloat(), (box.bottom / height).toFloat(),
                ),
                image = IosClassifierImage(crop),
                trackingId = box.trackingId.takeIf { it >= 0 },
            )
        }
    }

    private companion object {
        const val MIN_SIDE = 48.0
    }
}
