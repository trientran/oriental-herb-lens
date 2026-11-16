package com.uri.lee.dl.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgesTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun photo(id: Int, species: Long, daysAgo: Int, needed: Boolean = false) =
        Contribution("p$id", ContributionKind.PHOTO, speciesKey = species, url = "u$id", neededPhotos = needed, createdAtMillis = now - daysAgo * day)

    @Test
    fun summaryCountsPhotosSpeciesNamesAndModels() {
        val contributions = listOf(
            photo(1, 10, 1), photo(2, 10, 1), photo(3, 11, 1),
            Contribution("n1", ContributionKind.NAME, speciesKey = 10),
            Contribution("m1", ContributionKind.MODEL, modelId = "abc"),
        )
        assertEquals(ContributionSummary(photos = 3, species = 2, names = 1, models = 1, modelAdds = 4), Badges.summary(contributions, 4))
    }

    @Test
    fun onlyContributionsPastReviewCountTowardBadges() {
        val recent = listOf(photo(1, 10, 2))
        assertTrue(Badges.earned(recent, 0, now).isEmpty())
        assertEquals(setOf(Badge.FIRST_PHOTO), Badges.earned(listOf(photo(1, 10, 7)), 0, now))
    }

    @Test
    fun photosTakenDownDontCount() {
        val photos = (1..10).map { photo(it, it.toLong(), 30) }
        assertTrue(Badge.PHOTOS_10 in Badges.earned(photos, 0, now))
        val removed = Badges.earned(photos, 0, now) { it.id != "p1" }
        assertTrue(Badge.PHOTOS_10 !in removed)
        assertTrue(Badge.SPECIES_5 in removed)
    }

    @Test
    fun gapFillerNeedsAPhotoOfASpeciesTheModelDidntKnow() {
        assertTrue(Badge.GAP_FILLER !in Badges.earned(listOf(photo(1, 10, 10)), 0, now))
        assertTrue(Badge.GAP_FILLER in Badges.earned(listOf(photo(1, 10, 10, needed = true)), 0, now))
    }

    @Test
    fun modelAddsCountWhateverTheirAge() {
        assertEquals(setOf(Badge.MODEL_ADDS_10), Badges.earned(emptyList(), 10, now))
    }

    @Test
    fun unstampedContributionsWaitForTheServerTime() {
        val unstamped = Contribution("p1", ContributionKind.PHOTO, speciesKey = 1, url = "u")
        assertTrue(Badges.earned(listOf(unstamped), 0, now).isEmpty())
        assertEquals(1, Badges.summary(listOf(unstamped), 0).photos)
    }
}
