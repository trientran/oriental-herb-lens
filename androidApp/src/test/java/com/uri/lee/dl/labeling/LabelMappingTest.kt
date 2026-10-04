package com.uri.lee.dl.labeling

import com.uri.lee.dl.domain.model.RecognizedHerb
import com.uri.lee.dl.testing.fakes.species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelMappingTest {

    @Test
    fun `a recognised species fills the scan result names`() {
        val okra = species(3152707, "Abelmoschus esculentus", vi = listOf("Mướp tây", "Đậu bắp"), en = listOf("Okra"))

        val herb = RecognizedHerb("3152707", 0.91f, okra).toHerb()

        assertEquals("3152707", herb.id)
        assertEquals("Abelmoschus esculentus", herb.latinName)
        assertEquals("Mướp tây", herb.viName)
        assertEquals("Okra", herb.enName)
        assertEquals(0.91f, herb.confidence)
    }

    @Test
    fun `a label without a catalog entry keeps its id and confidence, with no names`() {
        val herb = RecognizedHerb("42", 0.5f, species = null).toHerb()

        assertEquals("42", herb.id)
        assertEquals(0.5f, herb.confidence)
        assertNull(herb.latinName)
        assertNull(herb.viName)
    }

    @Test
    fun `order is preserved`() {
        val results = listOf(RecognizedHerb("b", 0.9f, null), RecognizedHerb("a", 0.2f, null))

        assertEquals(listOf("b", "a"), results.toHerbs().map { it.id })
    }
}
