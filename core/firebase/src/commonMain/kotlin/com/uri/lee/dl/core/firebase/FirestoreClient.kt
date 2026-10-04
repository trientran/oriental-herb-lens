package com.uri.lee.dl.core.firebase

import com.uri.lee.dl.core.firebase.FirestorePaths.HERBS
import com.uri.lee.dl.core.firebase.FirestorePaths.HERB_IMAGES
import com.uri.lee.dl.core.firebase.FirestorePaths.NAME_SUGGESTIONS
import com.uri.lee.dl.core.firebase.FirestorePaths.USERS
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_FAVORITES
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_HISTORY
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Favourites and history as earlier versions stored them on `users/{uid}`, oldest first. */
data class LegacyUserLibrary(val favorites: List<Long>, val history: List<Long>)

/**
 * Everything the app reads from or writes to Firestore. The security rules (firebase/firestore.rules)
 * define what each call may do.
 */
class FirestoreClient internal constructor(private val db: FirebaseFirestore) {

    /** The `images` map of `herbs/{speciesKey}`: image URL to its detail string. */
    fun observeHerbImages(speciesKey: Long): Flow<Map<String, String>> =
        db.collection(HERBS).document(speciesKey.toString()).snapshots.map { it.stringMap(HERB_IMAGES) }

    /** Adds entries to the `images` map of `herbs/{speciesKey}`, creating the document if needed. */
    suspend fun addHerbImages(speciesKey: Long, images: Map<String, String>) {
        db.collection(HERBS).document(speciesKey.toString()).set(mapOf(HERB_IMAGES to images), merge = true)
    }

    suspend fun addNameSuggestion(speciesKey: Long, vietnameseName: String, uid: String) {
        db.collection(NAME_SUGGESTIONS).add(NameSuggestion(speciesKey, vietnameseName, uid))
    }

    suspend fun legacyUserLibrary(uid: String): LegacyUserLibrary {
        val doc = db.collection(USERS).document(uid).get()
        return LegacyUserLibrary(favorites = doc.ids(USER_FAVORITES), history = doc.ids(USER_HISTORY))
    }

    @Serializable
    private data class NameSuggestion(
        val speciesKey: Long,
        val viName: String,
        val uid: String,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    private fun DocumentSnapshot.stringMap(field: String): Map<String, String> =
        if (!exists || !contains(field)) emptyMap()
        else runCatching { get<Map<String, String>?>(field) }.getOrNull().orEmpty()

    private fun DocumentSnapshot.ids(field: String): List<Long> =
        if (!exists || !contains(field)) emptyList()
        else runCatching { get<List<Long>?>(field) }.getOrNull()
            ?: runCatching { get<List<Double>?>(field) }.getOrNull()?.map { it.toLong() }
            ?: emptyList()
}
