package com.uri.lee.dl.data.catalog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uri.lee.dl.core.common.AppDispatchers
import com.uri.lee.dl.data.db.HerbLensDatabase
import com.uri.lee.dl.core.common.text.PlatformTextNormalizer
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SqlSpeciesRepositoryTest {

    private val header = "speciesKey,authorship,canonicalName,family,genus,vernacularName,vietnameseName"
    private var csv = """
        $header
        1,L.,Mentha arvensis,Lamiaceae,Mentha,Corn Mint,Bạc hà; Bạc hà nam
        2,,Abelia chinensis,Caprifoliaceae,Abelia,Chinese abelia,
        3,(L.) Harms,Polyscias fruticosa,Araliaceae,Polyscias,Ming Aralia,Đinh lăng
    """.trimIndent()
    private var reads = 0

    private val source = object : CatalogSource {
        override fun readText(): String = csv.also { reads++ }
        override fun readBundledText(): String = csv
    }
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val db = HerbLensDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { HerbLensDatabase.Schema.create(it) })
    private val repository = SqlSpeciesRepository(db, source, SpeciesCsvReader(PlatformTextNormalizer), AppDispatchers(dispatcher, dispatcher, dispatcher))

    @Test
    fun `species round-trip through the database with all their names`() = scope.runTest {
        val mint = repository.get(1)!!

        assertEquals("Mentha arvensis", mint.scientificName)
        assertEquals(listOf("Bạc hà", "Bạc hà nam"), mint.vietnameseNames)
        assertEquals(listOf("Corn Mint"), mint.englishNames)
        assertEquals(emptyList<String>(), repository.get(2)!!.vietnameseNames)
    }

    @Test
    fun `Vietnamese order ignores diacritics and puts unnamed species last`() = scope.runTest {
        assertEquals(listOf(1L, 3L, 2L), repository.page(0, 10, sortByVietnameseName = true).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), repository.page(0, 10, sortByVietnameseName = false).map { it.id })
        assertEquals(listOf(3L), repository.page(1, 1, sortByVietnameseName = true).map { it.id })
    }

    @Test
    fun `the catalog is read once and re-imported only after invalidate with new content`() = scope.runTest {
        repository.get(1)
        repository.get(2)
        assertEquals(1, reads)

        csv = csv.replace("Đinh lăng", "Đinh lăng; Cây gỏi cá")
        repository.invalidate()

        assertEquals(listOf("Đinh lăng", "Cây gỏi cá"), repository.get(3)!!.vietnameseNames)
        assertEquals(2, reads)
    }

    @Test
    fun `search uses the imported catalog and is rebuilt after a new import`() = scope.runTest {
        assertEquals(listOf(3L), repository.search("dinh lang").map { it.species.id })

        csv = csv.replace("Bạc hà; Bạc hà nam", "Bạc hà; Húng cây")
        repository.invalidate()

        assertEquals(listOf(1L), repository.search("hung cay").map { it.species.id })
    }

    @Test
    fun `getAll leaves out unknown ids`() = scope.runTest {
        assertEquals(setOf(1L, 3L), repository.getAll(listOf(1, 3, 99)).keys)
    }
}
