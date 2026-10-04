package com.uri.lee.dl.search

import com.uri.lee.dl.testing.fakes.FakeSpeciesRepository
import com.uri.lee.dl.testing.fakes.species
import com.uri.lee.dl.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    private val catalog = FakeSpeciesRepository(
        listOf(
            species(3035652, "Polyscias fruticosa", vi = listOf("Đinh lăng")),
            species(2927192, "Mentha arvensis", vi = listOf("Bạc hà")),
        )
    )

    @Test
    fun `results arrive after typing pauses, for the latest query only`() = runTest(mainDispatcher.testDispatcher) {
        val viewModel = SearchViewModel(catalog)

        viewModel.onAction(SearchAction.QueryChanged("bac"))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS / 2)
        viewModel.onAction(SearchAction.QueryChanged("dinh lang"))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS / 2)
        assertTrue("nothing yet while typing", viewModel.state.value.results.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(3035652L), viewModel.state.value.results.map { it.species.id })
        assertEquals("dinh lang", viewModel.state.value.query)
    }

    @Test
    fun `clearing the query clears the results`() = runTest(mainDispatcher.testDispatcher) {
        val viewModel = SearchViewModel(catalog)
        viewModel.onAction(SearchAction.QueryChanged("bac ha"))
        advanceUntilIdle()

        viewModel.onAction(SearchAction.QueryChanged(""))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.results.isEmpty())
    }
}
