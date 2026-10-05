package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.InstallResult
import com.uri.lee.dl.domain.repository.ContentRepository

/**
 * Brings the model and catalog up to the published releases. The catalog goes first, so a new
 * model can be checked against the catalog it will be used with.
 */
class SyncContentUseCase(private val content: ContentRepository) {

    /** Result per kind that needed an install; kinds already up to date are absent. */
    suspend operator fun invoke(): Map<ContentKind, InstallResult> {
        val published = content.publishedReleases()
        return INSTALL_ORDER.mapNotNull { kind ->
            val release = published[kind] ?: return@mapNotNull null
            if (content.installedRelease(kind)?.url == release.url) return@mapNotNull null
            kind to content.install(kind, release)
        }.toMap()
    }

    companion object {
        val INSTALL_ORDER = listOf(ContentKind.CATALOG, ContentKind.MODEL)
    }
}
