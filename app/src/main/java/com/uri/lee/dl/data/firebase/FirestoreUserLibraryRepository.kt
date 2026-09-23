package com.uri.lee.dl.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Favourites and history as id arrays on `users/{uid}`, oldest first. Moves to local storage in
 * Phase 2 (decision D1).
 */
class FirestoreUserLibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: AuthRepository,
) : UserLibraryRepository {

    private fun userDoc(uid: String) = db.collection(FirestorePaths.USERS).document(uid)

    override fun observeFavorites(): Flow<List<Long>> = observeIdList(FirestorePaths.USER_FAVORITES)

    override fun observeHistory(): Flow<List<Long>> = observeIdList(FirestorePaths.USER_HISTORY)

    override suspend fun setFavorite(herbId: Long, favorite: Boolean) {
        val uid = auth.currentUserId ?: return
        val change = if (favorite) FieldValue.arrayUnion(herbId) else FieldValue.arrayRemove(herbId)
        userDoc(uid).set(mapOf(FirestorePaths.USER_FAVORITES to change), SetOptions.merge()).await()
    }

    override suspend fun recordViewed(herbId: Long) {
        val uid = auth.currentUserId ?: return
        val history = userDoc(uid).get().await().idList(FirestorePaths.USER_HISTORY)
        val updated = history.filter { it != herbId } + herbId
        userDoc(uid).set(mapOf(FirestorePaths.USER_HISTORY to updated), SetOptions.merge()).await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeIdList(field: String): Flow<List<Long>> =
        auth.observeUserId().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else userDoc(uid).snapshots().map { it.idList(field).reversed().distinct() }
        }.distinctUntilChanged()

    private fun DocumentSnapshot.idList(field: String): List<Long> =
        (get(field) as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong() }
}
