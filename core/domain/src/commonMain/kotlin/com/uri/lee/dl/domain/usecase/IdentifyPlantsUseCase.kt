package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.RecognizedHerb

/** A plant found in a photo, cropped out as [image], and what it may be, most likely first. */
class IdentifiedPlant(val region: Region, val image: ClassifierImage, val herbs: List<RecognizedHerb>)

/**
 * Finds each plant in a photo and identifies it on its own, which is more accurate than
 * classifying the whole photo when it shows several plants or a busy background. Every plant
 * found is returned, even those the model doesn't recognise (above [minConfidence]), so the user
 * can see what was looked at; recognised ones come first, most confident first.
 */
class IdentifyPlantsUseCase(
    private val objects: ObjectFinder,
    private val recognizeHerbs: RecognizeHerbsUseCase,
) {
    suspend operator fun invoke(image: ClassifierImage, minConfidence: Float, maxResults: Int = 3): List<IdentifiedPlant> =
        objects.find(image, fromCamera = false)
            .map { found -> IdentifiedPlant(found.region, found.image, recognizeHerbs(found.image, minConfidence, maxResults)) }
            .sortedByDescending { it.herbs.firstOrNull()?.confidence ?: 0f }
}
