package com.uri.lee.dl.core.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The signed-in Firebase user. Signing in itself is platform UI (FirebaseUI on Android). */
class AuthClient internal constructor(private val auth: FirebaseAuth) {

    val currentUserId: String? get() = auth.currentUser?.uid

    val userIdChanges: Flow<String?> = auth.authStateChanged.map { it?.uid }.distinctUntilChanged()

    /** A Firebase ID token for our own backend (the photo-upload Worker); refreshed when close to expiry. */
    suspend fun idToken(): String? = auth.currentUser?.getIdToken(forceRefresh = false)

    suspend fun signInWithGoogle(idToken: String, accessToken: String?) {
        auth.signInWithCredential(GoogleAuthProvider.credential(idToken = idToken, accessToken = accessToken))
    }

    suspend fun signInWithApple(idToken: String, rawNonce: String) {
        auth.signInWithCredential(OAuthProvider.credential(providerId = "apple.com", idToken = idToken, rawNonce = rawNonce))
    }

    suspend fun signOut() = auth.signOut()
}
