package com.uri.lee.dl.domain.repository

import kotlinx.coroutines.flow.Flow

/** The signed-in user's favourites and viewing history, most recent first. Empty when signed out. */
interface UserLibraryRepository {
    fun observeFavorites(): Flow<List<Long>>

    fun observeHistory(): Flow<List<Long>>

    suspend fun setFavorite(herbId: Long, favorite: Boolean)

    /** Moves [herbId] to the front of the history. */
    suspend fun recordViewed(herbId: Long)
}
