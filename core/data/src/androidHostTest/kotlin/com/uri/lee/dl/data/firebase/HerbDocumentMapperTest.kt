package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.model.PhotoSource
import com.uri.lee.dl.domain.model.SpeciesPhoto
import org.junit.Assert.assertEquals
import org.junit.Test

class HerbDocumentMapperTest {

    private fun user(url: String, uid: String?, location: GeoLocation?) =
        SpeciesPhoto(url, url, PhotoSource.USER, uploaderId = uid, location = location)

    @Test
    fun `older uploads store just the uploader's uid`() {
        assertEquals(user("https://i/1.jpg", "uid-7", null), HerbDocumentMapper.toPhoto("https://i/1.jpg", "uid-7"))
    }

    @Test
    fun `newer uploads store uid and location as JSON`() {
        val image = HerbDocumentMapper.toPhoto("https://i/2.jpg", """{"uid":"uid-8","lat":10.76,"lng":106.66}""")

        assertEquals(user("https://i/2.jpg", "uid-8", GeoLocation(10.76, 106.66)), image)
    }

    @Test
    fun `JSON without coordinates has no location`() {
        assertEquals(null, HerbDocumentMapper.toPhoto("u", """{"uid":"a","lat":null,"lng":null}""").location)
    }

    @Test
    fun `malformed JSON falls back to treating the value as a uid`() {
        assertEquals("{broken", HerbDocumentMapper.toPhoto("u", "{broken").uploaderId)
    }

    @Test
    fun `what the app writes for an upload reads back the same`() {
        val detail = HerbDocumentMapper.imageDetail("uid-9", GeoLocation(21.0, 105.8))

        assertEquals(user("u", "uid-9", GeoLocation(21.0, 105.8)), HerbDocumentMapper.toPhoto("u", detail))
    }
}
