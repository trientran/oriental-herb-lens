package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.data.firebase.RemoteConfigCitationRepository.Companion.parse
import com.uri.lee.dl.domain.model.Citation
import org.junit.Assert.assertEquals
import org.junit.Test

class CitationParsingTest {

    @Test
    fun `unset means nothing to cite`() {
        assertEquals(emptyList<Citation>(), parse(""))
    }

    @Test
    fun `entries are read in order, with or without a link`() {
        val value = """[
            {"text": " Paper one. ", "url": "https://doi.org/10.1/a", "bibtex": "ignored"},
            {"text": "The app itself."}
        ]"""
        assertEquals(listOf(Citation("Paper one.", "https://doi.org/10.1/a"), Citation("The app itself.")), parse(value))
    }

    @Test
    fun `blank entries and links that aren't web pages are dropped`() {
        val value = """[{"text": "  "}, {"text": "Paper.", "url": "javascript:alert(1)"}]"""
        assertEquals(listOf(Citation("Paper.")), parse(value))
    }

    @Test
    fun `a value that isn't a list is ignored`() {
        assertEquals(emptyList<Citation>(), parse("""{"text": "Paper."}"""))
        assertEquals(emptyList<Citation>(), parse("not json"))
    }
}
