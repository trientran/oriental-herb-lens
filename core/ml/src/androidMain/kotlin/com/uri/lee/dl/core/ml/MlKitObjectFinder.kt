package com.uri.lee.dl.core.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.Region
import kotlinx.coroutines.tasks.await

/**
 * ML Kit's built-in object detector, which finds up to five prominent objects. Each is cropped
 * from the upright bitmap (with a small margin) so the herb model sees just that plant.
 */
internal class MlKitObjectFinder : ObjectFinder {

    private val camera: ObjectDetector by lazy { detector(ObjectDetectorOptions.STREAM_MODE) }
    private val photo: ObjectDetector by lazy { detector(ObjectDetectorOptions.SINGLE_IMAGE_MODE) }

    override suspend fun find(image: ClassifierImage, fromCamera: Boolean): List<FoundObject> {
        val mlKit = image as MlKitClassifierImage
        val bitmap = mlKit.bitmap ?: return emptyList() // nothing to crop from
        val found = (if (fromCamera) camera else photo).process(mlKit.inputImage).await()
        return found.mapNotNull { detected ->
            val crop = crop(bitmap, detected.boundingBox) ?: return@mapNotNull null
            FoundObject(
                region = Region(
                    left = detected.boundingBox.left / bitmap.width.toFloat(),
                    top = detected.boundingBox.top / bitmap.height.toFloat(),
                    right = detected.boundingBox.right / bitmap.width.toFloat(),
                    bottom = detected.boundingBox.bottom / bitmap.height.toFloat(),
                ),
                image = MlKitClassifierImage(crop),
                trackingId = detected.trackingId,
            )
        }
    }

    private fun crop(bitmap: Bitmap, box: Rect): Bitmap? {
        val marginX = box.width() / 10
        val marginY = box.height() / 10
        val left = (box.left - marginX).coerceIn(0, bitmap.width - 1)
        val top = (box.top - marginY).coerceIn(0, bitmap.height - 1)
        val right = (box.right + marginX).coerceIn(left + 1, bitmap.width)
        val bottom = (box.bottom + marginY).coerceIn(top + 1, bitmap.height)
        if (right - left < MIN_SIDE || bottom - top < MIN_SIDE) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun detector(mode: Int) = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder().setDetectorMode(mode).enableMultipleObjects().build(),
    )

    private companion object {
        /** Smaller crops carry too little detail to classify. */
        const val MIN_SIDE = 48
    }
}
