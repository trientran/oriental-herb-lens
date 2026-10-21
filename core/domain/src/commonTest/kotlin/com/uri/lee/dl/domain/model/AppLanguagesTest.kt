package com.uri.lee.dl.domain.model

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
