package com.uri.lee.dl.domain.repository

import kotlinx.coroutines.flow.Flow

/** How the user signs in, which is how they prove it's them again before deleting the account. */
enum class SignInProvider { GOOGLE, APPLE, OTHER }

interface AuthRepository {
    val currentUserId: String?

    /** Null when signed out. */
    val signInProvider: SignInProvider?

    /** Emits the signed-in user's id, or null when signed out, and every change after that. */
    fun observeUserId(): Flow<String?>

    /** A current ID token proving who the user is to our own services, or null when signed out. */
    suspend fun idToken(): String?

    /**
     * Signs in with the Google tokens the platform obtained (Credential Manager on Android, Google
     * Sign-In on iOS, where Firebase also needs the [accessToken]).
     */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null)

    /** Signs in with an Apple identity token and the unhashed nonce its request was made with. */
    suspend fun signInWithApple(idToken: String, rawNonce: String)

    suspend fun signOut()

    /** Confirms who the user is with fresh Google tokens, as deleting the account requires. */
    suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String? = null)

    /** Confirms who the user is with a fresh Apple identity token. */
    suspend fun reauthenticateWithApple(idToken: String, rawNonce: String)

    /**
     * Deletes the account and the personal details kept with it (the old `users` document).
     * Firebase refuses unless the user signed in recently: reauthenticate first.
     */
    suspend fun deleteAccount()
}
