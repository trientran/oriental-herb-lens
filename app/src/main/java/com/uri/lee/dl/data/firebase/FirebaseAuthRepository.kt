package com.uri.lee.dl.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUser?.uid

    override fun observeUserId(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun idToken(): String? = auth.currentUser?.getIdToken(false)?.await()?.token
}
