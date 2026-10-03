package com.uri.lee.dl.data.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.Clock
import com.uri.lee.dl.data.db.HerbLensDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalUserLibraryRepositoryTest {

    private var now = 1_000_000L
    private val dispatcher = UnconfinedTestDispatcher()
    private val db = HerbLensDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { HerbLensDatabase.Schema.create(it) })
    private val library = LocalUserLibraryRepository(db, Clock { now++ }, AppDispatchers(dispatcher, dispatcher, dispatcher))

    @Test
    fun `favourites are newest first and can be removed`() = runTest(dispatcher) {
        library.setFavorite(1, true)
        library.setFavorite(2, true)
        library.setFavorite(3, true)
        library.setFavorite(2, false)

        assertEquals(listOf(3L, 1L), library.observeFavorites().first())
    }

    @Test
    fun `viewing again moves a species to the front of history`() = runTest(dispatcher) {
        listOf(1L, 2L, 3L, 1L).forEach { library.recordViewed(it) }

        assertEquals(listOf(1L, 3L, 2L), library.observeHistory().first())
    }

    @Test
    fun `history keeps the most recent 200`() = runTest(dispatcher) {
        (1L..250L).forEach { library.recordViewed(it) }

        val history = library.observeHistory().first()
        assertEquals(200, history.size)
        assertEquals(250L, history.first())
        assertEquals(51L, history.last())
    }

    @Test
    fun `changes reach observers`() = runTest(dispatcher) {
        library.observeFavorites().test {
            assertEquals(emptyList<Long>(), awaitItem())
            library.setFavorite(7, true)
            assertEquals(listOf(7L), awaitItem())
        }
    }

    @Test
    fun `copied entries keep their order and sit below anything already on the device`() = runTest(dispatcher) {
        library.setFavorite(9, true)
        library.recordViewed(9)

        library.importLegacy(favoritesOldestFirst = listOf(1, 2, 9), historyOldestFirst = listOf(4, 5, 5, 6))

        assertEquals(listOf(9L, 2L, 1L), library.observeFavorites().first())
        assertEquals(listOf(9L, 6L, 5L, 4L), library.observeHistory().first())
    }
}
