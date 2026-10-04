package com.uri.lee.dl.data.content

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.data.catalog.CatalogSource
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uri.lee.dl.data.catalog.SqlSpeciesRepository
import com.uri.lee.dl.data.db.HerbLensDatabase
import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.data.platform.JvmTextNormalizer
import com.uri.lee.dl.domain.model.ContentKind.CATALOG
import com.uri.lee.dl.domain.model.ContentKind.MODEL
import com.uri.lee.dl.domain.model.ContentRelease
import com.uri.lee.dl.domain.model.InstallResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DefaultContentRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** A fake HTTP server: path → (status, body). */
    private val served = mutableMapOf<String, Pair<HttpStatusCode, ByteArray>>()
    private var requestCount = 0
    private val engine = MockEngine { request ->
        requestCount++
        val (status, body) = served[request.url.encodedPath] ?: (HttpStatusCode.NotFound to ByteArray(0))
        respond(body, status)
    }
    private fun url(path: String) = "https://content.example$path"
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)

    private lateinit var files: ContentFiles
    private lateinit var installed: InstalledReleaseStore
    private lateinit var catalog: SqlSpeciesRepository
    private lateinit var repository: DefaultContentRepository

    private val header = "speciesKey,authorship,canonicalName,family,genus,vernacularName,vietnameseName"
    private val bundledCatalog = "$header\n1,,Species one,F,G,,Một\n"
    private val newCatalog = "$header\n1,,Species one,F,G,,Một\n2,,Species two,F,G,,Hai\n"

    @Before
    fun setUp() {
        files = ContentFiles(folder.newFolder("files"))
        installed = InstalledReleaseStore(
            PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("content.preferences_pb") }
        )
        val source = object : CatalogSource {
            override fun readText() = files.installed(CATALOG).takeIf { it.isFile }?.readText() ?: bundledCatalog
            override fun readBundledText() = bundledCatalog
        }
        val reader = SpeciesCsvReader(JvmTextNormalizer)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { HerbLensDatabase.Schema.create(it) }
        catalog = SqlSpeciesRepository(HerbLensDatabase(driver), source, reader, dispatchers)
        repository = DefaultContentRepository(
            releases = { emptyMap() },
            installedReleases = installed,
            files = files,
            downloader = ContentDownloader(HttpClient(engine), dispatchers),
            catalogReader = reader,
            catalog = catalog,
            dispatchers = dispatchers,
        )
    }

    private fun publish(path: String, body: ByteArray): ContentRelease {
        served[path] = HttpStatusCode.OK to body
        return ContentRelease(url(path), sha256(body))
    }

    @Test
    fun `a valid catalog replaces the bundled one and is recorded`() = scope.runTest {
        val release = publish("/catalog/herbs-v2.csv", newCatalog.toByteArray())

        assertEquals(InstallResult.Installed, repository.install(CATALOG, release))

        assertEquals(newCatalog, files.installed(CATALOG).readText())
        assertFalse(files.staging(CATALOG).exists())
        assertEquals(release, repository.installedRelease(CATALOG))
        assertEquals("Hai", catalog.get(2)?.preferredVietnameseName) // cached catalog was reloaded
    }

    @Test
    fun `a checksum mismatch is rejected and leaves the working copy alone`() = scope.runTest {
        files.installed(CATALOG).apply { parentFile?.mkdirs(); writeText(bundledCatalog) }
        val release = publish("/catalog/herbs-v2.csv", newCatalog.toByteArray()).copy(sha256 = "00".repeat(32))

        val result = repository.install(CATALOG, release)

        assertEquals(false, (result as InstallResult.Failed).retryable)
        assertEquals(bundledCatalog, files.installed(CATALOG).readText())
        assertFalse(files.staging(CATALOG).exists())
        assertNull(repository.installedRelease(CATALOG))
    }

    @Test
    fun `a server error is a retryable failure`() = scope.runTest {
        served["/x.csv"] = HttpStatusCode.ServiceUnavailable to ByteArray(0)

        val result = repository.install(CATALOG, ContentRelease(url("/x.csv"), "ab"))

        assertEquals(true, (result as InstallResult.Failed).retryable)
    }

    @Test
    fun `a catalog without the required columns is rejected`() = scope.runTest {
        val release = publish("/catalog/bad.csv", "id,name\n1,x\n".toByteArray())

        assertEquals(false, (repository.install(CATALOG, release) as InstallResult.Failed).retryable)
        assertFalse(files.installed(CATALOG).exists())
    }

    @Test
    fun `a model waits for a catalog that covers its labels, without downloading twice`() = scope.runTest {
        val model = fakeModel(labels = listOf("1", "2"))
        val modelRelease = publish("/models/herb_model-v2.tflite", model)

        // Label "2" is not in the bundled catalog yet
        assertTrue(repository.install(MODEL, modelRelease) is InstallResult.Deferred)
        assertFalse(files.installed(MODEL).exists())
        assertTrue("verified download is kept", files.staging(MODEL).exists())

        repository.install(CATALOG, publish("/catalog/herbs-v2.csv", newCatalog.toByteArray()))
        val requestsBefore = requestCount

        assertEquals(InstallResult.Installed, repository.install(MODEL, modelRelease))
        assertEquals(requestsBefore, requestCount)
        assertTrue(files.installed(MODEL).readBytes().contentEquals(model))
    }

    @Test
    fun `a model without an embedded label list is rejected`() = scope.runTest {
        val release = publish("/models/no-labels.tflite", "not a zip".toByteArray())

        assertEquals(false, (repository.install(MODEL, release) as InstallResult.Failed).retryable)
    }

    @Test
    fun `a recorded release whose file is gone counts as not installed`() = scope.runTest {
        installed.set(CATALOG, ContentRelease("https://content.example/c.csv", "ab"))

        assertNull(repository.installedRelease(CATALOG))
    }

    /** A zip carrying labels.txt, which is all ModelLabels reads from a real .tflite. */
    private fun fakeModel(labels: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("labels.txt"))
            zip.write(labels.joinToString("\n").toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
