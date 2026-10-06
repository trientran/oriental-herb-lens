package com.uri.lee.dl.data.library

import com.uri.lee.dl.core.datastore.KeyValueStore
import com.uri.lee.dl.core.datastore.stringKey
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Favourites and history in the browser's storage (decision D1: on the device only), most recent first. */
internal class WebUserLibraryRepository(private val store: KeyValueStore) : UserLibraryRepository {

    override fun observeFavorites(): Flow<List<Long>> = store.data.map { it[FAVORITES].toIds() }.distinctUntilChanged()

    override fun observeHistory(): Flow<List<Long>> = store.data.map { it[HISTORY].toIds() }.distinctUntilChanged()

    override suspend fun setFavorite(herbId: Long, favorite: Boolean) = store.edit {
        val rest = it[FAVORITES].toIds() - herbId
        it[FAVORITES] = (if (favorite) listOf(herbId) + rest else rest).joinToString(",")
    }

    override suspend fun recordViewed(herbId: Long) = store.edit {
        it[HISTORY] = (listOf(herbId) + (it[HISTORY].toIds() - herbId)).take(HISTORY_SIZE).joinToString(",")
    }

    private fun String?.toIds(): List<Long> = orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    private companion object {
        const val HISTORY_SIZE = 200
        val FAVORITES = stringKey("favorites")
        val HISTORY = stringKey("history")
    }
}
