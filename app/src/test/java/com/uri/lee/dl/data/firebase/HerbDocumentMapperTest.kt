package com.uri.lee.dl.data.firebase

import com.uri.lee.dl.FireStoreHerb
import com.uri.lee.dl.domain.model.GeoLocation
import com.uri.lee.dl.domain.model.HerbImage
import com.uri.lee.dl.domain.model.LocalizedText
import org.junit.Assert.assertEquals
import org.junit.Test

class HerbDocumentMapperTest {

    @Test
    fun `older uploads store just the uploader's uid`() {
        assertEquals(HerbImage("https://i/1.jpg", "uid-7", null), HerbDocumentMapper.toImage("https://i/1.jpg", "uid-7"))
    }

    @Test
    fun `newer uploads store uid and location as JSON`() {
        val image = HerbDocumentMapper.toImage("https://i/2.jpg", """{"uid":"uid-8","lat":10.76,"lng":106.66}""")

        assertEquals(HerbImage("https://i/2.jpg", "uid-8", GeoLocation(10.76, 106.66)), image)
    }

    @Test
    fun `JSON without coordinates has no location`() {
        assertEquals(null, HerbDocumentMapper.toImage("u", """{"uid":"a","lat":null,"lng":null}""").location)
    }

    @Test
    fun `malformed JSON falls back to treating the value as a uid`() {
        assertEquals("{broken", HerbDocumentMapper.toImage("u", "{broken").uploaderId)
    }

    @Test
    fun `what the app writes for an upload reads back the same`() {
        val detail = HerbDocumentMapper.imageDetail("uid-9", GeoLocation(21.0, 105.8))

        assertEquals(HerbImage("u", "uid-9", GeoLocation(21.0, 105.8)), HerbDocumentMapper.toImage("u", detail))
    }

    @Test
    fun `a document maps to a profile with both languages`() {
        val doc = FireStoreHerb(
            latinName = "Polyscias fruticosa", viName = "Đinh lăng", enName = "Ming aralia",
            viOverview = "Tổng quan", enOverview = "Overview", viDosing = "Liều", enDosing = "Dose",
            images = mapOf("https://i/a.jpg" to "uid"),
        )

        val profile = HerbDocumentMapper.toProfile(3035652, doc)

        assertEquals("Đinh lăng", profile.vietnameseName)
        assertEquals(LocalizedText("Tổng quan", "Overview"), profile.overview)
        assertEquals("Dose", profile.dosing.pick(vietnamese = false))
        assertEquals(listOf("https://i/a.jpg"), profile.images.map { it.url })
    }

    @Test
    fun `a list row always shows the same (first) image`() {
        val doc = FireStoreHerb(images = linkedMapOf("https://i/first.jpg" to "a", "https://i/second.jpg" to "b"))

        repeat(5) { assertEquals("https://i/first.jpg", HerbDocumentMapper.toSummary(1, doc).imageUrl) }
    }
}
