package com.uri.lee.dl.data.catalog

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.text.VietnameseText
import com.uri.lee.dl.data.content.ReleaseSource
import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.search.SpeciesMatch
import com.uri.lee.dl.domain.search.SpeciesSearchIndex
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.url.URL

/**
 * The browser's catalog: the published CSV (Remote Config, on R2) or the copy served with the
 * site, parsed once per page load (about 200 ms) and kept in memory. The browser's HTTP cache
 * keeps the download.
 */
internal class MemorySpeciesRepository(
    private val http: HttpClient,
    private val releases: ReleaseSource,
    private val reader: SpeciesCsvReader,
) : SpeciesRepository {

    private class Catalog(species: List<Species>) {
        val byId = species.associateBy { it.id }
        val byVietnamese = species.sortedWith(
            compareBy<Species> { it.sortVietnamese.isEmpty() }.thenBy { it.sortVietnamese }.thenBy { it.sortScientific },
        )
        val byScientific = species.sortedBy { it.sortScientific }
        val index = SpeciesSearchIndex(species)
    }

    private val mutex = Mutex()
    private var catalog: Catalog? = null

    override suspend fun get(id: Long): Species? = catalog().byId[id]

    override suspend fun getAll(ids: Collection<Long>): Map<Long, Species> {
        val byId = catalog().byId
        return ids.mapNotNull { byId[it] }.associateBy { it.id }
    }

    override suspend fun all(): List<Species> = catalog().byScientific

    override suspend fun page(offset: Int, limit: Int, sortByVietnameseName: Boolean): List<Species> {
        val sorted = if (sortByVietnameseName) catalog().byVietnamese else catalog().byScientific
        return sorted.drop(offset).take(limit)
    }

    override suspend fun search(query: String, limit: Int): List<SpeciesMatch> = catalog().index.search(query, limit)

    private suspend fun catalog(): Catalog = catalog ?: mutex.withLock {
        catalog ?: Catalog(load()).also { catalog = it }
    }

    private suspend fun load(): List<Species> {
        val published = try {
            releases.latest()[ContentKind.CATALOG]?.url
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Remote Config unreachable; using the catalog served with the site" }
            null
        }
        published?.let { url ->
            try {
                return read(fetch(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Published catalog unusable; using the one served with the site" }
            }
        }
        // Relative to the page (the app may live in a folder, e.g. /app/); Ktor would resolve it
        // against the site's root
        return read(fetch(URL(BUNDLED_CATALOG, window.location.href).href))
    }

    private suspend fun fetch(url: String): String {
        val response = http.get(url)
        check(response.status.isSuccess()) { "HTTP ${response.status.value} for $url" }
        return response.bodyAsText()
    }

    private fun read(text: String): List<Species> {
        val result = reader.read(text)
        if (result.skippedRows.isNotEmpty()) log.w { "Catalog rows skipped: ${result.skippedRows}" }
        log.i { "Loaded ${result.species.size} species" }
        return result.species
    }

    private companion object {
        /** Served next to the web app's index.html. */
        const val BUNDLED_CATALOG = "herb_catalog.csv"
        val log = Logger.withTag("Catalog")

        // The same order as the SQLite catalog's sort columns
        val Species.sortVietnamese get() = VietnameseText.normalizeQuery(preferredVietnameseName.orEmpty())
        val Species.sortScientific get() = VietnameseText.normalizeQuery(scientificName)
    }
}
