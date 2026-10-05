package com.uri.lee.dl.data.gbif

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.domain.model.PhotoCredit
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import com.uri.lee.dl.domain.repository.ReferencePhotoRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Photos from GBIF occurrence records of a species (taxon key = species key), keeping only
 * full-size originals (`original.jpg` / `original.jpeg`), which in practice are iNaturalist photos.
 * Results are cached for the session, so revisiting a species costs no request.
 */
internal class GbifPhotoRepository(
    private val client: HttpClient,
    private val dispatchers: AppDispatchers,
) : ReferencePhotoRepository {

    private val mutex = Mutex()
    /** Most recently used last; the first entry is evicted when full. */
    private val cache = LinkedHashMap<Long, List<SpeciesPhoto>>()

    override suspend fun photos(speciesId: Long, limit: Int): List<SpeciesPhoto> {
        mutex.withLock { cache.remove(speciesId)?.also { cache[speciesId] = it } }?.let { return it.take(limit) }
        val page: OccurrencePage = withContext(dispatchers.io) {
            client.get("$API/occurrence/search") {
                parameter("mediaType", "StillImage")
                parameter("taxon_key", speciesId)
                parameter("limit", OCCURRENCES_PER_REQUEST)
            }.body()
        }
        val photos = toPhotos(page)
        mutex.withLock {
            cache[speciesId] = photos
            if (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        }
        return photos.take(limit)
    }

    @Serializable
    internal data class OccurrencePage(val results: List<Occurrence> = emptyList())

    @Serializable
    internal data class Occurrence(val key: Long, val media: List<Media> = emptyList())

    @Serializable
    internal data class Media(
        val type: String? = null,
        val identifier: String? = null,
        val references: String? = null,
        val creator: String? = null,
        val rightsHolder: String? = null,
        val publisher: String? = null,
        val license: String? = null,
    )

    companion object {
        private const val API = "https://api.gbif.org/v1"
        private const val OCCURRENCES_PER_REQUEST = 50
        private const val CACHE_SIZE = 50

        internal fun toPhotos(page: OccurrencePage): List<SpeciesPhoto> =
            page.results.flatMap { occurrence ->
                occurrence.media.mapNotNull { media -> media.toPhoto(occurrence.key) }
            }.distinctBy { it.url }

        private fun Media.toPhoto(occurrenceKey: Long): SpeciesPhoto? {
            val url = identifier?.takeIf { type == "StillImage" && isOriginal(it) } ?: return null
            return SpeciesPhoto(
                url = url,
                thumbnailUrl = smallVariant(url),
                source = PhotoSource.GBIF,
                credit = PhotoCredit(
                    creator = creator ?: rightsHolder ?: "Unknown",
                    license = license?.let(::shortLicense) ?: "All rights reserved",
                    licenseUrl = license?.takeIf { it.startsWith("http") },
                    publisher = publisher,
                    sourceUrl = references ?: "https://www.gbif.org/occurrence/$occurrenceKey",
                ),
            )
        }

        internal fun isOriginal(url: String): Boolean {
            val path = url.substringBefore('?').lowercase()
            return path.endsWith("/original.jpg") || path.endsWith("/original.jpeg")
        }

        /**
         * iNaturalist serves the same photo at `small.<ext>`, about 240 px: enough for a grid tile,
         * and quick from its servers far from Australia and Vietnam. Full size opens on a tap.
         */
        internal fun smallVariant(url: String): String =
            if ("inaturalist" in url) url.replace(Regex("/original\\.(jpe?g)", RegexOption.IGNORE_CASE), "/small.$1") else url

        /** "http://creativecommons.org/licenses/by-nc/4.0/" → "CC BY-NC 4.0"; also GBIF's "CC_BY_4_0" form. */
        internal fun shortLicense(license: String): String {
            val creativeCommons = Regex("creativecommons\\.org/licenses/([a-z-]+)/([0-9.]+)", RegexOption.IGNORE_CASE).find(license)
            if (creativeCommons != null) {
                val (kind, version) = creativeCommons.destructured
                return "CC ${kind.uppercase()} $version"
            }
            if (Regex("publicdomain/zero", RegexOption.IGNORE_CASE).containsMatchIn(license)) return "CC0 1.0"
            val enum = Regex("^CC_?(BY(?:_[A-Z]+)*|0)_(\\d)_(\\d)$").find(license.trim().uppercase())
            if (enum != null) {
                val (kind, major, minor) = enum.destructured
                return if (kind == "0") "CC0 $major.$minor" else "CC ${kind.replace('_', '-')} $major.$minor"
            }
            return license
        }
    }
}
