package com.uri.lee.dl.feature.scan

import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.ml.ClassifierImage
import com.uri.lee.dl.domain.ml.FoundObject
import com.uri.lee.dl.domain.ml.HerbClassifier
import com.uri.lee.dl.domain.ml.ObjectFinder
import com.uri.lee.dl.domain.ml.ReadPhoto
import com.uri.lee.dl.domain.ml.Region
import com.uri.lee.dl.domain.model.Classification
import com.uri.lee.dl.domain.usecase.IdentifyPlantsUseCase
import com.uri.lee.dl.domain.usecase.RecognizeHerbsUseCase
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeSettingsRepository
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.species
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanViewModelTest : MainDispatcherTest() {

    /** An image whose name is what the fake classifier "sees". */
    private data class Image(val name: String) : ClassifierImage
    private data class Picked(override val uri: String) : LocalImage

    private val classifier = object : HerbClassifier {
        override suspend fun classify(image: ClassifierImage, minConfidence: Float, maxResults: Int) =
            when ((image as Image).name) {
                "dinh lang", "big plant" -> listOf(Classification("1", 0.9f))
                "small plant" -> listOf(Classification("2", 0.8f))
                else -> emptyList()
            }
    }
    private val big = FoundObject(Region(0f, 0f, 0.8f, 0.8f), Image("big plant"), trackingId = 7)
    private val small = FoundObject(Region(0.8f, 0.8f, 1f, 1f), Image("small plant"), trackingId = 8)
    private val finder = object : ObjectFinder {
        override suspend fun find(image: ClassifierImage, fromCamera: Boolean) = listOf(small, big)
    }
    private val settings = FakeSettingsRepository()
    private val recognize = RecognizeHerbsUseCase(classifier, FakeSpeciesRepository(listOf(species(1, "Polyscias fruticosa"), species(2, "Mentha arvensis"))))

    private fun viewModel() = ScanViewModel(
        recognizeHerbs = recognize,
        identifyPlants = IdentifyPlantsUseCase(finder, recognize),
        objectFinder = finder,
        photoReader = { photo -> if (photo.uri == "broken") null else ReadPhoto(Image(photo.uri), 400, 300) },
        settings = settings,
    )

    @Test
    fun `whole view identifies each camera frame`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.WHOLE_VIEW))

        viewModel.analyzeFrame(Image("dinh lang"), 0.75f)

        assertEquals(listOf("1"), viewModel.state.value.results.map { it.label })
    }

    @Test
    fun `pick a plant starts on the biggest plant and keeps the user's choice`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))

        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertEquals(listOf(8, 7), viewModel.state.value.objects.map { it.id })
        assertEquals(7, viewModel.state.value.selectedId)
        assertEquals("1", viewModel.state.value.results.single().label)

        viewModel.onAction(ScanAction.SelectObject(8))
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertEquals("2", viewModel.state.value.results.single().label)
    }

    @Test
    fun `the chosen mode is remembered`() = runTest {
        viewModel().onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))

        assertTrue(settings.scanSettings.value.detectObjectsInSingleImage)
    }

    @Test
    fun `one photo in pick a plant mode shows each recognised plant`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))

        viewModel.onAction(ScanAction.PhotosPicked(listOf(Picked("garden"))))

        val state = viewModel.state.value
        assertEquals(ScanSource.Photo("garden", aspect = 400f / 300), state.source)
        assertEquals(2, state.objects.size)
        assertEquals("1", state.results.single().label) // the most confident plant first
    }

    @Test
    fun `several photos are identified one by one`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ScanAction.PhotosPicked(listOf(Picked("dinh lang"), Picked("wall"), Picked("broken"))))

        val items = (viewModel.state.value.source as ScanSource.Photos).items
        assertEquals(listOf("1"), items[0].herbs?.map { it.label })
        assertEquals(emptyList(), items[1].herbs)
        assertTrue(items[2].failed)
    }

    @Test
    fun `camera frames are ignored while a photo is shown`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.PhotosPicked(listOf(Picked("wall"))))

        viewModel.analyzeFrame(Image("dinh lang"), 0.75f)
        assertTrue(viewModel.state.value.results.isEmpty())

        viewModel.onAction(ScanAction.BackToCamera)
        assertEquals(ScanSource.Camera, viewModel.state.value.source)
    }
}
