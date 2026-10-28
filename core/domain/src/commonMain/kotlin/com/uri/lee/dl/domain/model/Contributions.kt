package com.uri.lee.dl.domain.model

import kotlinx.coroutines.flow.Flow

enum class ContributionKind { PHOTO, NAME, MODEL }

/**
 * Something the signed-in user shared, as kept on their own record (`users/{uid}/contributions`),
 * which only they can read. Shown back to them in Profile; never ranked against others.
 */
data class Contribution(
    val id: String,
    val kind: ContributionKind,
    /** The species a photo or name is for; null for a model. */
    val speciesKey: Long? = null,
    /** A photo's address, to check it's still shown. */
    val url: String? = null,
    /** A shared model's id. */
    val modelId: String? = null,
    /** A photo of a species the herb model didn't know yet when it was shared. */
    val neededPhotos: Boolean = false,
    /** When it was shared; null until the server has stamped it. */
    val createdAtMillis: Long? = null,
)

/** Totals for Profile's "Your contributions". */
data class ContributionSummary(
    val photos: Int = 0,
    val species: Int = 0,
    val names: Int = 0,
    val models: Int = 0,
    /** How many other people added this user's shared models. */
    val modelAdds: Int = 0,
) {
    val isEmpty: Boolean get() = photos == 0 && names == 0 && models == 0
}

/**
 * Milestones shown privately in Profile. Only contributions that stood through the review period
 * count ([Badges.REVIEW_DAYS]), and photos and models only while they're still shown, so a badge
 * can't be earned with photos that were reported and removed.
 */
enum class Badge(val threshold: Int) {
    FIRST_PHOTO(1),
    PHOTOS_10(10),
    PHOTOS_50(50),
    SPECIES_5(5),
    SPECIES_25(25),

    /** A photo of a species the herb model didn't know yet. */
    GAP_FILLER(1),
    FIRST_NAME(1),
    NAMES_10(10),
    FIRST_MODEL(1),

    /** Others added this user's shared models ten times. */
    MODEL_ADDS_10(10),
}

object Badges {
    /** Days a contribution must stand, unreported, before it counts toward a badge. */
    const val REVIEW_DAYS = 7
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Totals of everything shared, whether or not it has stood through review yet; [stillShown]
     * leaves out photos and models that were taken down.
     */
    fun summary(contributions: List<Contribution>, modelAdds: Int, stillShown: (Contribution) -> Boolean = { true }): ContributionSummary {
        val shown = contributions.filter(stillShown)
        val photos = shown.filter { it.kind == ContributionKind.PHOTO }
        return ContributionSummary(
            photos = photos.size,
            species = photos.mapNotNull { it.speciesKey }.toSet().size,
            names = shown.count { it.kind == ContributionKind.NAME },
            models = shown.count { it.kind == ContributionKind.MODEL },
            modelAdds = modelAdds,
        )
    }

    /** The badges earned by [nowMillis]: from contributions older than the review period and still shown. */
    fun earned(contributions: List<Contribution>, modelAdds: Int, nowMillis: Long, stillShown: (Contribution) -> Boolean = { true }): Set<Badge> {
        val counted = contributions.filter { c ->
            val at = c.createdAtMillis ?: return@filter false
            nowMillis - at >= REVIEW_DAYS * DAY_MILLIS && stillShown(c)
        }
        val totals = summary(counted, modelAdds)
        val gapPhotos = counted.count { it.kind == ContributionKind.PHOTO && it.neededPhotos }
        return Badge.entries.filterTo(mutableSetOf()) { badge ->
            val count = when (badge) {
                Badge.FIRST_PHOTO, Badge.PHOTOS_10, Badge.PHOTOS_50 -> totals.photos
                Badge.SPECIES_5, Badge.SPECIES_25 -> totals.species
                Badge.GAP_FILLER -> gapPhotos
                Badge.FIRST_NAME, Badge.NAMES_10 -> totals.names
                Badge.FIRST_MODEL -> totals.models
                Badge.MODEL_ADDS_10 -> modelAdds
            }
            count >= badge.threshold
        }
    }
}

/** What the signed-in user has shared, with what's needed to show it back to them. */
interface ContributionHistoryRepository {
    /** The signed-in user's contributions, newest first; empty while signed out. */
    fun observe(): Flow<List<Contribution>>

    /** Which of [contributions] are still shown: photos still on their species page, models still listed. Names always are. */
    suspend fun stillShown(contributions: List<Contribution>): Set<String>

    /** How many times others added the signed-in user's shared models. */
    suspend fun modelAdds(modelIds: List<String>): Int
}
