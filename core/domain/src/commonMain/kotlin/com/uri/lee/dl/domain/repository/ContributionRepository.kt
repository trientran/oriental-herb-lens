package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.GeoLocation

/** An image already stored on the image host, ready to be attached to a herb. */
data class UploadedImage(val url: String, val uploaderId: String, val location: GeoLocation?)

/** The only two things users write: photos of a herb and a suggested common name. */
interface ContributionRepository {
    suspend fun addImages(herbId: Long, images: List<UploadedImage>)

    /** A common (vernacular) name for the herb in [language], an ISO 639-1 code from NameLanguages.ENABLED. */
    suspend fun suggestName(herbId: Long, language: String, name: String)
}
