package com.uri.lee.dl.labeling

import com.uri.lee.dl.testing.fixtures.internationalNames70
import com.uri.lee.dl.testing.fixtures.viNames70
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class LabelMappingTest {

    private fun List<RawLabel>.toHerbsFromFixtures() =
        toHerbs(latinNameOf = internationalNames70::get, viNameOf = viNames70::get)

    @Test
    fun `names are looked up by label id`() {
        val herb = listOf(RawLabel(id = "0002", confidence = 0.91f)).toHerbsFromFixtures().single()

        assertEquals("0002", herb.id)
        assertEquals("Stemona tuberosa", herb.latinName)
        // The fixture stores this name decomposed (NFD); compare the text, not the encoding.
        assertEquals("Bách bộ", herb.viName?.let { Normalizer.normalize(it, Normalizer.Form.NFC) })
        assertEquals(0.91f, herb.confidence)
    }

    @Test
    fun `model order is preserved`() {
        val labels = listOf(RawLabel("0007", 0.8f), RawLabel("0001", 0.15f), RawLabel("0004", 0.05f))

        assertEquals(listOf("0007", "0001", "0004"), labels.toHerbsFromFixtures().map { it.id })
    }

    @Test
    fun `an id missing from the lookups yields null names, not a failure`() {
        val herb = listOf(RawLabel(id = "9999", confidence = 0.5f)).toHerbsFromFixtures().single()

        assertEquals("9999", herb.id)
        assertNull(herb.latinName)
        assertNull(herb.viName)
    }

    @Test
    fun `no labels yields no herbs`() {
        assertTrue(emptyList<RawLabel>().toHerbsFromFixtures().isEmpty())
    }

    @Test
    fun `timing fields are left for the caller to fill`() {
        val herb = listOf(RawLabel("0001", 0.9f)).toHerbsFromFixtures().single()

        assertNull(herb.bitmapProcessingTime)
        assertNull(herb.inferenceProcessingTime)
    }

    @Test
    fun `every one of the 70 model labels has both names`() {
        val ids = (1..70).map { it.toString().padStart(4, '0') }
        val herbs = ids.map { RawLabel(it, 1f) }.toHerbsFromFixtures()

        herbs.forEach { herb ->
            assertTrue("missing Vietnamese name for ${herb.id}", !herb.viName.isNullOrBlank())
            assertTrue("missing international name for ${herb.id}", !herb.latinName.isNullOrBlank())
        }
    }
}
