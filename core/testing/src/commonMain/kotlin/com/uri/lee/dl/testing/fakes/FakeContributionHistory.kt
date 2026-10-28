package com.uri.lee.dl.testing.fakes

import com.uri.lee.dl.domain.model.Contribution
import com.uri.lee.dl.domain.model.ContributionHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps contributions in memory; [removed] ids are no longer shown, [adds] counts adds of each model. */
class FakeContributionHistory : ContributionHistoryRepository {
    val contributions = MutableStateFlow<List<Contribution>>(emptyList())
    val removed = mutableSetOf<String>()
    val adds = mutableMapOf<String, Int>()
    override fun observe() = contributions

    override suspend fun stillShown(contributions: List<Contribution>) = contributions.map { it.id }.filterNot { it in removed }.toSet()

    override suspend fun modelAdds(modelIds: List<String>) = modelIds.sumOf { adds[it] ?: 0 }
}
