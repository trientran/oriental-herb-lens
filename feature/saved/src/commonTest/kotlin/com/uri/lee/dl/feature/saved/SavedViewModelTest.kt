package com.uri.lee.dl.feature.saved

import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.testing.fakes.species
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedViewModelTest : MainDispatcherTest() {

    private val catalog = FakeSpeciesRepository((1L..10L).map { species(it, "Species $it") })
    private val library = FakeUserLibraryRepository()

    @Test
    fun `favourites and history keep the library's order and follow changes`() {
        library.favorites.value = listOf(3L, 1L)
        val viewModel = SavedViewModel(catalog, library)
        assertEquals(listOf(3L, 1L), viewModel.state.value.favorites?.map { it.id })

        library.history.value = listOf(7L, 2L, 999L) // 999 isn't in the catalog
        assertEquals(listOf(7L, 2L), viewModel.state.value.history?.map { it.id })
    }

    @Test
    fun `the selected tab decides which list is shown`() {
        library.favorites.value = listOf(1L)
        library.history.value = listOf(2L)
        val viewModel = SavedViewModel(catalog, library)

        viewModel.onAction(SavedAction.SelectTab(SavedTab.HISTORY))

        assertEquals(listOf(2L), viewModel.state.value.shown?.map { it.id })
    }
}
