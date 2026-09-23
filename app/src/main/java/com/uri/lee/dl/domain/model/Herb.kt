package com.uri.lee.dl.domain.model

/** Text kept in both app languages. */
data class LocalizedText(val vietnamese: String, val english: String) {
    fun pick(vietnamese: Boolean): String = if (vietnamese) this.vietnamese else english

    companion object {
        val EMPTY = LocalizedText("", "")
    }
}

data class GeoLocation(val latitude: Double, val longitude: Double)

/** A contributed photo of a herb. */
data class HerbImage(
    val url: String,
    val uploaderId: String?,
    /** Where the photo was taken, when the contributor supplied it. */
    val location: GeoLocation?,
)

/** A herb's full record with its medicinal content. */
data class HerbProfile(
    val id: Long,
    val latinName: String,
    val vietnameseName: String,
    val englishName: String,
    val overview: LocalizedText,
    val dosing: LocalizedText,
    val sideEffects: LocalizedText,
    val interactions: LocalizedText,
    val images: List<HerbImage>,
)

/** Enough of a herb to show it in a list. */
data class HerbSummary(
    val id: Long,
    val latinName: String,
    val vietnameseName: String,
    val imageUrl: String?,
)

/** Opaque position after the last item of a page. */
data class HerbPageKey(val sortValue: String, val documentId: String)

data class HerbPage(val herbs: List<HerbSummary>, val next: HerbPageKey?)
