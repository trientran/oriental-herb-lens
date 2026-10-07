package com.uri.lee.dl.data

import com.uri.lee.dl.data.analytics.UsageStatisticsSync
import com.uri.lee.dl.data.firebase.UserProfileSync
import com.uri.lee.dl.data.library.LibraryMigration
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.SettingsRepository
import com.uri.lee.dl.domain.upload.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Background work every app starts once at launch. */
class StartupTasks internal constructor(
    private val auth: AuthRepository,
    private val legacyLibrary: LibraryMigration,
    private val profile: UserProfileSync,
    private val settings: SettingsRepository,
    private val usageStatistics: UsageStatisticsSync,
    private val uploads: UploadScheduler,
) {
    fun launchIn(scope: CoroutineScope) {
        // Uploads saved but not finished last time (the app was closed, or offline)
        uploads.schedule()
        // Analytics follows the user's choice in Profile
        scope.launch { settings.usageStatistics.collect(usageStatistics::apply) }
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
