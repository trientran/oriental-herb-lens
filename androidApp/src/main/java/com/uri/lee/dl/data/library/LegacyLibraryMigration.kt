package com.uri.lee.dl.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.firebase.firestore.FirebaseFirestore
import com.uri.lee.dl.data.firebase.FirestorePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Earlier versions kept favourites and history on `users/{uid}`. Copies them to the device once
 * per user, then never reads that document again. If the device is offline it tries again next
 * launch.
 */
class LegacyLibraryMigration(
    private val db: FirebaseFirestore,
    private val library: LocalUserLibraryRepository,
    private val prefs: DataStore<Preferences>,
) {
    suspend fun migrateIfNeeded(uid: String) {
        val done = booleanPreferencesKey("library_migrated_$uid")
        if (prefs.data.first()[done] == true) return
        try {
            val doc = db.collection(FirestorePaths.USERS).document(uid).get().await()
            fun ids(field: String) = (doc.get(field) as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong() }
            library.importLegacy(
                favoritesOldestFirst = ids(FirestorePaths.USER_FAVORITES),
                historyOldestFirst = ids(FirestorePaths.USER_HISTORY),
            )
            prefs.edit { it[done] = true }
            Timber.i("Copied favourites and history to the device")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Library copy failed; will retry next launch")
        }
    }
}
