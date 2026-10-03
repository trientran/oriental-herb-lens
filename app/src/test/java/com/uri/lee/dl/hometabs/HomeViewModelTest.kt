package com.uri.lee.dl.hometabs

import com.uri.lee.dl.fakes.FakeSpeciesRepository
import com.uri.lee.dl.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.fakes.species
import com.uri.lee.dl.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeSpeciesRepository((1L..70L).map { species(it, "Species %03d".format(it), vi = listOf("Cây %03d".format(it))) })
    private val library = FakeUserLibraryRepository()

    private fun viewModel() = HomeViewModel(catalog, library, sortByVietnameseName = true)

    @Test
    fun `pages through the whole catalog, then stops`() {
        val viewModel = viewModel()
        assertEquals((1L..30L).toList(), viewModel.state.value.allSpecies.map { it.id })

        viewModel.onAction(HomeAction.LoadMore)
        viewModel.onAction(HomeAction.LoadMore)
        assertEquals((1L..70L).toList(), viewModel.state.value.allSpecies.map { it.id })

        viewModel.onAction(HomeAction.LoadMore)
        assertEquals(70, viewModel.state.value.allSpecies.size)
    }

    @Test
    fun `favourites and history keep the library's order and follow changes`() {
        library.favorites.value = listOf(3L, 1L)
        val viewModel = viewModel()
        assertEquals(listOf(3L, 1L), viewModel.state.value.favorites.map { it.id })

        library.history.value = listOf(7L, 2L, 999L) // 999 isn't in the catalog
        assertEquals(listOf(7L, 2L), viewModel.state.value.history.map { it.id })
    }
}
