package com.uri.lee.dl.core.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The signed-in Firebase user. The sign-in UI itself is the platform's (Credential Manager, Google Sign-In, Apple). */
class AuthClient internal constructor(private val auth: FirebaseAuth) {

    val currentUserId: String? get() = auth.currentUser?.uid

    /** The name and email the provider shared, if any (Apple shares neither, as the app asks for neither). */
    val displayName: String? get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
    val email: String? get() = auth.currentUser?.email?.takeIf { it.isNotBlank() }

    /** Firebase's provider ids of the ways the user signs in, e.g. `google.com`, `apple.com`. */
    val providerIds: List<String> get() = auth.currentUser?.providerData?.map { it.providerId }.orEmpty()

    val userIdChanges: Flow<String?> = auth.authStateChanged.map { it?.uid }.distinctUntilChanged()

    /** A Firebase ID token for our own backend (the photo-upload Worker); refreshed when close to expiry. */
    suspend fun idToken(): String? = auth.currentUser?.getIdToken(forceRefresh = false)

    suspend fun signInWithGoogle(idToken: String, accessToken: String?) {
        auth.signInWithCredential(GoogleAuthProvider.credential(idToken = idToken, accessToken = accessToken))
    }

    suspend fun signInWithApple(idToken: String, rawNonce: String) {
        auth.signInWithCredential(OAuthProvider.credential(providerId = APPLE, idToken = idToken, rawNonce = rawNonce))
    }

    suspend fun signOut() = auth.signOut()

    suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String?) {
        auth.currentUser?.reauthenticate(GoogleAuthProvider.credential(idToken = idToken, accessToken = accessToken))
    }

    suspend fun reauthenticateWithApple(idToken: String, rawNonce: String) {
        auth.currentUser?.reauthenticate(OAuthProvider.credential(providerId = APPLE, idToken = idToken, rawNonce = rawNonce))
    }

    /** Deletes the signed-in account, which also signs it out. */
    suspend fun deleteUser() {
        auth.currentUser?.delete()
    }

    companion object {
        const val GOOGLE = "google.com"
        const val APPLE = "apple.com"
    }
}
