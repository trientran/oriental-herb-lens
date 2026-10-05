package com.uri.lee.dl.data.firebase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.AuthClient
import com.uri.lee.dl.core.firebase.FirestoreClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Keeps `users/{uid}` saying who each contributor is (uid, name, email), for the research data.
 * Written once per user on each device, so a sign-in costs one write, not one per launch. If the
 * device is offline it tries again next launch.
 */
internal class UserProfileSync(
    private val auth: AuthClient,
    private val firestore: FirestoreClient,
    private val prefs: DataStore<Preferences>,
) {
    suspend fun saveIfNeeded(uid: String) {
        val done = booleanPreferencesKey("profile_saved_$uid")
        if (prefs.data.first()[done] == true) return
        try {
            firestore.saveUserProfile(uid, auth.displayName, auth.email)
            prefs.edit { it[done] = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Profile not saved; will retry next launch" }
        }
    }

    private companion object {
        val log = Logger.withTag("Profile")
    }
}
