package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.core.datastore.KeyValueStore
import com.uri.lee.dl.core.datastore.booleanKey
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
    private val prefs: KeyValueStore,
) {
    suspend fun saveIfNeeded(uid: String) {
        val done = booleanKey("profile_saved_$uid")
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
