package com.uri.lee.dl.domain.ml

import com.uri.lee.dl.domain.model.Classification

/**
 * An image in whatever form the platform classifier consumes. Created and unwrapped by the
 * platform layer only, which keeps camera and bitmap types out of the domain.
 */
interface ClassifierImage

interface HerbClassifier {
    /**
     * Labels [image] with the current herb model: at most [maxResults] labels whose confidence is
     * at least [minConfidence], highest confidence first.
     */
    suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification>

    /**
     * The species (GBIF keys) the current herb model can name; the rest of the catalog needs
     * photos before it can learn them. Empty when that can't be told. On the web this loads the model.
     */
    suspend fun knownSpecies(): Set<Long> = emptySet()
}

/** The species keys among a herb model's labels (its embedded labels.txt); other labels are left out. */
fun speciesKeysOf(labels: String?): Set<Long> =
    labels?.lineSequence()?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()
