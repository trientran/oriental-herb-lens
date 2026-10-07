package com.uri.lee.dl.data.catalog

import com.uri.lee.dl.core.common.text.PlatformTextNormalizer
import com.uri.lee.dl.domain.model.Taxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCsvReaderTest {

    private val reader = SpeciesCsvReader(PlatformTextNormalizer)
    private val header = "speciesKey,authorship,canonicalName,family,genus,extra,vernacularName,vietnameseName"

    @Test
    fun `reads the columns the app uses and ignores the rest`() {
        val csv = "$header\r\n3035652,(L.) Harms,Polyscias fruticosa,Araliaceae,Polyscias,x,Ming Aralia,Đinh lăng\r\n"

        val species = reader.read(csv).species.single()

        assertEquals(3035652L, species.id)
        assertEquals("Polyscias fruticosa", species.scientificName)
        assertEquals("(L.) Harms", species.authorship)
        assertEquals("Araliaceae", species.family)
        assertEquals("Polyscias", species.genus)
        assertEquals(listOf("Đinh lăng"), species.vietnameseNames)
        assertEquals(listOf("Ming Aralia"), species.englishNames)
        assertEquals(Taxonomy(), species.taxonomy)
    }

    @Test
    fun `GBIF's taxonomy columns are read when present`() {
        val csv = "speciesKey,authorship,basionym,canonicalName,class,family,genus,kingdom,order,phylum,publishedIn,rank,taxonomicStatus,vernacularName,vietnameseName\n" +
            "3035652,(L.) Harms,Panax fruticosus L.,Polyscias fruticosa,Magnoliopsida,Araliaceae,Polyscias,Plantae,Apiales,Tracheophyta,\"Harms, H. (1894). Nat. Pflanzenfam.\",SPECIES,ACCEPTED,Ming Aralia,Đinh lăng\n"

        val species = reader.read(csv).species.single()

        assertEquals(
            Taxonomy("Plantae", "Tracheophyta", "Magnoliopsida", "Apiales", "SPECIES", "ACCEPTED", "Harms, H. (1894). Nat. Pflanzenfam.", "Panax fruticosus L."),
            species.taxonomy,
        )
    }

    @Test
    fun `decomposed Vietnamese is stored precomposed`() {
        val nfd = "Ba\u0323c ha\u0300" // "Bạc hà" as base letters + combining marks
        val csv = "$header\n1,,Mentha arvensis,Lamiaceae,Mentha,,,$nfd\n"

        val name = reader.read(csv).species.single().preferredVietnameseName

        assertEquals("B\u1EA1c h\u00E0", name) // precomposed ạ and à
        assertEquals(6, name?.length)
    }

    @Test
    fun `malformed rows are skipped and reported by line number`() {
        val csv = "$header\n1,,Good one,F,G,,,\nnot-a-number,,Bad id,F,G,,,\n3,,,F,G,,,\n4,,Too few columns\n5,,Good two,F,G,,,\n"

        val result = reader.read(csv)

        assertEquals(listOf(1L, 5L), result.species.map { it.id })
        assertEquals(listOf(3, 4, 5), result.skippedRows)
    }

    @Test(expected = CatalogFormatException::class)
    fun `a missing required column fails the whole file`() {
        reader.read("speciesKey,canonicalName\n1,A b\n")
    }

    @Test
    fun `name lists split on both separators, keep order and drop duplicates`() {
        assertEquals(
            listOf("Mướp tây", "Bụp bắp", "Đậu bắp"),
            SpeciesCsvReader.splitNames("Mướp tây; Bụp bắp, Đậu bắp;  mướp TÂY ;"),
        )
    }

    @Test
    fun `separators inside parentheses do not split a name`() {
        assertEquals(
            listOf("Bách bộ", "Pê chầu chàng (Hmông)", "Lưỡi cọp đỏ (Hồng mao chiên, Mặt đất)"),
            SpeciesCsvReader.splitNames("Bách bộ, Pê chầu chàng (Hmông); Lưỡi cọp đỏ (Hồng mao chiên, Mặt đất)"),
        )
    }

    @Test
    fun `an empty name column gives an empty list`() {
        assertTrue(SpeciesCsvReader.splitNames("  ").isEmpty())
    }
}
