package com.uri.lee.dl.data.catalog

import com.uri.lee.dl.core.common.text.PlatformTextNormalizer
import com.uri.lee.dl.domain.search.SpeciesSearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Checks the catalog and model actually bundled in the Android app (androidApp/assets). */
class BundledContentTest {

    private val catalog = SpeciesCsvReader(PlatformTextNormalizer)
        .read(File(ASSETS, "herb_catalog.csv").readText())

    @Test
    fun `bundled catalog parses without skipped rows and without duplicate ids`() {
        assertTrue("skipped: ${catalog.skippedRows}", catalog.skippedRows.isEmpty())
        assertEquals(catalog.species.size, catalog.species.map { it.id }.toSet().size)
    }

    @Test
    fun `every label the bundled model can output has a catalog entry`() {
        // A .tflite with metadata is also a zip; its label list is the embedded labels.txt.
        val labels = ZipFile(File(ASSETS, "herb_model.tflite")).use { zip ->
            zip.getInputStream(zip.getEntry("labels.txt")).bufferedReader().readLines().filter { it.isNotBlank() }
        }
        val ids = catalog.species.map { it.id.toString() }.toSet()

        assertTrue(labels.isNotEmpty())
        assertEquals(emptyList<String>(), labels.filterNot { it in ids })
    }

    @Test
    fun `known species carry their Vietnamese names`() {
        val byName = catalog.species.associateBy { it.scientificName }

        assertEquals("Đinh lăng", byName.getValue("Polyscias fruticosa").preferredVietnameseName)
        assertEquals("Bạc hà", byName.getValue("Mentha arvensis").preferredVietnameseName)
        assertEquals(listOf("Okra"), byName.getValue("Abelmoschus esculentus").englishNames)
    }

    @Test
    fun `search ranks the expected species first`() {
        val index = SpeciesSearchIndex(catalog.species)
        fun first(q: String) = index.search(q).firstOrNull()?.species?.scientificName

        assertEquals("Polyscias fruticosa", first("dinh lang"))
        assertEquals("Polyscias fruticosa", first("lang dinh"))
        assertEquals("Polyscias fruticosa", first("dinh lanh"))
        assertEquals("Stemona tuberosa", first("bach bo"))
        assertEquals("Abelmoschus esculentus", first("okra"))
        assertTrue(index.search("fruticosa").map { it.species.scientificName }.containsAll(listOf("Polyscias fruticosa", "Cordyline fruticosa")))
    }

    private companion object {
        /** Host tests run in the module directory. */
        val ASSETS = File("../../androidApp/assets")
    }
}
