package com.uri.lee.dl.domain.search

import com.uri.lee.dl.data.catalog.SpeciesCsvReader
import com.uri.lee.dl.data.platform.JvmTextNormalizer
import com.uri.lee.dl.fakes.species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeciesSearchIndexTest {

    private val dinhLang = species(3035652, "Polyscias fruticosa", vi = listOf("Đinh lăng"), en = listOf("Ming Aralia"))
    private val tiPlant = species(2766278, "Cordyline fruticosa", vi = listOf("Huyết dụng", "Phát dụ"), en = listOf("Tiplant"))
    private val okra = species(3152707, "Abelmoschus esculentus", vi = listOf("Mướp tây", "Đậu bắp"), en = listOf("Okra"))
    private val dinhLangLa = species(1, "Polyscias guilfoylei", vi = listOf("Đinh lăng lá tròn"))
    private val index = SpeciesSearchIndex(listOf(dinhLang, tiPlant, okra, dinhLangLa))

    private fun top(query: String) = index.search(query).firstOrNull()

    @Test
    fun `diacritics and case don't matter`() {
        listOf("dinh lang", "Đinh lăng", "ĐINH LĂNG", "  dinh   lang ").forEach { q ->
            assertEquals(q, dinhLang, top(q)?.species)
        }
    }

    @Test
    fun `an exact name ranks above a longer name that starts with it`() {
        assertEquals(listOf(dinhLang, dinhLangLa), index.search("dinh lang").map { it.species })
    }

    @Test
    fun `words can come in any order`() {
        assertEquals(dinhLang, top("lang dinh")?.species)
    }

    @Test
    fun `partial words match the start of a word`() {
        assertEquals(setOf(dinhLang, tiPlant), index.search("frutic").map { it.species }.toSet())
    }

    @Test
    fun `alternative and English names are searched too`() {
        assertEquals(okra, top("dau bap")?.species)
        assertEquals(okra, top("okra")?.species)
        assertEquals(tiPlant, top("phat du")?.species)
    }

    @Test
    fun `one typo is forgiven in longer words`() {
        val hit = top("dinh lanh")
        assertEquals(dinhLang, hit?.species)
        assertNull("fuzzy matches aren't highlighted", hit?.highlight)
    }

    @Test
    fun `a typo keeps the word's first letter`() {
        val dinhCanh = species(2, "Pauldopia ghorta", vi = listOf("Đinh cánh"))
        val withRival = SpeciesSearchIndex(listOf(dinhCanh, dinhLang))

        // "lanh" is one edit from both "lăng" and "cánh"; only "lăng" keeps the first letter
        assertEquals(listOf(dinhLang), withRival.search("dinh lanh").map { it.species })
    }

    @Test
    fun `typo matches need a different word for each query word`() {
        val linh = species(3, "Eurya japonica", vi = listOf("Linh"))

        assertTrue(SpeciesSearchIndex(listOf(linh)).search("linh lanh").isEmpty())
    }

    @Test
    fun `short words must match exactly`() {
        assertTrue(index.search("dah").isEmpty())
    }

    @Test
    fun `the highlight covers the matched part of the original name`() {
        val hit = top("lăng")!!
        assertEquals("lăng", hit.matchedName.substring(hit.highlight!!))
    }

    @Test
    fun `an empty query finds nothing`() {
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun `one-edit distance`() {
        assertTrue(SpeciesSearchIndex.withinOneEdit("lanh", "lang"))
        assertTrue(SpeciesSearchIndex.withinOneEdit("dinh", "dihn"))
        assertTrue(SpeciesSearchIndex.withinOneEdit("lang", "lan"))
        assertFalse(SpeciesSearchIndex.withinOneEdit("lang", "lnah"))
    }

    @Test
    fun `against the bundled catalog`() {
        val catalog = SpeciesCsvReader(JvmTextNormalizer).read(File("assets/herb_catalog.csv").readText()).species
        val real = SpeciesSearchIndex(catalog)
        fun first(q: String) = real.search(q).firstOrNull()?.species?.scientificName

        assertEquals("Polyscias fruticosa", first("dinh lang"))
        assertEquals("Polyscias fruticosa", first("lang dinh"))
        assertEquals("Polyscias fruticosa", first("dinh lanh"))
        assertEquals("Stemona tuberosa", first("bach bo"))
        assertEquals("Abelmoschus esculentus", first("okra"))
        assertTrue(real.search("fruticosa").map { it.species.scientificName }.containsAll(listOf("Polyscias fruticosa", "Cordyline fruticosa")))
    }
}
