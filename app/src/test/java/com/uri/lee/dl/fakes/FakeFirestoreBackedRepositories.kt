package com.uri.lee.dl.fakes

import com.uri.lee.dl.domain.model.HerbPage
import com.uri.lee.dl.domain.model.HerbPageKey
import com.uri.lee.dl.domain.model.HerbProfile
import com.uri.lee.dl.domain.model.HerbSummary
import com.uri.lee.dl.domain.model.LocalizedText
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.HerbRepository
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeAuthRepository(uid: String? = "user-1", var admin: Boolean = false) : AuthRepository {
    val userId = MutableStateFlow(uid)
    override val currentUserId: String? get() = userId.value
    override fun observeUserId(): Flow<String?> = userId
    override suspend fun isAdmin() = admin
}

/** All herbs sorted by id; pages hand out ids as keys. */
class FakeHerbRepository(herbs: List<HerbProfile> = emptyList()) : HerbRepository {
    val profiles = MutableStateFlow(herbs.associateBy { it.id })
    var pageRequests = 0
        private set

    override fun observeProfile(id: Long): Flow<HerbProfile?> = profiles.map { it[id] }

    override suspend fun page(after: HerbPageKey?, size: Int, sortByVietnameseName: Boolean): HerbPage {
        pageRequests++
        val sorted = profiles.value.values.sortedBy { it.id }
        val start = after?.let { key -> sorted.indexOfFirst { it.id.toString() == key.documentId } + 1 } ?: 0
        val chunk = sorted.drop(start).take(size)
        val next = if (chunk.size < size) null else chunk.last().let { HerbPageKey(it.latinName, it.id.toString()) }
        return HerbPage(chunk.map { it.toSummary() }, next)
    }

    override suspend fun summaries(ids: List<Long>): List<HerbSummary> =
        ids.mapNotNull { profiles.value[it]?.toSummary() }

    private fun HerbProfile.toSummary() = HerbSummary(id, latinName, vietnameseName, images.firstOrNull()?.url)
}

class FakeUserLibraryRepository : UserLibraryRepository {
    val favorites = MutableStateFlow<List<Long>>(emptyList())
    val history = MutableStateFlow<List<Long>>(emptyList())
    var failWrites = false

    override fun observeFavorites(): Flow<List<Long>> = favorites
    override fun observeHistory(): Flow<List<Long>> = history

    override suspend fun setFavorite(herbId: Long, favorite: Boolean) {
        if (failWrites) error("offline")
        favorites.update { list -> if (favorite) listOf(herbId) + (list - herbId) else list - herbId }
    }

    override suspend fun recordViewed(herbId: Long) {
        history.update { listOf(herbId) + (it - herbId) }
    }
}

class FakeContributionRepository : ContributionRepository {
    val images = mutableListOf<Pair<Long, List<UploadedImage>>>()
    val names = mutableListOf<Pair<Long, String>>()
    var fail = false

    override suspend fun addImages(herbId: Long, images: List<UploadedImage>) {
        if (fail) error("offline")
        this.images += herbId to images
    }

    override suspend fun suggestVietnameseName(herbId: Long, name: String) {
        if (fail) error("offline")
        names += herbId to name
    }
}

fun herbProfile(id: Long, latin: String = "Species $id", vi: String = "Cây $id") = HerbProfile(
    id = id,
    latinName = latin,
    vietnameseName = vi,
    englishName = "",
    overview = LocalizedText.EMPTY,
    dosing = LocalizedText.EMPTY,
    sideEffects = LocalizedText.EMPTY,
    interactions = LocalizedText.EMPTY,
    images = emptyList(),
)
