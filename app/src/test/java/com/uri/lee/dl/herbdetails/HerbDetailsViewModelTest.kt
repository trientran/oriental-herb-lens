package com.uri.lee.dl.herbdetails

import androidx.lifecycle.SavedStateHandle
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.repository.ReferencePhotoRepository
import com.uri.lee.dl.fakes.FakePhotoRepository
import com.uri.lee.dl.fakes.FakeSpeciesRepository
import com.uri.lee.dl.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.fakes.species
import com.uri.lee.dl.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HerbDetailsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeSpeciesRepository(listOf(species(5, "Polyscias fruticosa", vi = listOf("Đinh lăng"))))
    private val photos = FakePhotoRepository()
    private val gbifPhoto = SpeciesPhoto("https://inat/1/original.jpg", "https://inat/1/medium.jpg", PhotoSource.GBIF)
    private var gbifAvailable = true
    private val gbif = object : ReferencePhotoRepository {
        override suspend fun photos(speciesId: Long, limit: Int) =
            if (gbifAvailable) listOf(gbifPhoto) else throw java.io.IOException("offline")
    }
    private val library = FakeUserLibraryRepository()

    private fun viewModel(id: Long = 5) =
        HerbDetailsViewModel(SavedStateHandle(mapOf(HERB_ID to id)), catalog, photos, gbif, library)

    @Test
    fun `shows the species from the catalog`() {
        assertEquals("Đinh lăng", viewModel().state.value.species?.preferredVietnameseName)
    }

    @Test
    fun `user photos come first, then GBIF photos`() {
        val viewModel = viewModel()
        val photo = SpeciesPhoto("https://r2/p.jpg", "https://r2/p.jpg", PhotoSource.USER, uploaderId = "u1")

        photos.photos.value = mapOf(5L to listOf(photo))

        assertEquals(listOf(photo, gbifPhoto), viewModel.state.value.photos)
    }

    @Test
    fun `without GBIF (offline) the user photos still show, with no error`() {
        gbifAvailable = false
        val viewModel = viewModel()
        val photo = SpeciesPhoto("https://r2/p.jpg", "https://r2/p.jpg", PhotoSource.USER)
        photos.photos.value = mapOf(5L to listOf(photo))

        assertEquals(listOf(photo), viewModel.state.value.photos)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `opening a species records it in history`() {
        viewModel()
        assertEquals(listOf(5L), library.history.value)
    }

    @Test
    fun `a species missing from the catalog is reported and not recorded`() {
        val viewModel = viewModel(id = 99)

        assertTrue(viewModel.state.value.notFound)
        assertTrue(library.history.value.isEmpty())
    }

    @Test
    fun `favourite state follows the library and toggles`() {
        library.favorites.value = listOf(5L)
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isFavorite)

        viewModel.onAction(HerbDetailsAction.ToggleFavorite)

        assertFalse(viewModel.state.value.isFavorite)
        assertEquals(emptyList<Long>(), library.favorites.value)
    }

    @Test
    fun `a failed favourite write is rolled back and reported`() {
        library.failWrites = true
        val viewModel = viewModel()

        viewModel.onAction(HerbDetailsAction.ToggleFavorite)

        assertFalse(viewModel.state.value.isFavorite)
        assertNotNull(viewModel.state.value.error)
    }
}
