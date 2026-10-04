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
}
