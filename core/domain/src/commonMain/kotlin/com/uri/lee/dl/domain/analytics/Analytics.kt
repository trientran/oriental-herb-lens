package com.uri.lee.dl.domain.analytics

/**
 * Usage statistics for research (how people identify and explore herbs). Nothing that identifies
 * a person is sent: no account, name, email, photo or place. Users can turn it off in Profile.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** A screen was shown; [name] is the route, e.g. "species". */
    fun screen(name: String)
}

/** The events, with GA4-style snake_case names and parameters. */
sealed class AnalyticsEvent(val name: String, val parameters: Map<String, Any>) {

    /** A herb was identified: one camera pick, one photo, or one photo of several. */
    class Identified(mode: String, source: String, topSpeciesId: Long?, topConfidence: Float?, resultCount: Int) : AnalyticsEvent(
        "identify",
        buildMap {
            put("mode", mode)
            put("source", source)
            put("result_count", resultCount)
            put("recognised", topSpeciesId != null)
            topSpeciesId?.let { put("species_id", it) }
            topConfidence?.let { put("confidence", (it * 100).toLong()) }
        },
    )

    /** An identification result was opened. */
    class ResultOpened(speciesId: Long, confidence: Float, rank: Int, mode: String, source: String) : AnalyticsEvent(
        "open_result",
        mapOf("species_id" to speciesId, "confidence" to (confidence * 100).toLong(), "rank" to rank, "mode" to mode, "source" to source),
    )

    class SpeciesViewed(speciesId: Long) : AnalyticsEvent("view_species", mapOf("species_id" to speciesId))

    class FavoriteAdded(speciesId: Long) : AnalyticsEvent("add_favorite", mapOf("species_id" to speciesId))

    class PhotosShared(speciesId: Long, count: Int) : AnalyticsEvent("share_photos", mapOf("species_id" to speciesId, "count" to count))

    class NameSuggested(speciesId: Long) : AnalyticsEvent("suggest_name", mapOf("species_id" to speciesId))

    class PhotoReported(reason: String) : AnalyticsEvent("report_photo", mapOf("reason" to reason))
}

/** For tests and previews. */
object NoAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
    override fun screen(name: String) = Unit
}
