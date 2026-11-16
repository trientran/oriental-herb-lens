package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.speciesKeysOf
import com.uri.lee.dl.domain.ml.LABELS_TAIL_BYTES
import com.uri.lee.dl.domain.ml.storedZipEntry
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.Classification
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSMakeRange
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.subdataWithRange
import platform.posix.memcpy
import platform.Foundation.NSBundle
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class IosHerbClassifier(
    private val labeler: NativeHerbLabeler,
    private val models: HerbModelLocator,
) : HerbClassifier {

    private val mutex = Mutex()
    private var known: Pair<String, Set<Long>>? = null

    private fun HerbModelFile.path(): String = when (this) {
        is HerbModelFile.Installed -> path
        is HerbModelFile.Bundled -> NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.'))
            ?: error("$name is missing from the app bundle")
    }

    /** The labels.txt the model's metadata carries, read once per model file from its end. */
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun knownSpecies(): Set<Long> = mutex.withLock {
        val model = models.current()
        known?.takeIf { it.first == model.cacheKey }?.second ?: run {
            // Mapped, not read: only the end of the file is copied
            val file = NSData.dataWithContentsOfFile(model.path(), NSDataReadingMappedIfSafe, null) ?: return@withLock emptySet()
            val size = file.length.toLong()
            val offset = (size - LABELS_TAIL_BYTES).coerceAtLeast(0)
            val data = file.subdataWithRange(NSMakeRange(offset.toULong(), (size - offset).toULong()))
            val tail = ByteArray(data.length.toInt())
            if (tail.isNotEmpty()) tail.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            speciesKeysOf(storedZipEntry(tail, offset, "labels.txt")).also { known = model.cacheKey to it }
        }
    }

    override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> {
        val path = models.current().path()
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
