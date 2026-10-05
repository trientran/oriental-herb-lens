package com.uri.lee.dl.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.FirestoreClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Earlier versions kept favourites and history on `users/{uid}`. Copies them to the device once
 * per user, then never reads that document again. If the device is offline it tries again next
 * launch.
 */
internal class LegacyLibraryMigration(
    private val firestore: FirestoreClient,
    private val library: LocalUserLibraryRepository,
    private val prefs: DataStore<Preferences>,
) {
    suspend fun migrateIfNeeded(uid: String) {
        val done = booleanPreferencesKey("library_migrated_$uid")
        if (prefs.data.first()[done] == true) return
        try {
            val legacy = firestore.legacyUserLibrary(uid)
            library.importLegacy(favoritesOldestFirst = legacy.favorites, historyOldestFirst = legacy.history)
            prefs.edit { it[done] = true }
            log.i { "Copied favourites and history to the device" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Library copy failed; will retry next launch" }
        }
    }

    private companion object {
        val log = Logger.withTag("Library")
    }
}
