package com.uri.lee.dl.domain.moderation

import com.uri.lee.dl.domain.media.LocalImage
import kotlinx.coroutines.flow.Flow

/** Why a user reports a shared photo. */
enum class ReportReason { NOT_A_PLANT, SEXUAL_OR_VIOLENT, SHOWS_A_PERSON, WRONG_SPECIES, OTHER }

/** Photos and contributors this user chose not to see, on this device. */
data class HiddenContent(val photoUrls: Set<String> = emptySet(), val contributors: Set<String> = emptySet()) {
    fun hides(url: String, uploaderId: String?): Boolean = url in photoUrls || (uploaderId != null && uploaderId in contributors)
}

/**
 * Reporting and hiding shared photos (App Store guideline 1.2). Reports go to the administrator;
 * hiding is immediate and stays on the device.
 */
interface ModerationRepository {
    fun observeHidden(): Flow<HiddenContent>

    /** Reports a shared photo to the administrator and hides it for this user. Needs a signed-in user. */
    suspend fun report(speciesId: Long, photoUrl: String, uploaderId: String?, reason: ReportReason)

    /** Hides every photo this contributor shared, for this user. */
    suspend fun hideContributor(uploaderId: String)
}

/** Checks a photo shows a plant before it's shared, on the device. */
fun interface PlantCheck {
    /** False when no plant, flower or leaf is seen; null when the photo couldn't be read. */
    suspend fun showsPlant(image: LocalImage): Boolean?
}
