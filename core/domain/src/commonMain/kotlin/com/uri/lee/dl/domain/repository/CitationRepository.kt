package com.uri.lee.dl.domain.repository

import com.uri.lee.dl.domain.model.Citation

/** How to cite the app; published remotely, so new papers appear without a release. */
interface CitationRepository {
    /** Empty when nothing is published. */
    suspend fun citations(): List<Citation>
}
