package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.Classification
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.species
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentifyPlantsUseCaseTest {

    private data class Crop(val name: String) : ClassifierImage

    private val leaf = FoundObject(Region(0f, 0f, 0.5f, 0.5f), Crop("leaf"), trackingId = null)
    private val pot = FoundObject(Region(0.5f, 0.5f, 1f, 1f), Crop("pot"), trackingId = null)
    private val flower = FoundObject(Region(0.2f, 0.6f, 0.4f, 0.9f), Crop("flower"), trackingId = null)

    private val finder = object : ObjectFinder {
        override suspend fun find(image: ClassifierImage, fromCamera: Boolean) = listOf(leaf, pot, flower)
    }
    private val classifier = object : com.uri.lee.dl.domain.ml.HerbClassifier {
        override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int) =
            when ((image as Crop).name) {
                "leaf" -> listOf(Classification("1", 0.8f))
                "flower" -> listOf(Classification("2", 0.95f))
                else -> emptyList() // the pot isn't a herb
            }
    }
    private val recognize = RecognizeHerbsUseCase(classifier, FakeSpeciesRepository(listOf(species(1, "A"), species(2, "B"))))

    @Test
    fun `each plant is identified on its own and recognised ones come first`() = runTest {
        val plants = IdentifyPlantsUseCase(finder, recognize)(Crop("photo"), minConfidence = 0.5f)

        assertEquals(listOf(flower.region, leaf.region, pot.region), plants.map { it.region })
        assertEquals(listOf("2", "1", null), plants.map { it.herbs.firstOrNull()?.label })
        assertEquals(Crop("pot"), plants.last().image) // still shown, so the user sees it was looked at
    }

    @Test
    fun `nothing found means nothing identified`() = runTest {
        val none = object : ObjectFinder {
            override suspend fun find(image: ClassifierImage, fromCamera: Boolean) = emptyList<FoundObject>()
        }
        assertEquals(emptyList(), IdentifyPlantsUseCase(none, recognize)(Crop("photo"), 0.5f))
    }
}
