package com.uri.lee.dl.data.firebase

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.firebase.AuthClient
import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.SignInProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal class FirebaseAuthRepository(private val auth: AuthClient, private val firestore: FirestoreClient) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUserId

    override val signInProvider: SignInProvider?
        get() {
            if (auth.currentUserId == null) return null
            val ids = auth.providerIds
            return when {
                AuthClient.APPLE in ids -> SignInProvider.APPLE
                AuthClient.GOOGLE in ids -> SignInProvider.GOOGLE
                else -> SignInProvider.OTHER
            }
        }

    override fun observeUserId(): Flow<String?> = auth.userIdChanges

    override suspend fun idToken(): String? = auth.idToken()

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) = auth.signInWithGoogle(idToken, accessToken)

    override suspend fun signInWithApple(idToken: String, rawNonce: String) = auth.signInWithApple(idToken, rawNonce)

    override suspend fun signOut() = auth.signOut()

    override suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String?) = auth.reauthenticateWithGoogle(idToken, accessToken)

    override suspend fun reauthenticateWithApple(idToken: String, rawNonce: String) = auth.reauthenticateWithApple(idToken, rawNonce)

    override suspend fun deleteAccount() {
        val uid = auth.currentUserId ?: return
        // While still signed in, as the rules require; most users never had one, which is fine
        try {
            firestore.deleteUserDocument(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "users/$uid not deleted" }
        }
        auth.deleteUser()
    }

    private companion object {
        val log = Logger.withTag("Auth")
    }
}
