package com.uri.lee.dl.feature.contribute

import com.uri.lee.dl.core.common.ApplicationScope
import com.uri.lee.dl.domain.media.LocalImage
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.usecase.SubmitImagesUseCase
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeContributionRepository
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.species
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContributeViewModelTest : MainDispatcherTest() {

    private data class Photo(override val uri: String) : LocalImage

    private val auth = FakeAuthRepository()
    private val contributions = FakeContributionRepository()
    private val unreadable = mutableSetOf<String>()
    private val submit = SubmitImagesUseCase(
        compressor = { image -> image.uri.takeUnless { it in unreadable }?.encodeToByteArray() },
        host = { id, jpeg -> "https://r2/$id/${jpeg.decodeToString()}" },
        contributions = contributions,
        auth = auth,
    )

    private fun viewModel() = ContributeViewModel(
        herbId = 5,
        catalog = FakeSpeciesRepository(listOf(species(5, "Polyscias fruticosa", vi = listOf("Đinh lăng")))),
        auth = auth,
        submitImages = submit,
        addresses = { "Hà Nội" },
        appScope = ApplicationScope(CoroutineScope(testDispatcher)),
    )

    @Test
    fun `shows which species the photos are for`() {
        assertEquals("Đinh lăng", viewModel().state.value.speciesName)
    }

    @Test
    fun `picking the same photo twice keeps one`() {
        val viewModel = viewModel()
        viewModel.onAction(ContributeAction.PhotosPicked(listOf(Photo("a"), Photo("b"))))
        viewModel.onAction(ContributeAction.PhotosPicked(listOf(Photo("b"), Photo("c"))))

        assertEquals(listOf("a", "b", "c"), viewModel.state.value.photos.map { it.uri })

        viewModel.onAction(ContributeAction.RemovePhoto(Photo("b")))
        assertEquals(listOf("a", "c"), viewModel.state.value.photos.map { it.uri })
    }

    @Test
    fun `a picked place gets its address`() {
        val viewModel = viewModel()

        viewModel.onAction(ContributeAction.LocationPicked(GeoLocation(21.0, 105.8)))

        assertEquals(PickedLocation(GeoLocation(21.0, 105.8), "Hà Nội"), viewModel.state.value.location)
    }

    @Test
    fun `uploads every photo with the place and reports the result`() {
        val viewModel = viewModel()
        viewModel.onAction(ContributeAction.PhotosPicked(listOf(Photo("a"), Photo("b"))))
        viewModel.onAction(ContributeAction.LocationPicked(GeoLocation(21.0, 105.8)))

        viewModel.onAction(ContributeAction.Upload)

        assertEquals(UploadPhase.Finished(uploaded = 2, failed = 0), viewModel.state.value.phase)
        val (herbId, images) = contributions.images.single()
        assertEquals(5L, herbId)
        assertEquals(listOf("https://r2/5/a", "https://r2/5/b"), images.map { it.url })
        assertTrue(images.all { it.location == GeoLocation(21.0, 105.8) })
    }

    @Test
    fun `a photo that can't be read is counted as failed`() {
        unreadable += "b"
        val viewModel = viewModel()
        viewModel.onAction(ContributeAction.PhotosPicked(listOf(Photo("a"), Photo("b"))))
        viewModel.onAction(ContributeAction.LocationPicked(GeoLocation(21.0, 105.8)))

        viewModel.onAction(ContributeAction.Upload)

        assertEquals(UploadPhase.Finished(uploaded = 1, failed = 1), viewModel.state.value.phase)
    }

    @Test
    fun `photos need a place before they can be uploaded`() {
        val viewModel = viewModel()
        viewModel.onAction(ContributeAction.PhotosPicked(listOf(Photo("a"))))

        assertFalse(viewModel.state.value.canUpload)
        viewModel.onAction(ContributeAction.Upload)
        assertEquals(UploadPhase.Editing, viewModel.state.value.phase)

        viewModel.onAction(ContributeAction.LocationPicked(GeoLocation(21.0, 105.8)))
        assertTrue(viewModel.state.value.canUpload)
    }

    @Test
    fun `signed out users can't upload`() {
        auth.userId.value = null
        val viewModel = viewModel()
        viewModel.onAction(ContributeAction.PhotosPicked(listOf(Photo("a"))))

        assertFalse(viewModel.state.value.canUpload)
        viewModel.onAction(ContributeAction.Upload)
        assertEquals(UploadPhase.Editing, viewModel.state.value.phase)
    }
}
