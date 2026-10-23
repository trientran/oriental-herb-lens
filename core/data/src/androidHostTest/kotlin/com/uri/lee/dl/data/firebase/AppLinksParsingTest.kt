package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.data.firebase.RemoteConfigAppLinksRepository.Companion.parse
import com.uri.lee.dl.domain.model.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLinksParsingTest {

    @Test
    fun `unset means the built-in links`() {
        assertEquals(AppLinks(), parse("", ""))
    }

    @Test
    fun `published links are read in order`() {
        assertEquals(
            AppLinks(listOf("a@uni.edu.au", "b@example.com"), "https://www.kaggle.com/datasets/x/y"),
            parse(" a@uni.edu.au, b@example.com ,", " https://www.kaggle.com/datasets/x/y "),
        )
    }

    @Test
    fun `an address that isn't valid keeps the built-in ones`() {
        assertEquals(AppLinks.DEFAULT_CONTACT_EMAILS, parse("a@uni.edu.au, not an email", "").contactEmails)
    }

    @Test
    fun `a dataset link that isn't a web link keeps the built-in one`() {
        assertEquals(AppLinks.DEFAULT_DATASET_URL, parse("", "javascript:alert(1)").datasetUrl)
    }

    @Test
    fun `the survey is on by default and off when the study has closed`() {
        assertEquals(AppLinks.DEFAULT_SURVEY_URL, parse("", "", "").surveyUrl)
        assertEquals(null, parse("", "", " OFF ").surveyUrl)
        assertEquals("https://example.org/s", parse("", "", "https://example.org/s").surveyUrl)
        assertEquals(AppLinks.DEFAULT_SURVEY_URL, parse("", "", "not a link").surveyUrl)
    }
}
