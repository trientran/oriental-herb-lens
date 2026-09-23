package com.uri.lee.dl.data.catalog

import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.repository.SpeciesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Holds the whole catalog in memory (a few thousand rows), parsed on first use.
 * Replaced by a SQLDelight-backed implementation with search in Phase 2.
 */
class CsvSpeciesRepository(
    private val source: CatalogSource,
    private val reader: SpeciesCsvReader,
    private val dispatchers: AppDispatchers,
) : SpeciesRepository {

    private val mutex = Mutex()

    @Volatile
    private var byId: Map<Long, Species>? = null

    override suspend fun get(id: Long): Species? = catalog()[id]

    override suspend fun getAll(ids: Collection<Long>): Map<Long, Species> {
        val catalog = catalog()
        return ids.mapNotNull { id -> catalog[id]?.let { id to it } }.toMap()
    }

    override suspend fun all(): List<Species> = catalog().values.toList()

    /** Drops the parsed catalog so the next read picks up a newly installed file. */
    fun invalidate() {
        byId = null
    }

    private suspend fun catalog(): Map<Long, Species> =
        byId ?: mutex.withLock { byId ?: load().also { byId = it } }

    private suspend fun load(): Map<Long, Species> = withContext(dispatchers.default) {
        val result = try {
            reader.read(source.readText())
        } catch (e: CatalogFormatException) {
            Timber.e(e, "Installed catalog is unreadable; using the bundled one")
            reader.read(source.readBundledText())
        }
        if (result.skippedRows.isNotEmpty()) Timber.w("Catalog rows skipped: ${result.skippedRows}")
        result.species.associateBy { it.id }
    }
}
