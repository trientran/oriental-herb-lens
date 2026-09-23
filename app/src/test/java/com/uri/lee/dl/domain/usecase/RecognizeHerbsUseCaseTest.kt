package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.model.Classification
import com.uri.lee.dl.domain.model.RecognizedHerb
import com.uri.lee.dl.fakes.FakeHerbClassifier
import com.uri.lee.dl.fakes.FakeImage
import com.uri.lee.dl.fakes.FakeSpeciesRepository
import com.uri.lee.dl.fakes.species
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizeHerbsUseCaseTest {

    private val dinhLang = species(3035652, "Polyscias fruticosa", vi = listOf("Đinh lăng"))
    private val tiPlant = species(2766278, "Cordyline fruticosa", vi = listOf("Huyết dụng"))
    private val catalog = FakeSpeciesRepository(listOf(dinhLang, tiPlant))
    private val classifier = FakeHerbClassifier()
    private val recognize = RecognizeHerbsUseCase(classifier, catalog)

    @Test
    fun `results keep classifier order and carry their catalog entry`() = runTest {
        classifier.results = listOf(Classification("2766278", 0.9f), Classification("3035652", 0.8f))

        assertEquals(
            listOf(RecognizedHerb("2766278", 0.9f, tiPlant), RecognizedHerb("3035652", 0.8f, dinhLang)),
            recognize(FakeImage, minConfidence = 0.5f),
        )
    }

    @Test
    fun `a label missing from the catalog is still returned, without species`() = runTest {
        classifier.results = listOf(Classification("999", 0.9f), Classification("not-a-key", 0.8f))

        assertEquals(
            listOf(RecognizedHerb("999", 0.9f, null), RecognizedHerb("not-a-key", 0.8f, null)),
            recognize(FakeImage, minConfidence = 0.5f),
        )
    }

    @Test
    fun `threshold and result cap are passed to the classifier`() = runTest {
        recognize(FakeImage, minConfidence = 0.65f, maxResults = 1)

        assertEquals(0.65f, classifier.lastMinConfidence)
        assertEquals(1, classifier.lastMaxResults)
    }

    @Test
    fun `defaults to ML Kit's own cap of ten results`() = runTest {
        recognize(FakeImage, minConfidence = 0.5f)

        assertEquals(10, classifier.lastMaxResults)
    }

    @Test
    fun `no results means no catalog lookup`() = runTest {
        assertEquals(emptyList<RecognizedHerb>(), recognize(FakeImage, minConfidence = 0.5f))
        assertEquals(0, catalog.lookups)
    }
}
