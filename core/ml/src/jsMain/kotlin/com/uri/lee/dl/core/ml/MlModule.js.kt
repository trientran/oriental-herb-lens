package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.moderation.PlantCheck
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The browser: the herb model in LiteRT.js. ML Kit has no web SDK, so there is no object
 * detection (no "Pick a plant") and the plant check before sharing uses the herb model itself.
 * Needs a [WebModelSource] in the graph.
 */
actual val mlModule: Module = module {
    single<HerbClassifier> { LiteRtHerbClassifier(get()) }
    single<ObjectFinder> { NoObjectFinder }
    single<ImageCropper> { WebImageCropper() }
    single<PlantCheck> { HerbModelPlantCheck(get(), get()) }
    single<PhotoReader> { WebPhotoReader() }
    single<ImageCompressor> { WebImageCompressor() }
}

private object NoObjectFinder : ObjectFinder {
    override val isAvailable = false

    override suspend fun find(image: ClassifierImage, fromCamera: Boolean): List<FoundObject> = emptyList()
}

/**
 * Without ML Kit's general labeller, a photo counts as a plant when the herb model recognises a
 * species in it with some confidence. Stricter than the apps: an unknown plant may be refused.
 */
internal class HerbModelPlantCheck(
    private val reader: PhotoReader,
    private val classifier: HerbClassifier,
) : PlantCheck {
    override suspend fun showsPlant(image: LocalImage): Boolean? {
        val photo = reader.read(image) ?: return null
        return classifier.classify(photo.image, minConfidence = PLANT_SPECIES_CONFIDENCE, maxResults = 1).isNotEmpty()
    }

    private companion object {
        const val PLANT_SPECIES_CONFIDENCE = 0.2f
    }
}
