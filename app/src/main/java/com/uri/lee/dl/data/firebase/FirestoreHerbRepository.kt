package com.uri.lee.dl.data.firebase

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.uri.lee.dl.FireStoreHerb
import com.uri.lee.dl.domain.model.HerbPage
import com.uri.lee.dl.domain.model.HerbPageKey
import com.uri.lee.dl.domain.model.HerbProfile
import com.uri.lee.dl.domain.model.HerbSummary
import com.uri.lee.dl.domain.repository.HerbRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreHerbRepository(private val db: FirebaseFirestore) : HerbRepository {

    private val herbs get() = db.collection(FirestorePaths.HERBS)

    override fun observeProfile(id: Long): Flow<HerbProfile?> =
        herbs.document(id.toString()).snapshots().map { snapshot ->
            snapshot.takeIf { it.exists() }?.toObject<FireStoreHerb>()?.let { HerbDocumentMapper.toProfile(id, it) }
        }

    override suspend fun page(after: HerbPageKey?, size: Int, sortByVietnameseName: Boolean): HerbPage {
        val sortField = if (sortByVietnameseName) FirestorePaths.HERB_VI_NAME else FirestorePaths.HERB_LATIN_NAME
        var query = herbs.orderBy(sortField).orderBy(FieldPath.documentId()).limit(size.toLong())
        if (after != null) query = query.startAfter(after.sortValue, after.documentId)
        val docs = query.get().await().documents
        val summaries = docs.mapNotNull { doc ->
            val id = doc.id.toLongOrNull() ?: return@mapNotNull null
            doc.toObject<FireStoreHerb>()?.let { HerbDocumentMapper.toSummary(id, it) }
        }
        val last = docs.lastOrNull()
        val next = if (docs.size < size || last == null) null else HerbPageKey(last.getString(sortField).orEmpty(), last.id)
        return HerbPage(summaries, next)
    }

    override suspend fun summaries(ids: List<Long>): List<HerbSummary> {
        if (ids.isEmpty()) return emptyList()
        // Firestore allows at most 30 values in an `in` filter.
        val found = ids.distinct().chunked(30).flatMap { chunk ->
            herbs.whereIn(FieldPath.documentId(), chunk.map(Long::toString)).get().await().documents
        }.mapNotNull { doc ->
            val id = doc.id.toLongOrNull() ?: return@mapNotNull null
            doc.toObject<FireStoreHerb>()?.let { id to HerbDocumentMapper.toSummary(id, it) }
        }.toMap()
        return ids.mapNotNull(found::get)
    }
}
