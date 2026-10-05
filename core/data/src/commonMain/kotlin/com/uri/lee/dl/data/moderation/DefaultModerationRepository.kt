package com.uri.lee.dl.data.moderation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.uri.lee.dl.core.firebase.AuthClient
import com.uri.lee.dl.core.firebase.FirestoreClient
import com.uri.lee.dl.domain.moderation.HiddenContent
import com.uri.lee.dl.domain.moderation.ModerationRepository
import com.uri.lee.dl.domain.moderation.ReportReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reports go to Firestore (`photoReports`) for the administrator; hidden photos and contributors stay in DataStore. */
internal class DefaultModerationRepository(
    private val firestore: FirestoreClient,
    private val auth: AuthClient,
    private val prefs: DataStore<Preferences>,
) : ModerationRepository {

    override fun observeHidden(): Flow<HiddenContent> = prefs.data.map {
        HiddenContent(photoUrls = it[HIDDEN_PHOTOS].orEmpty(), contributors = it[HIDDEN_CONTRIBUTORS].orEmpty())
    }

    override suspend fun report(speciesId: Long, photoUrl: String, uploaderId: String?, reason: ReportReason) {
        // Hidden first: the user sees the effect even if the report can't be sent (offline)
        prefs.edit { it[HIDDEN_PHOTOS] = it[HIDDEN_PHOTOS].orEmpty() + photoUrl }
        firestore.addPhotoReport(speciesId, photoUrl, uploaderId, reason.name, auth.currentUserId)
    }

    override suspend fun hideContributor(uploaderId: String) {
        prefs.edit { it[HIDDEN_CONTRIBUTORS] = it[HIDDEN_CONTRIBUTORS].orEmpty() + uploaderId }
    }

    private companion object {
        val HIDDEN_PHOTOS = stringSetPreferencesKey("hidden_photo_urls")
        val HIDDEN_CONTRIBUTORS = stringSetPreferencesKey("hidden_contributors")
    }
}
