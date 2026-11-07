package com.uri.lee.dl.domain.model

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLanguagesTest {

    // offered is app-wide: put it back as the other tests expect it
    @AfterTest
    fun allLanguages() {
        AppLanguages.start(allLanguages = true, preferred = "en")
    }

    @Test
    fun `with every language on the system's language is kept and all are offered`() {
        assertNull(AppLanguages.start(allLanguages = true, preferred = "fr"))
        assertEquals(AppLanguages.CODES, AppLanguages.offered)
        assertEquals(AppLanguages.CODES, NameLanguages.SUGGESTIBLE)
    }

    @Test
    fun `switched off other languages show in English and only English and Vietnamese are offered`() {
        assertEquals("en", AppLanguages.start(allLanguages = false, preferred = "fr"))
        assertEquals(listOf("en", "vi"), AppLanguages.offered)
        assertEquals(listOf("en", "vi"), NameLanguages.SUGGESTIBLE)
    }

    @Test
    fun `switched off English and Vietnamese are kept`() {
        assertNull(AppLanguages.start(allLanguages = false, preferred = "vi"))
        assertNull(AppLanguages.start(allLanguages = false, preferred = "en"))
    }

    @Test
    fun `sixty languages each once`() {
        assertEquals(60, AppLanguages.CODES.size)
        assertEquals(AppLanguages.CODES.size, AppLanguages.CODES.toSet().size)
    }

    @Test
    fun `Arabic Persian Hebrew and Urdu are right to left`() {
        assertEquals(setOf("ar", "fa", "he", "ur"), AppLanguages.ALL.filter { it.rightToLeft }.map { it.code }.toSet())
        assertTrue(AppLanguages.isRightToLeft("he"))
        assertFalse(AppLanguages.isRightToLeft("id"))
    }

    @Test
    fun `old Android codes for Hebrew and Indonesian map to the current ones`() {
        assertEquals("he", AppLanguages.codeFor("iw", region = "IL"))
        assertEquals("id", AppLanguages.codeFor("in", region = "ID"))
        assertEquals("nb", AppLanguages.codeFor("no", region = "NO"))
    }

    @Test
    fun `Chinese is Traditional for its script or for Taiwan Hong Kong and Macau`() {
        assertEquals("zh-Hant", AppLanguages.codeFor("zh", "Hant", "TW"))
        assertEquals("zh-Hant", AppLanguages.codeFor("zh", "Hant"))
        assertEquals("zh-Hant", AppLanguages.codeFor("zh", region = "HK"))
        assertEquals("zh", AppLanguages.codeFor("zh", "Hans", "CN"))
        assertEquals("zh", AppLanguages.codeFor("zh", region = "SG"))
        assertEquals("zh", AppLanguages.codeFor("zh", "Hans", "HK"))
    }

    @Test
    fun `Portuguese is Brazilian only in Brazil`() {
        assertEquals("pt-BR", AppLanguages.codeFor("pt", region = "BR"))
        assertEquals("pt", AppLanguages.codeFor("pt", region = "PT"))
        assertEquals("pt", AppLanguages.codeFor("pt", region = "AO"))
        assertEquals("pt", AppLanguages.codeFor("pt"))
    }

    @Test
    fun `browser tags map to interface codes`() {
        assertEquals("zh-Hant", AppLanguages.codeForTag("zh-TW"))
        assertEquals("zh-Hant", AppLanguages.codeForTag("zh-Hant-HK"))
        assertEquals("zh", AppLanguages.codeForTag("zh-CN"))
        assertEquals("pt-BR", AppLanguages.codeForTag("pt-BR"))
        assertEquals("id", AppLanguages.codeForTag("id"))
        assertEquals("sat", AppLanguages.codeForTag("sat-Olck-IN"))
        assertEquals("fr", AppLanguages.codeForTag("fr-CA"))
        assertTrue(AppLanguages.codeForTag("pt-BR") in AppLanguages.CODES)
    }
}
