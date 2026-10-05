package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the `images` map of a `herbs/{speciesKey}` document: image URL to either the uploader's
 * uid (older uploads) or a JSON object `{"uid", "lat", "lng"}`.
 */
internal object HerbDocumentMapper {

    @Serializable
    private data class ImageDetail(val uid: String? = null, val lat: Double? = null, val lng: Double? = null)

    private val json = Json { ignoreUnknownKeys = true }

    fun toPhoto(url: String, detail: String): SpeciesPhoto {
        val parsed = if (detail.trimStart().startsWith("{")) {
            runCatching { json.decodeFromString(ImageDetail.serializer(), detail) }.getOrNull()
        } else {
            null
        }
        if (parsed == null) return userPhoto(url, uploaderId = detail.ifBlank { null }, location = null)
        val location = if (parsed.lat != null && parsed.lng != null) GeoLocation(parsed.lat, parsed.lng) else null
        return userPhoto(url, uploaderId = parsed.uid, location = location)
    }

    private fun userPhoto(url: String, uploaderId: String?, location: GeoLocation?) =
        SpeciesPhoto(url = url, thumbnailUrl = url, source = PhotoSource.USER, uploaderId = uploaderId, location = location)

    /** The value stored for an uploaded image; the inverse of [toPhoto]. */
    fun imageDetail(uploaderId: String, location: GeoLocation?): String =
        json.encodeToString(ImageDetail.serializer(), ImageDetail(uploaderId, location?.latitude, location?.longitude))
}
