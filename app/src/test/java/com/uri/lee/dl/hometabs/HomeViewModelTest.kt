package com.uri.lee.dl.hometabs

import com.uri.lee.dl.fakes.FakeHerbRepository
import com.uri.lee.dl.fakes.FakeUserLibraryRepository
import com.uri.lee.dl.fakes.herbProfile
import com.uri.lee.dl.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val herbs = FakeHerbRepository((1L..25L).map { herbProfile(it) })
    private val library = FakeUserLibraryRepository()

    private fun viewModel() = HomeViewModel(herbs, library, sortByVietnameseName = true)

    @Test
    fun `loads the first page, then more on request, until the end`() {
        val viewModel = viewModel()
        assertEquals((1L..10L).toList(), viewModel.state.value.allHerbs.map { it.id })

        viewModel.onAction(HomeAction.LoadMore)
        viewModel.onAction(HomeAction.LoadMore)
        assertEquals((1L..25L).toList(), viewModel.state.value.allHerbs.map { it.id })

        val requests = herbs.pageRequests
        viewModel.onAction(HomeAction.LoadMore)
        assertEquals("no request past the last page", requests, herbs.pageRequests)
    }

    @Test
    fun `refresh starts again from the first page`() {
        val viewModel = viewModel()
        viewModel.onAction(HomeAction.LoadMore)

        viewModel.onAction(HomeAction.Refresh)

        assertEquals((1L..10L).toList(), viewModel.state.value.allHerbs.map { it.id })
    }

    @Test
    fun `favourites and history keep the library's order and follow changes`() {
        library.favorites.value = listOf(3L, 1L)
        val viewModel = viewModel()
        assertEquals(listOf(3L, 1L), viewModel.state.value.favorites.map { it.id })

        library.history.value = listOf(7L, 2L, 999L) // 999 no longer exists
        assertEquals(listOf(7L, 2L), viewModel.state.value.history.map { it.id })
    }
}
