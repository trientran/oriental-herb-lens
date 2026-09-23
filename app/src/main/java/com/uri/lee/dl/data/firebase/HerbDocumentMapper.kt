package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.FireStoreHerb
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.model.HerbImage
import com.uri.lee.dl.domain.model.HerbProfile
import com.uri.lee.dl.domain.model.HerbSummary
import com.uri.lee.dl.domain.model.LocalizedText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Converts herb documents to domain models. A document's `images` map goes from image URL to
 * either the uploader's uid (older uploads) or a JSON object `{"uid", "lat", "lng"}`.
 */
internal object HerbDocumentMapper {

    @Serializable
    private data class ImageDetail(val uid: String? = null, val lat: Double? = null, val lng: Double? = null)

    private val json = Json { ignoreUnknownKeys = true }

    fun toProfile(id: Long, doc: FireStoreHerb) = HerbProfile(
        id = id,
        latinName = doc.latinName,
        vietnameseName = doc.viName,
        englishName = doc.enName,
        overview = LocalizedText(doc.viOverview, doc.enOverview),
        dosing = LocalizedText(doc.viDosing, doc.enDosing),
        sideEffects = LocalizedText(doc.viSideEffects, doc.enSideEffects),
        interactions = LocalizedText(doc.viInteractions, doc.enInteractions),
        images = doc.images.orEmpty().map { (url, detail) -> toImage(url, detail) },
    )

    fun toSummary(id: Long, doc: FireStoreHerb) = HerbSummary(
        id = id,
        latinName = doc.latinName,
        vietnameseName = doc.viName,
        // The first image, so a list row doesn't change picture every time it is redrawn
        imageUrl = doc.images?.keys?.firstOrNull(),
    )

    fun toImage(url: String, detail: String): HerbImage {
        val parsed = if (detail.trimStart().startsWith("{")) {
            runCatching { json.decodeFromString(ImageDetail.serializer(), detail) }.getOrNull()
        } else {
            null
        }
        if (parsed == null) return HerbImage(url, uploaderId = detail.ifBlank { null }, location = null)
        val location = if (parsed.lat != null && parsed.lng != null) GeoLocation(parsed.lat, parsed.lng) else null
        return HerbImage(url, uploaderId = parsed.uid, location = location)
    }

    /** The value stored for an uploaded image; the inverse of [toImage]. */
    fun imageDetail(uploaderId: String, location: GeoLocation?): String =
        json.encodeToString(ImageDetail.serializer(), ImageDetail(uploaderId, location?.latitude, location?.longitude))
}
