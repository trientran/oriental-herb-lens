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

    /** A species' full details were opened, and from there its GBIF page. */
    class SpeciesInfoViewed(speciesId: Long) : AnalyticsEvent("view_species_info", mapOf("species_id" to speciesId))

    class GbifOpened(speciesId: Long) : AnalyticsEvent("open_gbif", mapOf("species_id" to speciesId))

    /** The user trained one of their own models: [update] is "full" or "add" (only what's new, with replay). */
    class ModelTrained(quality: String, speciesCount: Int, photoCount: Int, update: String) : AnalyticsEvent(
        "train_model",
        mapOf("quality" to quality, "species_count" to speciesCount, "photo_count" to photoCount, "update" to update),
    )

    /** A model file was imported; [trainable] when it came from Herb Lens and can learn more. */
    class ModelImported(trainable: Boolean, speciesCount: Int) : AnalyticsEvent(
        "import_model",
        mapOf("trainable" to trainable, "species_count" to speciesCount),
    )

    class ModelShared(speciesCount: Int) : AnalyticsEvent("share_model", mapOf("species_count" to speciesCount))

    /** The user opened one of their own models to identify with it. */
    class ModelTried(speciesCount: Int) : AnalyticsEvent("try_model", mapOf("species_count" to speciesCount))

    /** A reference from Profile → How to cite was copied; [url] is its DOI or web link, if any. */
    class CitationCopied(position: Int, url: String?) : AnalyticsEvent(
        "copy_citation",
        buildMap {
            put("position", position)
            url?.let { put("url", it.take(100)) }
        },
    )

    /**
     * Research mode, to count who runs studies with the app: the run's size only, never its results
     * (those stay in the researcher's own files).
     */
    class ResearchStarted(resumed: Boolean, backbones: Int, runs: Int, speciesCount: Int, photoCount: Int) : AnalyticsEvent(
        "research_start",
        mapOf("resumed" to resumed, "backbones" to backbones, "runs" to runs, "species_count" to speciesCount, "photo_count" to photoCount),
    )

    class ResearchFinished(runs: Int, minutes: Long) : AnalyticsEvent("research_finish", mapOf("runs" to runs, "minutes" to minutes))

    class ResearchSaved(runs: Int) : AnalyticsEvent("research_save", mapOf("runs" to runs))
}

/** For tests and previews. */
object NoAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
    override fun screen(name: String) = Unit
}
