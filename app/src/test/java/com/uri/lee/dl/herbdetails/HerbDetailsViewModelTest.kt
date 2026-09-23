package com.uri.lee.dl.herbdetails

import androidx.lifecycle.SavedStateHandle
import com.uri.lee.dl.HERB_ID
import com.uri.lee.dl.fakes.FakeHerbRepository
import com.uri.lee.dl.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.fakes.herbProfile
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

    private val herbs = FakeHerbRepository(listOf(herbProfile(5, vi = "Đinh lăng")))
    private val library = FakeUserLibraryRepository()

    private fun viewModel(id: Long = 5) =
        HerbDetailsViewModel(SavedStateHandle(mapOf(HERB_ID to id)), herbs, library)

    @Test
    fun `shows the herb and follows later changes`() {
        val viewModel = viewModel()
        assertEquals("Đinh lăng", viewModel.state.value.profile?.vietnameseName)

        herbs.profiles.value = mapOf(5L to herbProfile(5, vi = "Cây đinh lăng"))

        assertEquals("Cây đinh lăng", viewModel.state.value.profile?.vietnameseName)
    }

    @Test
    fun `opening a herb records it in history once`() {
        viewModel()
        herbs.profiles.value = mapOf(5L to herbProfile(5, vi = "changed"))

        assertEquals(listOf(5L), library.history.value)
    }

    @Test
    fun `a herb that doesn't exist isn't added to history`() {
        viewModel(id = 99)

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
