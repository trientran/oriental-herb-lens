package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.HerbPage
import com.uri.lee.dl.domain.model.HerbPageKey
import com.uri.lee.dl.domain.model.HerbProfile
import com.uri.lee.dl.domain.model.HerbSummary
import kotlinx.coroutines.flow.Flow

interface HerbRepository {
    /** Emits the herb's profile and every later change; null while it doesn't exist. */
    fun observeProfile(id: Long): Flow<HerbProfile?>

    /** One page of all herbs, sorted by Vietnamese or Latin name. */
    suspend fun page(after: HerbPageKey?, size: Int, sortByVietnameseName: Boolean): HerbPage

    /** Summaries in the order of [ids]; ids with no herb are left out. */
    suspend fun summaries(ids: List<Long>): List<HerbSummary>
}
