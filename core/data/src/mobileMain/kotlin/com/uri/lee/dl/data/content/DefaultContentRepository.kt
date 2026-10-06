package com.uri.lee.dl.data.content

import co.touchlab.kermit.Logger
import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.data.catalog.CatalogFormatException
import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.data.catalog.SqlSpeciesRepository
import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.ContentRelease
import com.uri.lee.dl.domain.model.InstallResult
import com.uri.lee.dl.domain.repository.ContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

internal class DefaultContentRepository(
    private val releases: ReleaseSource,
    private val installedReleases: InstalledReleaseStore,
    private val files: ContentFiles,
    private val fileSystem: FileSystem,
    private val downloader: ContentDownloader,
    private val catalogReader: SpeciesCsvReader,
    private val catalog: SqlSpeciesRepository,
    private val dispatchers: AppDispatchers,
) : ContentRepository {

    override suspend fun publishedReleases(): Map<ContentKind, ContentRelease> = releases.latest()

    /** A recorded release whose file has disappeared counts as not installed, so it's fetched again. */
    override suspend fun installedRelease(kind: ContentKind): ContentRelease? =
        installedReleases.get(kind)?.takeIf { fileSystem.exists(files.installed(kind)) }

    override suspend fun install(kind: ContentKind, release: ContentRelease): InstallResult {
        val staging = files.staging(kind)
        try {
            downloader.download(release.url, release.sha256, staging)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChecksumMismatchException) {
            log.e(e) { "Rejected $kind release ${release.url}" }
            return InstallResult.Failed(e.message.orEmpty(), retryable = false)
        } catch (e: Exception) {
            log.w(e) { "Download of $kind failed" }
            return InstallResult.Failed(e.message.orEmpty(), retryable = true)
        }

        val check = withContext(dispatchers.default) { validate(kind, staging) }
        if (check != null) return check.also { if (it is InstallResult.Failed) fileSystem.delete(staging) }

        // Same-volume rename: readers see the old file or the new one, never a partial one
        withContext(dispatchers.io) { fileSystem.atomicMove(staging, files.installed(kind)) }
        installedReleases.set(kind, release)
        if (kind == ContentKind.CATALOG) catalog.invalidate()
        log.i { "Installed $kind from ${release.url}" }
        return InstallResult.Installed
    }

    /** Null when [file] may be activated; otherwise why not. */
    private suspend fun validate(kind: ContentKind, file: Path): InstallResult? = when (kind) {
        ContentKind.CATALOG -> try {
            val result = catalogReader.read(fileSystem.read(file) { readUtf8() })
            if (result.species.isEmpty()) InstallResult.Failed("Catalog has no species", retryable = false) else null
        } catch (e: CatalogFormatException) {
            InstallResult.Failed(e.message.orEmpty(), retryable = false)
        }

        ContentKind.MODEL -> {
            val labels = ModelLabels.read(fileSystem, file)
            if (labels.isNullOrEmpty()) {
                InstallResult.Failed("Model has no embedded label list", retryable = false)
            } else {
                // Every label must name a catalog species, or results would show without names.
                val known = catalog.all().mapTo(HashSet()) { it.id.toString() }
                val missing = labels.filterNot { it in known }
                if (missing.isEmpty()) null
                else InstallResult.Deferred("${missing.size} model labels are not in the catalog yet, e.g. ${missing.take(3)}")
            }
        }
    }

    private companion object {
        val log = Logger.withTag("ContentSync")
    }
}
