package com.uri.lee.dl.data

import com.uri.lee.dl.data.library.LegacyLibraryMigration
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Background work every app starts once at launch. */
class StartupTasks internal constructor(
    private val auth: AuthRepository,
    private val legacyLibrary: LegacyLibraryMigration,
) {
    fun launchIn(scope: CoroutineScope) {
        // One-time copy of each signed-in user's Firestore favourites and history to the device
        scope.launch { auth.observeUserId().filterNotNull().collect { legacyLibrary.migrateIfNeeded(it) } }
    }
}
