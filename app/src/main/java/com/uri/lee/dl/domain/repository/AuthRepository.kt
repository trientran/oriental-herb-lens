package com.uri.lee.dl.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?

    /** Emits the signed-in user's id, or null when signed out, and every change after that. */
    fun observeUserId(): Flow<String?>

    suspend fun isAdmin(): Boolean
}
