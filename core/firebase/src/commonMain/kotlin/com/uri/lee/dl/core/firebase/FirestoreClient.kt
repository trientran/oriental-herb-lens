package com.uri.lee.dl.core.firebase

import com.uri.lee.dl.core.firebase.FirestorePaths.CREATED_AT
import com.uri.lee.dl.core.firebase.FirestorePaths.HERBS
import com.uri.lee.dl.core.firebase.FirestorePaths.HERB_IMAGES
import com.uri.lee.dl.core.firebase.FirestorePaths.MODEL_REPORTS
import com.uri.lee.dl.core.firebase.FirestorePaths.NAME_SUGGESTIONS
import com.uri.lee.dl.core.firebase.FirestorePaths.PHOTO_REPORTS
import com.uri.lee.dl.core.firebase.FirestorePaths.SHARED_MODELS
import com.uri.lee.dl.core.firebase.FirestorePaths.USERS
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_EMAIL
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_FAVORITES
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_HISTORY
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_NAME
import com.uri.lee.dl.core.firebase.FirestorePaths.USER_UID
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** A model shared from the Train tab, as `sharedModels/{id}` stores it (the files are in R2). */
data class SharedModelRecord(
    val id: String,
    val name: String,
    val species: List<String>,
    val backbone: String,
    val trainable: Boolean,
    val url: String,
    val size: Int,
    val uploaderId: String,
    val license: String,
    /** "none" or "requested" when shared; the administrator sets "published" (with [huggingFaceUrl]) or "declined". */
    val huggingFace: String = "none",
    val huggingFaceUrl: String? = null,
)

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
        db.collection(NAME_SUGGESTIONS).add(NameSuggestion(speciesKey.asStoredKey(), vietnameseName, uid))
    }

    suspend fun legacyUserLibrary(uid: String): LegacyUserLibrary {
        val doc = db.collection(USERS).document(uid).get()
        return LegacyUserLibrary(favorites = doc.ids(USER_FAVORITES), history = doc.ids(USER_HISTORY))
    }

    /**
     * Records who a contributor is on `users/{uid}`: uid, and name and email when the provider
     * shared them. Merged, so favourites and history from old versions stay.
     */
    suspend fun saveUserProfile(uid: String, name: String?, email: String?) {
        val profile = buildMap {
            put(USER_UID, uid)
            name?.let { put(USER_NAME, it) }
            email?.let { put(USER_EMAIL, it) }
        }
        db.collection(USERS).document(uid).set(profile, merge = true)
    }

    /**
     * A report of a shared photo, for the administrator (`photoReports`, create-only). The
     * reporter is recorded when signed in; anyone may report.
     */
    suspend fun addPhotoReport(speciesKey: Long, url: String, uploaderId: String?, reason: String, reporterUid: String?) {
        db.collection(PHOTO_REPORTS).add(PhotoReport(speciesKey.asStoredKey(), url, uploaderId, reason, reporterUid))
    }

    /** The newest shared models; documents that don't read as one are skipped. */
    fun observeSharedModels(limit: Int): Flow<List<SharedModelRecord>> =
        db.collection(SHARED_MODELS).orderBy(CREATED_AT, Direction.DESCENDING).limit(limit).snapshots
            .map { snapshot -> snapshot.documents.mapNotNull { runCatching { it.sharedModel() }.getOrNull() } }

    /** Lists a model the Worker stored; the rules check it's the user's own and well formed. */
    suspend fun addSharedModel(record: SharedModelRecord) {
        db.collection(SHARED_MODELS).document(record.id).set(
            SharedModelDocument(
                record.name, record.species, record.backbone, record.trainable, record.url, record.size,
                record.uploaderId, record.license, record.huggingFace,
            ),
        )
    }

    suspend fun sharedModelExists(id: String): Boolean = db.collection(SHARED_MODELS).document(id).get().exists

    suspend fun deleteSharedModel(id: String) {
        db.collection(SHARED_MODELS).document(id).delete()
    }

    /** A report of a shared model, for the administrator (`modelReports`, create-only). */
    suspend fun addModelReport(modelId: String, uploaderId: String, reason: String, reporterUid: String) {
        db.collection(MODEL_REPORTS).add(ModelReport(modelId, uploaderId, reason, reporterUid))
    }

    /** Removes `users/{uid}`, where old app versions kept the user's name and email. */
    suspend fun deleteUserDocument(uid: String) {
        db.collection(USERS).document(uid).delete()
    }

    /**
     * Species keys are stored as Int: in the browser a Kotlin Long isn't a JS number, and
     * Firestore's JS SDK refuses it. GBIF keys (about 12 million today) fit easily.
     */
    private fun Long.asStoredKey(): Int {
        require(this in 0..Int.MAX_VALUE) { "Species key out of range: $this" }
        return toInt()
    }

    @Serializable
    private data class PhotoReport(
        val speciesKey: Int,
        val url: String,
        val uploaderId: String?,
        val reason: String,
        val reporterUid: String?,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    @Serializable
    private data class SharedModelDocument(
        val name: String,
        val species: List<String>,
        val backbone: String,
        val trainable: Boolean,
        val url: String,
        val size: Int,
        val uploaderId: String,
        val license: String,
        val huggingFace: String,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    @Serializable
    private data class ModelReport(
        val modelId: String,
        val uploaderId: String,
        val reason: String,
        val reporterUid: String,
        val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    )

    private fun DocumentSnapshot.sharedModel() = SharedModelRecord(
        id = id,
        name = get<String>("name"),
        species = get<List<String>>("species"),
        backbone = get<String?>("backbone").orEmpty(),
        trainable = get<Boolean?>("trainable") ?: false,
        url = get<String>("url"),
        // A whole number arrives as a Long on Android and iOS, and as a JS number on the web
        size = runCatching { get<Long>("size").toInt() }.getOrElse { get<Double>("size").toInt() },
        uploaderId = get<String>("uploaderId"),
        license = get<String?>("license").orEmpty(),
        huggingFace = runCatching { get<String?>("huggingFace") }.getOrNull() ?: "none",
        huggingFaceUrl = runCatching { get<String?>("huggingFaceUrl") }.getOrNull(),
    )

    @Serializable
    private data class NameSuggestion(
        val speciesKey: Int,
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
