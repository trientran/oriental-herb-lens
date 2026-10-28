package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.model.Classification

object FakeImage : ClassifierImage

/** Returns [results], filtered and capped the way a real classifier would be. */
class FakeHerbClassifier(var results: List<Classification> = emptyList(), var known: Set<Long> = emptySet()) : HerbClassifier {
    var lastMinConfidence: Float? = null
    var lastMaxResults: Int? = null

    override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int): List<Classification> {
        lastMinConfidence = minConfidence
        lastMaxResults = maxResults
        return results.filter { it.confidence >= minConfidence }.take(maxResults)
    }

    override suspend fun knownSpecies(): Set<Long> = known
}
