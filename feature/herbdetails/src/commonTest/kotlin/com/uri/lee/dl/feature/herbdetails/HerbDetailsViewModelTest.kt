package com.uri.lee.dl.feature.herbdetails

import com.uri.lee.dl.domain.analytics.Analytics
import com.uri.lee.dl.domain.analytics.AnalyticsEvent
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.moderation.ReportReason
import com.uri.lee.dl.domain.repository.ReferencePhotoRepository
import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeAuthRepository
import com.uri.lee.dl.testing.fakes.FakeModerationRepository
import com.uri.lee.dl.testing.fakes.FakePhotoRepository
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.testing.fakes.species
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HerbDetailsViewModelTest : MainDispatcherTest() {

    private val catalog = FakeSpeciesRepository(listOf(species(5, "Polyscias fruticosa", vi = listOf("Đinh lăng"))))
    private val photos = FakePhotoRepository()
    private val gbifPhoto = SpeciesPhoto("https://inat/1/original.jpg", "https://inat/1/medium.jpg", PhotoSource.GBIF)
    private var gbifAvailable = true
    private val gbif = object : ReferencePhotoRepository {
        override suspend fun photos(speciesId: Long, limit: Int) =
            if (gbifAvailable) listOf(gbifPhoto) else throw IllegalStateException("offline")
    }
    private val library = FakeUserLibraryRepository()

    private val moderation = FakeModerationRepository()
    private val auth = FakeAuthRepository()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun log(event: AnalyticsEvent) { events += event }
        override fun screen(name: String) = Unit
    }
    private fun viewModel(id: Long = 5) = HerbDetailsViewModel(id, catalog, photos, gbif, library, moderation, auth, addresses = { if (it.latitude > 0) "Hà Nội" else null }, analytics = analytics)

    @Test
    fun `reporting follows the sign-in state`() {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isSignedIn)

        auth.userId.value = null

        assertFalse(viewModel.state.value.isSignedIn)
    }

    @Test
    fun `opening the full details and then GBIF is counted`() {
        val viewModel = viewModel()
        events.clear()

        viewModel.onAction(HerbDetailsAction.InfoOpened)
        viewModel.onAction(HerbDetailsAction.GbifOpened)

        assertEquals(listOf("view_species_info", "open_gbif"), events.map { it.name })
        assertEquals(listOf(5L, 5L), events.map { it.parameters["species_id"] })
    }

    @Test
    fun `shows the species from the catalog`() {
        assertEquals("Đinh lăng", viewModel().state.value.species?.preferredVietnameseName)
    }

    @Test
    fun `user photos come first then GBIF photos`() {
        val viewModel = viewModel()
        val photo = SpeciesPhoto("https://r2/p.jpg", "https://r2/p.jpg", PhotoSource.USER, uploaderId = "u1")

        photos.photos.value = mapOf(5L to listOf(photo))

        assertEquals(listOf(photo, gbifPhoto), viewModel.state.value.photos)
    }

    @Test
    fun `a reported photo is hidden and the report sent`() {
        val viewModel = viewModel()
        val photo = SpeciesPhoto("https://r2/p.jpg", "https://r2/p.jpg", PhotoSource.USER, uploaderId = "u1")
        photos.photos.value = mapOf(5L to listOf(photo))
        viewModel.onAction(HerbDetailsAction.ViewPhoto(0))

        viewModel.onAction(HerbDetailsAction.Report(photo, ReportReason.SEXUAL_OR_VIOLENT))

        assertEquals(listOf(gbifPhoto), viewModel.state.value.photos)
        assertEquals(listOf(photo.url to ReportReason.SEXUAL_OR_VIOLENT), moderation.reports)
        assertEquals(ModerationNotice.REPORTED, viewModel.state.value.notice)
        assertEquals(null, viewModel.state.value.viewingPhoto)
    }

    @Test
    fun `hiding a contributor hides all their photos`() {
        val viewModel = viewModel()
        val theirs = SpeciesPhoto("https://r2/a.jpg", "https://r2/a.jpg", PhotoSource.USER, uploaderId = "u1")
        val others = SpeciesPhoto("https://r2/b.jpg", "https://r2/b.jpg", PhotoSource.USER, uploaderId = "u2")
        photos.photos.value = mapOf(5L to listOf(theirs, others))

        viewModel.onAction(HerbDetailsAction.HideContributor("u1"))

        assertEquals(listOf(others, gbifPhoto), viewModel.state.value.photos)
        assertEquals(ModerationNotice.CONTRIBUTOR_HIDDEN, viewModel.state.value.notice)
    }

    @Test
    fun `offline the user photos still show with no error`() {
        gbifAvailable = false
        val viewModel = viewModel()
        val photo = SpeciesPhoto("https://r2/p.jpg", "https://r2/p.jpg", PhotoSource.USER)
        photos.photos.value = mapOf(5L to listOf(photo))

        assertEquals(listOf(photo), viewModel.state.value.photos)
        assertFalse(viewModel.state.value.hasError)
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
        assertEquals(emptyList(), library.favorites.value)
    }

    @Test
    fun `a failed favourite write is rolled back`() {
        library.failWrites = true
        val viewModel = viewModel()

        viewModel.onAction(HerbDetailsAction.ToggleFavorite)

        assertFalse(viewModel.state.value.isFavorite)
    }

    @Test
    fun `the photo viewer opens at a photo and closes`() {
        val viewModel = viewModel()

        viewModel.onAction(HerbDetailsAction.ViewPhoto(0))
        assertEquals(0, viewModel.state.value.viewingPhoto)

        viewModel.onAction(HerbDetailsAction.ViewPhoto(null))
        assertNull(viewModel.state.value.viewingPhoto)
    }

    @Test
    fun `a pin shows its place with the address and a second tap closes it`() {
        val viewModel = viewModel()
        val hanoi = GeoLocation(21.03, 105.85)

        viewModel.onAction(HerbDetailsAction.SelectPlace(hanoi))
        assertEquals(SelectedPlace(hanoi, "Hà Nội"), viewModel.state.value.place)

        viewModel.onAction(HerbDetailsAction.SelectPlace(hanoi))
        assertNull(viewModel.state.value.place)
    }
}
