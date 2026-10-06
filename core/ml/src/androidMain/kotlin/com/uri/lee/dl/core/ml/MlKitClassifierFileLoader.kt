package com.uri.lee.dl.core.ml

import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.uri.lee.dl.domain.ml.ClassifierFileLoader
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.model.Classification
import kotlinx.coroutines.tasks.await

/** ML Kit's custom image labeler on a model file; it takes labels and normalisation from the metadata. */
internal class MlKitClassifierFileLoader : ClassifierFileLoader {
    override suspend fun load(model: String): HerbClassifier = object : HerbClassifier {
        private val localModel = LocalModel.Builder().setAbsoluteFilePath(model).build()

        override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> {
            val options = CustomImageLabelerOptions.Builder(localModel).setConfidenceThreshold(minConfidence).setMaxResultCount(maxResults).build()
            return ImageLabeling.getClient(options).use { labeler ->
                labeler.process((image as MlKitClassifierImage).inputImage).await().map { Classification(it.text, it.confidence) }
            }
        }
    }
}
