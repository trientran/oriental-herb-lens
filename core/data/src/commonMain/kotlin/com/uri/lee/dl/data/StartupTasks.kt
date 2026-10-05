package com.uri.lee.dl.data

import com.uri.lee.dl.data.firebase.UserProfileSync
import com.uri.lee.dl.data.library.LegacyLibraryMigration
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Background work every app starts once at launch. */
class StartupTasks internal constructor(
    private val auth: AuthRepository,
    private val legacyLibrary: LegacyLibraryMigration,
    private val profile: UserProfileSync,
) {
    fun launchIn(scope: CoroutineScope) {
        scope.launch {
            auth.observeUserId().filterNotNull().collect { uid ->
                // Who the contributor is, once per user and device
                profile.saveIfNeeded(uid)
                // One-time copy of the user's Firestore favourites and history to the device
                legacyLibrary.migrateIfNeeded(uid)
            }
        }
    }
}
