package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.core.firebase.AuthClient
import com.uri.lee.dl.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

internal class FirebaseAuthRepository(private val auth: AuthClient) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUserId

    override fun observeUserId(): Flow<String?> = auth.userIdChanges

    override suspend fun idToken(): String? = auth.idToken()
}
