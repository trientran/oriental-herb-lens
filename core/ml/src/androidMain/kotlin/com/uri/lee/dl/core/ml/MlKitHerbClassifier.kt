package com.uri.lee.dl.core.ml

import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.model.Classification
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** An ML Kit [InputImage] handed to the classifier. */
class MlKitClassifierImage(val inputImage: InputImage) : ClassifierImage

/**
 * Runs the herb model through ML Kit's custom image labeler, which reads the label list and input
 * normalisation from the model's metadata. One labeler is kept per model file and options, instead
 * of building a new one for every image or camera frame.
 */
internal class MlKitHerbClassifier(private val models: HerbModelLocator) : HerbClassifier {

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
