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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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

    private val clock = TestTimeSource()
    private var cropped: Region? = null

    private fun viewModel() = ScanViewModel(
        recognizeHerbs = recognize,
        identifyPlants = IdentifyPlantsUseCase(finder, recognize),
        objectFinder = finder,
        cropper = { image, region -> cropped = region; image },
        photoReader = { photo -> if (photo.uri == "broken") null else ReadPhoto(Image(photo.uri), 400, 300) },
        settings = settings,
        time = clock,
    )

    @Test
    fun `whole view identifies each camera frame`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.WHOLE_VIEW))

        viewModel.analyzeFrame(Image("dinh lang"), 0.75f)

        assertEquals(listOf("1"), viewModel.state.value.results.map { it.label })
    }

    @Test
    fun `whole view identifies the square the user can see`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.ViewAspect(0.5f))

        viewModel.analyzeFrame(Image("dinh lang"), 0.75f)

        // A 480 x 640 frame filling a view twice as tall as wide shows 320 px of its width
        val region = cropped!!
        listOf(1f / 6 to region.left, 0.25f to region.top, 5f / 6 to region.right, 0.75f to region.bottom)
            .forEach { (expected, actual) -> assertEquals(expected, actual, absoluteTolerance = 0.0001f) }
    }

    @Test
    fun `holding the camera on a plant for two seconds identifies it and pauses`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))

        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertEquals(listOf(8, 7), viewModel.state.value.objects.map { it.id })
        assertEquals(7, viewModel.state.value.steadyId) // the biggest plant
        assertNull(viewModel.state.value.picked)

        clock += 2.seconds
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        val picked = viewModel.state.value.picked
        assertEquals(7, picked?.id)
        assertEquals(Image("big plant"), picked?.image)
        assertEquals("1", picked?.herbs?.single()?.label)

        // Paused: more frames change nothing until the user scans again
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertEquals(7, viewModel.state.value.picked?.id)
        viewModel.onAction(ScanAction.ClosePicked)
        assertNull(viewModel.state.value.picked)
    }

    @Test
    fun `tapping a plant's dot identifies it straight away`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))
        viewModel.analyzeFrame(Image("garden"), 0.75f)

        viewModel.onAction(ScanAction.SelectObject(8))
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertEquals("2", viewModel.state.value.picked?.herbs?.single()?.label)

        // While paused, another dot from the same frame can be tapped
        viewModel.onAction(ScanAction.SelectObject(7))
        assertEquals("1", viewModel.state.value.picked?.herbs?.single()?.label)
    }

    @Test
    fun `moving the camera starts the two seconds again`() = runTest {
        val moving = mutableListOf(big)
        val viewModel = ScanViewModel(
            recognize, IdentifyPlantsUseCase(finder, recognize),
            objectFinder = object : ObjectFinder {
                override suspend fun find(image: ClassifierImage, fromCamera: Boolean) = moving.toList()
            },
            cropper = { image, _ -> image }, photoReader = { null }, settings = settings, time = clock,
        )
        viewModel.onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))
        viewModel.analyzeFrame(Image("garden"), 0.75f)

        clock += 1.5.seconds
        moving[0] = FoundObject(Region(0.2f, 0.2f, 1f, 1f), Image("big plant"), trackingId = 7)
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        clock += 1.seconds
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertNull(viewModel.state.value.picked)

        clock += 1.seconds
        viewModel.analyzeFrame(Image("garden"), 0.75f)
        assertEquals(7, viewModel.state.value.picked?.id)
    }

    @Test
    fun `the chosen mode is remembered`() = runTest {
        viewModel().onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))

        assertTrue(settings.scanSettings.value.detectObjectsInSingleImage)
    }

    @Test
    fun `one photo in pick a plant mode shows each plant with the most likely herb first`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ScanAction.SetMode(ScanMode.PICK_PLANT))

        viewModel.onAction(ScanAction.PhotosPicked(listOf(Picked("garden"))))

        val state = viewModel.state.value
        assertEquals(ScanSource.Photo("garden", aspect = 400f / 300), state.source)
        assertEquals(2, state.objects.size)
        assertEquals("1", state.picked?.herbs?.single()?.label)

        viewModel.onAction(ScanAction.SelectObject(1))
        assertEquals("2", viewModel.state.value.picked?.herbs?.single()?.label)
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
