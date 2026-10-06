package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.model.Citation
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.moderation.HiddenContent
import com.uri.lee.dl.domain.moderation.ModerationRepository
import com.uri.lee.dl.domain.moderation.ReportReason
import com.uri.lee.dl.domain.repository.AuthRepository
import com.uri.lee.dl.domain.repository.CitationRepository
import com.uri.lee.dl.domain.repository.ContributionRepository
import com.uri.lee.dl.domain.repository.PhotoRepository
import com.uri.lee.dl.domain.repository.SignInProvider
import com.uri.lee.dl.domain.repository.UploadedImage
import com.uri.lee.dl.domain.repository.UserLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeAuthRepository(uid: String? = "user-1") : AuthRepository {
    val userId = MutableStateFlow(uid)
    override val currentUserId: String? get() = userId.value
    override fun observeUserId(): Flow<String?> = userId
    override suspend fun idToken(): String? = userId.value?.let { "token-for-$it" }
    var failSignIn = false
    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) {
        if (failSignIn) error("sign-in failed")
        userId.value = "google-$idToken"
    }
    override suspend fun signInWithApple(idToken: String, rawNonce: String) {
        if (failSignIn) error("sign-in failed")
        userId.value = "apple-$idToken"
    }
    override suspend fun signOut() { userId.value = null }

    override val signInProvider: SignInProvider?
        get() = userId.value?.let { if (it.startsWith("apple-")) SignInProvider.APPLE else SignInProvider.GOOGLE }

    /** Who the user last proved to be; deleting needs it, as Firebase requires a recent sign-in. */
    var reauthenticatedAs: String? = null
    var failDelete = false
    val deleted = mutableListOf<String>()

    override suspend fun reauthenticateWithGoogle(idToken: String, accessToken: String?) {
        if (failSignIn) error("sign-in failed")
        reauthenticatedAs = "google-$idToken"
    }

    override suspend fun reauthenticateWithApple(idToken: String, rawNonce: String) {
        if (failSignIn) error("sign-in failed")
        reauthenticatedAs = "apple-$idToken"
    }

    override suspend fun deleteAccount() {
        val uid = userId.value ?: return
        if (failDelete || reauthenticatedAs == null) error("requires recent login")
        deleted += uid
        userId.value = null
    }
}

class FakePhotoRepository : PhotoRepository {
    val photos = MutableStateFlow<Map<Long, List<SpeciesPhoto>>>(emptyMap())
    override fun observeUserPhotos(speciesId: Long): Flow<List<SpeciesPhoto>> = photos.map { it[speciesId].orEmpty() }
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

class FakeModerationRepository : ModerationRepository {
    val hidden = MutableStateFlow(HiddenContent())
    val reports = mutableListOf<Pair<String, ReportReason>>()
    var failReports = false

    override fun observeHidden(): Flow<HiddenContent> = hidden

    override suspend fun report(speciesId: Long, photoUrl: String, uploaderId: String?, reason: ReportReason) {
        hidden.value = hidden.value.copy(photoUrls = hidden.value.photoUrls + photoUrl)
        if (failReports) error("offline")
        reports += photoUrl to reason
    }

    override suspend fun hideContributor(uploaderId: String) {
        hidden.value = hidden.value.copy(contributors = hidden.value.contributors + uploaderId)
    }
}

class FakeCitationRepository(var published: List<Citation> = emptyList()) : CitationRepository {
    var fail = false
    override suspend fun citations(): List<Citation> = if (fail) error("fetch failed") else published
}
