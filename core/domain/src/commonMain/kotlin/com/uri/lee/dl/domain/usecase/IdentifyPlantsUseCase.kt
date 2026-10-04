package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.RecognizedHerb

/** A plant found in a photo and what it may be, most likely first. */
data class IdentifiedPlant(val region: Region, val herbs: List<RecognizedHerb>)

/**
 * Finds each plant in a photo and identifies it on its own, which is more accurate than
 * classifying the whole photo when it shows several plants or a busy background. Objects the
 * model doesn't recognise as a herb (above [minConfidence]) are left out.
 */
class IdentifyPlantsUseCase(
    private val objects: ObjectFinder,
    private val recognizeHerbs: RecognizeHerbsUseCase,
) {
    suspend operator fun invoke(image: ClassifierImage, minConfidence: Float, maxResults: Int = 3): List<IdentifiedPlant> =
        objects.find(image, fromCamera = false)
            .mapNotNull { found ->
                recognizeHerbs(found.image, minConfidence, maxResults)
                    .takeIf { it.isNotEmpty() }
                    ?.let { IdentifiedPlant(found.region, it) }
            }
            .sortedByDescending { it.herbs.first().confidence }
}
