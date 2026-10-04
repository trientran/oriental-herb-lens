package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.model.RecognizedHerb
import com.uri.lee.dl.domain.repository.SpeciesRepository

/** Classifies an image and names each result from the species catalog. */
class RecognizeHerbsUseCase(
    private val classifier: HerbClassifier,
    private val species: SpeciesRepository,
) {
    suspend operator fun invoke(
        image: ClassifierImage,
        minConfidence: Float,
        maxResults: Int = DEFAULT_MAX_RESULTS,
    ): List<RecognizedHerb> {
        val results = classifier.classify(image, minConfidence, maxResults)
        if (results.isEmpty()) return emptyList()
        val named = species.getAll(results.mapNotNull { it.label.toLongOrNull() })
        return results.map { RecognizedHerb(it.label, it.confidence, named[it.label.toLongOrNull()]) }
    }

    companion object {
        /** ML Kit's own default, which the screens relied on before this use case existed. */
        const val DEFAULT_MAX_RESULTS = 10
    }
}
