package com.uri.lee.dl.feature.browse

import com.uri.lee.dl.testing.MainDispatcherTest
import com.uri.lee.dl.testing.fakes.FakeHerbClassifier
import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.species
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest : MainDispatcherTest(StandardTestDispatcher()) {

    private fun padded(n: Long) = n.toString().padStart(3, '0')
    private val catalog = FakeSpeciesRepository(
        (1L..90L).map { species(it, "Species ${padded(it)}", vi = listOf("Cây ${padded(it)}")) } +
            species(3035652, "Polyscias fruticosa", vi = listOf("Đinh lăng")),
    )

    private val classifier = FakeHerbClassifier(known = (1L..88L).toSet())

    private fun viewModel() = BrowseViewModel(catalog, sortByVietnameseName = true, classifier)

    @Test
    fun `needs photos lists the species the model does not know yet`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(BrowseAction.SetNeedsPhotos(true))
        advanceUntilIdle()

        assertEquals(listOf("Cây 089", "Cây 090", "Đinh lăng"), viewModel.state.value.needsPhotosSpecies?.map { it.vietnameseNames.first() })
        viewModel.onAction(BrowseAction.SetNeedsPhotos(false))
        assertFalse(viewModel.state.value.needsPhotos)
    }

    @Test
    fun `needs photos is not offered when the model's species are unknown`() = runTest(testDispatcher) {
        classifier.known = emptySet()
        val viewModel = viewModel()
        viewModel.onAction(BrowseAction.SetNeedsPhotos(true))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.needsPhotos)
        assertFalse(viewModel.state.value.needsPhotosAvailable)
    }

    @Test
    fun `pages through the whole catalog then stops`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(BrowseViewModel.PAGE_SIZE, viewModel.state.value.species.size)

        repeat(3) {
            viewModel.onAction(BrowseAction.LoadMore)
            advanceUntilIdle()
        }

        assertEquals(91, viewModel.state.value.species.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `results arrive after typing pauses for the latest query only`() = runTest(testDispatcher) {
        val viewModel = viewModel()

        viewModel.onAction(BrowseAction.QueryChanged("cay 00"))
        advanceTimeBy(BrowseViewModel.SEARCH_DEBOUNCE_MS / 2)
        viewModel.onAction(BrowseAction.QueryChanged("dinh lang"))
        advanceTimeBy(BrowseViewModel.SEARCH_DEBOUNCE_MS / 2)
        assertTrue(viewModel.state.value.results.isEmpty(), "nothing yet while typing")

        advanceUntilIdle()
        assertEquals(listOf(3035652L), viewModel.state.value.results.map { it.species.id })
        assertTrue(viewModel.state.value.isSearching)
    }

    @Test
    fun `clearing the query goes back to the catalog`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        viewModel.onAction(BrowseAction.QueryChanged("dinh"))
        advanceUntilIdle()

        viewModel.onAction(BrowseAction.QueryChanged(""))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSearching)
        assertTrue(viewModel.state.value.results.isEmpty())
    }
}
