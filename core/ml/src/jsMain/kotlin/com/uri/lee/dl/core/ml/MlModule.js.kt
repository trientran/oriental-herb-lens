package com.uri.lee.dl.core.ml

import com.uri.lee.dl.domain.media.ImageCompressor
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ImageCropper
import com.uri.lee.dl.domain.ml.ImageEmbedderLoader
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.PhotoReader
import com.uri.lee.dl.domain.moderation.PlantCheck
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The browser: the herb model in LiteRT.js. ML Kit has no web SDK, so there is no object
 * detection (no "Pick a plant") and no plant check before sharing.
 * Needs a [WebModelSource] in the graph.
 */
actual val mlModule: Module = module {
    single<HerbClassifier> { LiteRtHerbClassifier(get()) }
    single<ObjectFinder> { NoObjectFinder }
    single<ImageCropper> { WebImageCropper() }
    single<PlantCheck> { NoPlantCheck }
    single<PhotoReader> { WebPhotoReader() }
    single<ImageCompressor> { WebImageCompressor() }
    single<ImageEmbedderLoader> { LiteRtEmbedderLoader() }
}

private object NoObjectFinder : ObjectFinder {
    override val isAvailable = false

    override suspend fun find(image: ClassifierImage, fromCamera: Boolean): List<FoundObject> = emptyList()
}

/**
 * ML Kit's general labeller has no web version, and the herb model can't stand in for it: it only
 * knows its 2,721 species, so it refused other plants (a fern). Photos shared from the web rely
 * on reports and the administrator's review instead (tools/moderate.py).
 */
private object NoPlantCheck : PlantCheck {
    override suspend fun showsPlant(image: LocalImage): Boolean = true
}
