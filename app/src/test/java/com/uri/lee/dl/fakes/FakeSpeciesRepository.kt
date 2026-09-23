package com.uri.lee.dl.fakes

import com.uri.lee.dl.domain.model.Species
import com.uri.lee.dl.domain.repository.SpeciesRepository

class FakeSpeciesRepository(species: List<Species> = emptyList()) : SpeciesRepository {
    private val byId = species.associateBy { it.id }
    var lookups = 0
        private set

    override suspend fun get(id: Long): Species? = byId[id].also { lookups++ }

    override suspend fun getAll(ids: Collection<Long>): Map<Long, Species> {
        lookups++
        return ids.mapNotNull { id -> byId[id]?.let { id to it } }.toMap()
    }

    override suspend fun all(): List<Species> = byId.values.toList()
}

fun species(id: Long, scientificName: String, vi: List<String> = emptyList(), en: List<String> = emptyList()) =
    Species(
        id = id,
        scientificName = scientificName,
        authorship = "",
        family = "",
        genus = scientificName.substringBefore(' '),
        vietnameseNames = vi,
        englishNames = en,
    )
