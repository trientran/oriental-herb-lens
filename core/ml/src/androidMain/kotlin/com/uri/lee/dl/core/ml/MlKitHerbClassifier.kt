package com.uri.lee.dl.core.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.speciesKeysOf
import com.uri.lee.dl.domain.ml.LABELS_TAIL_BYTES
import com.uri.lee.dl.domain.ml.storedZipEntry
import com.uri.lee.dl.domain.model.Classification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * An ML Kit [InputImage] handed to the classifier. With its upright [bitmap], objects found in it
 * can be cropped out and classified on their own.
 */
class MlKitClassifierImage(val inputImage: InputImage, val bitmap: Bitmap? = null) : ClassifierImage {
    constructor(bitmap: Bitmap) : this(InputImage.fromBitmap(bitmap, 0), bitmap)
}

/**
 * Runs the herb model through ML Kit's custom image labeler, which reads the label list and input
 * normalisation from the model's metadata. One labeler is kept per model file and options, instead
 * of building a new one for every image or camera frame.
 */
internal class MlKitHerbClassifier(private val models: HerbModelLocator, private val context: Context) : HerbClassifier {

    private data class LabelerKey(val model: String, val minConfidence: Float, val maxResults: Int)

    private val mutex = Mutex()
    private var cached: Pair<LabelerKey, ImageLabeler>? = null

    override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> {
        val input = (image as MlKitClassifierImage).inputImage
        return labeler(minConfidence, maxResults)
            .process(input)
            .await()
            .map { Classification(label = it.text, confidence = it.confidence) }
    }

    private var known: Pair<String, Set<Long>>? = null

    /** The labels.txt the model's metadata carries, read once per model file from its end. */
    override suspend fun knownSpecies(): Set<Long> = mutex.withLock {
        val model = models.current()
        known?.takeIf { it.first == model.cacheKey }?.second ?: withContext(Dispatchers.IO) {
            val stream: InputStream = when (model) {
                is HerbModelFile.Bundled -> context.assets.open(model.name)
                is HerbModelFile.Installed -> java.io.File(model.path).inputStream()
            }
            stream.use {
                val size = it.available().toLong()
                val offset = (size - LABELS_TAIL_BYTES).coerceAtLeast(0)
                var skipped = 0L
                while (skipped < offset) skipped += it.skip(offset - skipped).takeIf { n -> n > 0 } ?: break
                speciesKeysOf(storedZipEntry(it.readBytes(), skipped, "labels.txt"))
            }.also { known = model.cacheKey to it }
        }
    }

    private suspend fun labeler(minConfidence: Float, maxResults: Int): ImageLabeler = mutex.withLock {
        val model = models.current()
        val key = LabelerKey(model.cacheKey, minConfidence, maxResults)
        cached?.takeIf { it.first == key }?.second ?: run {
            cached?.second?.close()
            val options = CustomImageLabelerOptions.Builder(model.toLocalModel())
                .setConfidenceThreshold(minConfidence)
                .setMaxResultCount(maxResults)
                .build()
            ImageLabeling.getClient(options).also { cached = key to it }
        }
    }

    private fun HerbModelFile.toLocalModel(): LocalModel = when (this) {
        is HerbModelFile.Bundled -> LocalModel.Builder().setAssetFilePath(name).build()
        is HerbModelFile.Installed -> LocalModel.Builder().setAbsoluteFilePath(path).build()
    }
}
