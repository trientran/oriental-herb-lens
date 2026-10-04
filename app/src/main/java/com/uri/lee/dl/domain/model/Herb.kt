package com.uri.lee.dl.domain.model

data class GeoLocation(val latitude: Double, val longitude: Double)

enum class PhotoSource { USER, GBIF }

/** Who made a third-party photo and under which licence; must be shown with the photo. */
data class PhotoCredit(
    val creator: String,
    /** Short form such as "CC BY-NC 4.0". */
    val license: String,
    val licenseUrl: String?,
    /** e.g. "iNaturalist" */
    val publisher: String?,
    /** The record the photo comes from, for a "view source" link. */
    val sourceUrl: String?,
)

/** A photo of a species, contributed by a user (stored on R2) or taken from GBIF. */
data class SpeciesPhoto(
    /** Full-size image, for the full-screen viewer. */
    val url: String,
    /** A smaller version for lists and grids; the same as [url] when none exists. */
    val thumbnailUrl: String,
    val source: PhotoSource,
    val uploaderId: String? = null,
    /** Where the photo was taken, when known. */
    val location: GeoLocation? = null,
    val credit: PhotoCredit? = null,
)
