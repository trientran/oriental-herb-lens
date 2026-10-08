package com.uri.lee.dl.data.catalog

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.core.common.text.VietnameseText
import com.uri.lee.dl.data.db.HerbLensDatabase
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.model.Taxonomy
import com.uri.lee.dl.domain.repository.SpeciesRepository
import com.uri.lee.dl.domain.search.SpeciesMatch
import com.uri.lee.dl.domain.search.SpeciesSearchIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import co.touchlab.kermit.Logger
import okio.ByteString.Companion.encodeUtf8
import kotlin.concurrent.Volatile
import com.uri.lee.dl.data.db.Species as SpeciesRow

/**
 * The catalog in SQLite. The CSV is imported only when its content changes (compared by
 * SHA-256), in one transaction, so readers see either the old catalog or the new one.
 */
internal class SqlSpeciesRepository(
    private val db: HerbLensDatabase,
    private val source: CatalogSource,
    private val reader: SpeciesCsvReader,
    private val dispatchers: AppDispatchers,
) : SpeciesRepository {

    private val mutex = Mutex()

    @Volatile
    private var checked = false

    @Volatile
    private var searchIndex: SpeciesSearchIndex? = null

    override suspend fun get(id: Long): Species? = query { db.speciesQueries.byId(id).executeAsOneOrNull()?.toDomain() }

    override suspend fun getAll(ids: Collection<Long>): Map<Long, Species> = query {
        // SQLite caps bound parameters; chunk to stay well under it.
        ids.distinct().chunked(500).flatMap { db.speciesQueries.byIds(it).executeAsList() }
            .map { it.toDomain() }
            .associateBy { it.id }
    }

    override suspend fun all(): List<Species> = query { db.speciesQueries.all().executeAsList().map { it.toDomain() } }

    override suspend fun page(offset: Int, limit: Int, sortByVietnameseName: Boolean): List<Species> = query {
        val q = if (sortByVietnameseName) {
            db.speciesQueries.pageByVietnamese(limit.toLong(), offset.toLong())
        } else {
            db.speciesQueries.pageByScientific(limit.toLong(), offset.toLong())
        }
        q.executeAsList().map { it.toDomain() }
    }

    override suspend fun search(query: String, limit: Int): List<SpeciesMatch> {
        val species = if (searchIndex == null) all() else emptyList()
        return withContext(dispatchers.default) {
            val index = searchIndex ?: SpeciesSearchIndex(species).also { searchIndex = it }
            index.search(query, limit)
        }
    }

    /** Called after content sync installs a new catalog file: re-check and re-import on next use. */
    fun invalidate() {
        checked = false
        searchIndex = null
    }

    private suspend fun <T> query(block: () -> T): T {
        ensureImported()
        return withContext(dispatchers.io) { block() }
    }

    private suspend fun ensureImported() {
        if (checked) return
        mutex.withLock {
            if (checked) return
            withContext(dispatchers.io) { importIfChanged() }
            checked = true
        }
    }

    private fun importIfChanged() {
        val text = try {
            source.readText()
        } catch (e: Exception) {
            log.e(e) { "Installed catalog unreadable; using the bundled one" }
            source.readBundledText()
        }
        val hash = sha256(text)
        if (db.catalogMetaQueries.valueOf(SOURCE_HASH).executeAsOneOrNull() == hash) return

        val result = try {
            reader.read(text)
        } catch (e: CatalogFormatException) {
            log.e(e) { "Catalog unreadable; importing the bundled one" }
            reader.read(source.readBundledText())
        }
        if (result.skippedRows.isNotEmpty()) log.w { "Catalog rows skipped: ${result.skippedRows}" }
        db.transaction {
            db.speciesQueries.deleteAll()
            result.species.forEach { db.speciesQueries.insert(it.toRow()) }
            db.catalogMetaQueries.put(SOURCE_HASH, hash)
        }
        searchIndex = null
        log.i { "Imported ${result.species.size} species into the catalog database" }
    }

    companion object {
        private const val SOURCE_HASH = "source_sha256"
        private const val NAME_SEPARATOR = "\n"

        private val log = Logger.withTag("Catalog")

        private fun sha256(text: String) = text.encodeUtf8().sha256().hex()

        internal fun Species.toRow() = SpeciesRow(
            id = id,
            scientific_name = scientificName,
            authorship = authorship,
            family = family,
            genus = genus,
            vietnamese_names = vietnameseNames.joinToString(NAME_SEPARATOR) { it.replace('\n', ' ') },
            english_names = englishNames.joinToString(NAME_SEPARATOR) { it.replace('\n', ' ') },
            sort_vietnamese = VietnameseText.normalizeQuery(preferredVietnameseName.orEmpty()),
            sort_scientific = VietnameseText.normalizeQuery(scientificName),
            kingdom = taxonomy.kingdom,
            phylum = taxonomy.phylum,
            class_name = taxonomy.className,
            order_name = taxonomy.order,
            rank = taxonomy.rank,
            taxonomic_status = taxonomy.status,
            published_in = taxonomy.publishedIn,
            basionym = taxonomy.basionym,
            other_names = otherNames.flatMap { (language, names) -> names.map { "$language\t${it.replace('\n', ' ').replace('\t', ' ')}" } }
                .joinToString(NAME_SEPARATOR),
        )

        internal fun SpeciesRow.toDomain() = Species(
            id = id,
            scientificName = scientific_name,
            authorship = authorship,
            family = family,
            genus = genus,
            vietnameseNames = vietnamese_names.split(NAME_SEPARATOR).filter { it.isNotEmpty() },
            englishNames = english_names.split(NAME_SEPARATOR).filter { it.isNotEmpty() },
            taxonomy = Taxonomy(kingdom, phylum, class_name, order_name, rank, taxonomic_status, published_in, basionym),
            otherNames = other_names.split(NAME_SEPARATOR).filter { '\t' in it }
                .groupBy({ it.substringBefore('\t') }, { it.substringAfter('\t') }),
        )
    }
}
