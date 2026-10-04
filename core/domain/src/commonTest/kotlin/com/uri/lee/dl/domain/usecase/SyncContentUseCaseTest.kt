package com.uri.lee.dl.domain.usecase

import com.uri.lee.dl.domain.model.ContentKind
import com.uri.lee.dl.domain.model.ContentKind.CATALOG
import com.uri.lee.dl.domain.model.ContentKind.MODEL
import com.uri.lee.dl.domain.model.ContentRelease
import com.uri.lee.dl.domain.model.InstallResult
import com.uri.lee.dl.domain.repository.ContentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SyncContentUseCaseTest {

    private class FakeContentRepository(
        var published: Map<ContentKind, ContentRelease> = emptyMap(),
        val installed: MutableMap<ContentKind, ContentRelease> = mutableMapOf(),
        var result: InstallResult = InstallResult.Installed,
    ) : ContentRepository {
        val installCalls = mutableListOf<ContentKind>()

        override suspend fun publishedReleases() = published
        override suspend fun installedRelease(kind: ContentKind) = installed[kind]
        override suspend fun install(kind: ContentKind, release: ContentRelease): InstallResult {
            installCalls += kind
            if (result == InstallResult.Installed) installed[kind] = release
            return result
        }
    }

    private val modelV2 = ContentRelease("https://content.example/models/herb_model-v2.tflite", "aa")
    private val catalogV2 = ContentRelease("https://content.example/catalog/herbs-v2.csv", "bb")

    @Test
    fun `the catalog is installed before the model`() = runTest {
        val repo = FakeContentRepository(published = mapOf(MODEL to modelV2, CATALOG to catalogV2))

        val results = SyncContentUseCase(repo)()

        assertEquals(listOf(CATALOG, MODEL), repo.installCalls)
        assertEquals(mapOf(CATALOG to InstallResult.Installed, MODEL to InstallResult.Installed), results)
    }

    @Test
    fun `a release that is already installed is not fetched again`() = runTest {
        val repo = FakeContentRepository(
            published = mapOf(MODEL to modelV2, CATALOG to catalogV2),
            installed = mutableMapOf(MODEL to modelV2),
        )

        SyncContentUseCase(repo)()

        assertEquals(listOf(CATALOG), repo.installCalls)
    }

    @Test
    fun `nothing published means nothing to do and the bundled copies stay in use`() = runTest {
        val repo = FakeContentRepository()

        assertEquals(emptyMap<ContentKind, InstallResult>(), SyncContentUseCase(repo)())
        assertEquals(emptyList<ContentKind>(), repo.installCalls)
    }

    @Test
    fun `a deferred model is retried on the next sync`() = runTest {
        val repo = FakeContentRepository(published = mapOf(MODEL to modelV2), result = InstallResult.Deferred("waiting"))
        val sync = SyncContentUseCase(repo)

        sync()
        repo.result = InstallResult.Installed
        sync()

        assertEquals(listOf(MODEL, MODEL), repo.installCalls)
    }
}
