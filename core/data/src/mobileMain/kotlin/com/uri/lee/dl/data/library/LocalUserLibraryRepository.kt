package com.uri.lee.dl.data.library

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.Clock
import com.uri.lee.dl.data.db.HerbLensDatabase
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Favourites and history on the device only (decision D1); they work offline and signed out. */
internal class LocalUserLibraryRepository(
    private val db: HerbLensDatabase,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : UserLibraryRepository {

    override fun observeFavorites(): Flow<List<Long>> =
        db.libraryQueries.favoriteIds().asFlow().mapToList(dispatchers.io)

    override fun observeHistory(): Flow<List<Long>> =
        db.libraryQueries.historyIds(HISTORY_SIZE).asFlow().mapToList(dispatchers.io)

    override suspend fun setFavorite(herbId: Long, favorite: Boolean) {
        withContext(dispatchers.io) {
            if (favorite) db.libraryQueries.addFavorite(herbId, clock.nowMillis()) else db.libraryQueries.removeFavorite(herbId)
        }
    }

    override suspend fun recordViewed(herbId: Long): Unit = withContext(dispatchers.io) {
        db.transaction {
            db.libraryQueries.recordView(herbId, clock.nowMillis())
            db.libraryQueries.trimHistory(HISTORY_SIZE)
        }
    }

    /**
     * Adds entries copied from elsewhere, oldest first, below everything already on the device
     * (they're given times before the earliest local entry). Existing entries win.
     */
    internal suspend fun importLegacy(favoritesOldestFirst: List<Long>, historyOldestFirst: List<Long>): Unit =
        withContext(dispatchers.io) {
            db.transaction {
                val base = 1L // epoch + n ms: always older than anything recorded on the device
                val localFavorites = db.libraryQueries.favoriteIds().executeAsList().toSet()
                favoritesOldestFirst.distinct().forEachIndexed { i, id ->
                    if (id !in localFavorites) db.libraryQueries.addFavorite(id, base + i)
                }
                val localHistory = db.libraryQueries.historyIds(Long.MAX_VALUE).executeAsList().toSet()
                historyOldestFirst.distinct().forEachIndexed { i, id ->
                    if (id !in localHistory) db.libraryQueries.recordView(id, base + i)
                }
                db.libraryQueries.trimHistory(HISTORY_SIZE)
            }
        }

    companion object {
        const val HISTORY_SIZE = 200L
    }
}
