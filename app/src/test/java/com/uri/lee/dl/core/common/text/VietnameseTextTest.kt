package com.uri.lee.dl.core.common.text

import com.uri.lee.dl.testing.fixtures.viNames70
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

class VietnameseTextTest {

    @Test
    fun `tone and vowel marks are removed and đ becomes d`() {
        assertEquals("dinh lang", VietnameseText.fold("Đinh lăng"))
        assertEquals("ca gai leo", VietnameseText.fold("Cà gai leo"))
        assertEquals("cuc tan", VietnameseText.fold("Cúc tần"))
        assertEquals("ngu tram", VietnameseText.fold("NGŨ TRẢM"))
    }

    @Test
    fun `every Vietnamese vowel form folds to its base letter`() {
        val vowels = "aàáạảãâầấậẩẫăằắặẳẵeèéẹẻẽêềếệểễiìíịỉĩoòóọỏõôồốộổỗơờớợởỡuùúụủũưừứựửữyỳýỵỷỹ"
        val expected = "a".repeat(18) + "e".repeat(12) + "i".repeat(6) + "o".repeat(18) + "u".repeat(12) + "y".repeat(6)
        assertEquals(expected, VietnameseText.fold(vowels))
        assertEquals(expected.uppercase().lowercase(), VietnameseText.fold(vowels.uppercase()))
    }

    @Test
    fun `decomposed and precomposed text fold the same`() {
        viNames70.values.forEach { name ->
            val nfc = Normalizer.normalize(name, Normalizer.Form.NFC)
            val nfd = Normalizer.normalize(name, Normalizer.Form.NFD)
            assertEquals(name, VietnameseText.fold(nfc), VietnameseText.fold(nfd))
        }
    }

    @Test
    fun `precomposed text folds one character to one character`() {
        val name = "Cỏ roi ngựa"
        assertEquals(name.length, VietnameseText.fold(name).length)
    }

    @Test
    fun `positions map back through combining marks`() {
        val nfd = Normalizer.normalize("Lăng", Normalizer.Form.NFD) // L, a, ̆, n, g
        val folded = VietnameseText.foldWithIndex(nfd)

        assertEquals("lang", folded.folded)
        assertEquals(0..4, folded.toSourceRange(0..3))
    }

    @Test
    fun `punctuation separates words and queries are collapsed`() {
        assertEquals("tra dieu thu", VietnameseText.normalizeQuery("  Trà-điều; thụ! "))
    }
}
