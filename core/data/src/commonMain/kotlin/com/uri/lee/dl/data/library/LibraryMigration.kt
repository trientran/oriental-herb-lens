package com.uri.lee.dl.data.library

/** Copies a user's favourites and history from where earlier versions kept them, once. */
internal fun interface LibraryMigration {
    suspend fun migrateIfNeeded(uid: String)
}
